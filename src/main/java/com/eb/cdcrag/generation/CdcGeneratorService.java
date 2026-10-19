package com.eb.cdcrag.generation;

import com.eb.cdcrag.ingestion.IngestionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class CdcGeneratorService {

    private final CdcRepository cdcRepository;
    private final CdcTemplateRepository templateRepository;
    private final CdcSectionRepository sectionRepository;
    private final CdcSectionGenerationWorker worker;
    private final IngestionService ingestionService;
    private final PlanRepository planRepository;

    public CdcGeneratorService(CdcRepository cdcRepository, CdcTemplateRepository templateRepository,
                               CdcSectionRepository sectionRepository, CdcSectionGenerationWorker worker,
                               IngestionService ingestionService, PlanRepository planRepository) {
        this.cdcRepository = cdcRepository;
        this.templateRepository = templateRepository;
        this.sectionRepository = sectionRepository;
        this.worker = worker;
        this.ingestionService = ingestionService;
        this.planRepository = planRepository;
    }

    /** Crée le CDC et ses sections vides. templateId par défaut = template
     * par défaut du système ; si planId est fourni, le template du plan
     * prévaut (un plan référence toujours un template précis -- voir
     * PlanRepository). Synchrone : aucun appel LLM ici, seulement de
     * l'écriture en base -- la génération est un appel séparé, voir
     * PlanExecutionService. */
    public CdcRepository.CdcRecord create(String title, Long templateId, String cdcType, String brief, Long planId) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title est requis");
        }
        long resolvedTemplateId;
        if (planId != null) {
            resolvedTemplateId = planRepository.findSummaryById(planId)
                    .orElseThrow(() -> new NoSuchElementException("Plan " + planId + " introuvable"))
                    .templateId();
        } else if (templateId != null) {
            resolvedTemplateId = templateId;
        } else {
            resolvedTemplateId = templateRepository.findDefaultTemplateId()
                    .orElseThrow(() -> new IllegalStateException("Aucun template par défaut configuré"));
        }
        List<CdcTemplateRepository.TemplateSection> sections = templateRepository.findSections(resolvedTemplateId);

        long id = cdcRepository.insert(title.strip(), cdcType, resolvedTemplateId, brief, planId);
        sectionRepository.insertEmptySections(id, sections);

        return cdcRepository.findById(id).orElseThrow();
    }

    /** Marque la section QUEUED puis lance la génération en tâche de fond.
     * QUEUED -> GENERATING est posé par le worker lui-même, au tout début du
     * traitement effectif (voir CdcSectionGenerationWorker) : le pool n'a
     * qu'un thread, une section peut attendre en file un moment avant d'être
     * réellement traitée. */
    public CdcSectionRepository.Section generateSection(long cdcId, int ordre) {
        cdcRepository.findById(cdcId)
                .orElseThrow(() -> new NoSuchElementException("CDC " + cdcId + " introuvable"));
        sectionRepository.findByCdcAndOrdre(cdcId, ordre)
                .orElseThrow(() -> new NoSuchElementException(
                        "Section " + ordre + " introuvable pour le CDC " + cdcId));

        sectionRepository.markQueued(cdcId, ordre);
        worker.run(cdcId, ordre);
        return sectionRepository.findByCdcAndOrdre(cdcId, ordre).orElseThrow();
    }

    /** DRAFT -> APPROVED sur une section. */
    public CdcSectionRepository.Section approveSection(long cdcId, int ordre) {
        cdcRepository.findById(cdcId)
                .orElseThrow(() -> new NoSuchElementException("CDC " + cdcId + " introuvable"));
        CdcSectionRepository.Section section = sectionRepository.findByCdcAndOrdre(cdcId, ordre)
                .orElseThrow(() -> new NoSuchElementException(
                        "Section " + ordre + " introuvable pour le CDC " + cdcId));
        if (!"DRAFT".equals(section.status())) {
            throw new IllegalStateException(
                    "Section " + ordre + " n'est pas au statut DRAFT (actuel : " + section.status() + ")");
        }
        sectionRepository.markApproved(cdcId, ordre);
        return sectionRepository.findByCdcAndOrdre(cdcId, ordre).orElseThrow();
    }

    /** Refuse si une section obligatoire n'est pas APPROVED. Sinon assemble le
     * texte complet, l'ingère dans le corpus (document + chunk, section_code
     * renseigné via SectionDetector) et passe le CDC à APPROVED.
     * Idempotent : si le CDC est déjà APPROVED, ne ré-ingère rien -- pas de
     * doublon possible puisque l'ingestion n'est même pas retentée -- mais
     * l'id du document déjà créé est quand même renvoyé (jamais null une
     * fois le CDC réellement approuvé). */
    public ApproveResult approveCdc(long cdcId) {
        CdcRepository.CdcRecord cdc = cdcRepository.findById(cdcId)
                .orElseThrow(() -> new NoSuchElementException("CDC " + cdcId + " introuvable"));
        String filename = documentFilename(cdcId, cdc.cdcType());

        if ("APPROVED".equals(cdc.status())) {
            Long documentId = ingestionService.findDocumentId(filename).orElse(null);
            return new ApproveResult(cdc, sectionRepository.findAllByCdc(cdcId), documentId);
        }

        List<String> pending = sectionRepository.findObligatoireByCdc(cdcId).stream()
                .filter(s -> !"APPROVED".equals(s.status()))
                .map(s -> s.code() + " (" + s.status() + ")")
                .toList();
        if (!pending.isEmpty()) {
            throw new IllegalStateException(
                    "Sections obligatoires non approuvées : " + String.join(", ", pending));
        }

        String markdown = sectionRepository.assembleMarkdown(cdcId);
        // Ingestion d'abord : si elle échoue, le CDC ne doit pas se retrouver
        // marqué APPROVED sans document correspondant.
        IngestionService.IngestResult ingestResult = ingestionService.ingest(filename, cdc.cdcType(), markdown);

        cdcRepository.markApproved(cdcId);
        CdcRepository.CdcRecord updated = cdcRepository.findById(cdcId).orElseThrow();
        return new ApproveResult(updated, sectionRepository.findAllByCdc(cdcId), ingestResult.documentId());
    }

    private String documentFilename(long cdcId, String cdcType) {
        return "CDC-" + cdcId + "_" + cdcType + ".md";
    }


    /** Édition manuelle du contenu d'une section. Refusée si APPROVED (le
     * document assemblé a potentiellement déjà été ingéré ; le modifier sans
     * repasser par une nouvelle approbation romprait la cohérence avec le
     * corpus). Toute édition repasse la section à DRAFT, y compris si elle
     * était FAILED ou encore EMPTY. */
    public CdcSectionRepository.Section updateSection(long cdcId, int ordre, String body) {
        cdcRepository.findById(cdcId)
                .orElseThrow(() -> new NoSuchElementException("CDC " + cdcId + " introuvable"));
        CdcSectionRepository.Section section = sectionRepository.findByCdcAndOrdre(cdcId, ordre)
                .orElseThrow(() -> new NoSuchElementException(
                        "Section " + ordre + " introuvable pour le CDC " + cdcId));
        if ("APPROVED".equals(section.status())) {
            throw new IllegalStateException(
                    "Section " + ordre + " est APPROVED, modification refusée");
        }
        sectionRepository.markDraft(cdcId, ordre, body);
        return sectionRepository.findByCdcAndOrdre(cdcId, ordre).orElseThrow();
    }

    public record ApproveResult(CdcRepository.CdcRecord cdc, List<CdcSectionRepository.Section> sections,
                                Long documentId) {}
}
