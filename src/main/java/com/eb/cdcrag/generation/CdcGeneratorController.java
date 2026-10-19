package com.eb.cdcrag.generation;

import com.eb.cdcrag.export.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/cdc")
public class CdcGeneratorController {

    private final CdcGeneratorService generator;
    private final CdcRepository cdcRepository;
    private final CdcSectionRepository sectionRepository;
    private final PlanExecutionService planExecutionService;
    private final CdcEventRepository eventRepository;
    private final ExportService exportService;

    public CdcGeneratorController(CdcGeneratorService generator, CdcRepository cdcRepository,
                                  CdcSectionRepository sectionRepository, PlanExecutionService planExecutionService,
                                  CdcEventRepository eventRepository, ExportService exportService) {
        this.generator = generator;
        this.cdcRepository = cdcRepository;
        this.sectionRepository = sectionRepository;
        this.planExecutionService = planExecutionService;
        this.eventRepository = eventRepository;
        this.exportService = exportService;
    }

    /** Liste des CDC avec avancement (sections DRAFT/APPROVED sur le total). */
    @GetMapping
    public List<CdcRepository.CdcSummary> list() {
        return cdcRepository.findAllSummaries();
    }

    /** Types de CDC réellement présents en base (cdc + document), pour
     * alimenter les sélecteurs de type côté frontend sans liste figée. */
    @GetMapping("/types")
    public List<String> types() {
        return cdcRepository.findDistinctTypes();
    }

    /** Crée le CDC et ses sections vides. Ne lance aucune génération : voir
     * POST /{id}/generate, qui exécute un plan (enregistré ou ponctuel). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CdcRepository.CdcRecord create(@RequestBody CreateCdcRequest request) {
        try {
            return generator.create(request.title(), request.templateId(), request.cdcType(),
                    request.brief(), request.planId());
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /** Exécute un plan sur ce CDC : soit un plan enregistré ({planId}), soit
     * un plan ponctuel ({mode, etapes}), voir PlanExecutionService. Répond
     * avant la fin de la génération (asynchrone, un seul thread) : le
     * nombre d'appels LLM planifiés est renvoyé pour affichage immédiat. */
    @PostMapping("/{id}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public PlanExecutionService.ExecutionResult generate(@PathVariable long id,
                                                          @RequestBody PlanExecutionService.GenerateRequest request) {
        try {
            return planExecutionService.execute(id, request);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    /** Génère une section : recherche vectorielle filtrée type + section,
     * puis appel LLM en tâche de fond.
     * EMPTY/DRAFT/FAILED → QUEUED → GENERATING → DRAFT/FAILED. */
    @PostMapping("/{id}/sections/{ordre}/generate")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CdcSectionRepository.Section generateSection(@PathVariable long id, @PathVariable int ordre) {
        try {
            return generator.generateSection(id, ordre);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    /** Historique du CDC (voir cdc_event -- notamment l'instantané du plan à
     * chaque exécution). Plus récents d'abord. */
    @GetMapping("/{id}/events")
    public List<CdcEventRepository.CdcEvent> events(@PathVariable long id) {
        cdcRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CDC " + id + " introuvable"));
        return eventRepository.findByCdc(id);
    }

    @GetMapping("/{id}")
    public CdcView get(@PathVariable long id) {
        CdcRepository.CdcRecord cdc = cdcRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CDC " + id + " introuvable"));
        String reviewStatus = cdcRepository.findLatestReviewStatus(id).orElse(null);
        return new CdcView(cdc, sectionRepository.findAllByCdc(id), reviewStatus);
    }

    /** Édition manuelle du contenu d'une section. Refusée (409) si APPROVED.
     * Repasse la section à DRAFT, quel que soit son statut précédent. */
    @PutMapping("/{id}/sections/{ordre}")
    public CdcSectionRepository.Section updateSection(@PathVariable long id, @PathVariable int ordre,
                                                       @RequestBody UpdateSectionRequest request) {
        try {
            return generator.updateSection(id, ordre, request.body());
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    /** DRAFT -> APPROVED sur une section. */
    @PostMapping("/{id}/sections/{ordre}/approve")
    public CdcSectionRepository.Section approveSection(@PathVariable long id, @PathVariable int ordre) {
        try {
            return generator.approveSection(id, ordre);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    /** Refuse (409) si une section obligatoire n'est pas APPROVED ; sinon
     * assemble, ingère dans le corpus et passe le CDC à APPROVED. Idempotent. */
    @PostMapping("/{id}/approve")
    public CdcGeneratorService.ApproveResult approveCdc(@PathVariable long id) {
        try {
            return generator.approveCdc(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    /** Associe (ou retire, formatTemplateId=null) un modèle de mise en forme
     * à ce CDC -- pris en compte au prochain export (voir
     * ExportService.resolveStyle). */
    @PutMapping("/{id}/format-template")
    public CdcRepository.CdcRecord setFormatTemplate(@PathVariable long id, @RequestBody SetFormatTemplateRequest request) {
        cdcRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "CDC " + id + " introuvable"));
        cdcRepository.setFormatTemplateId(id, request.formatTemplateId());
        return cdcRepository.findById(id).orElseThrow();
    }

    /** format = pdf | docx | md (défaut). Page de garde, sommaire et
     * tableaux rendus uniquement pour pdf/docx -- md reste le texte
     * assemblé brut, voir ExportService. */
    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable long id,
                                         @RequestParam(defaultValue = "md") String format) {
        try {
            ExportService.ExportResult result = exportService.export(id, format);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(result.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + result.filename() + "\"")
                    .body(result.content());
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    public record CdcView(CdcRepository.CdcRecord cdc, List<CdcSectionRepository.Section> sections,
                          String reviewStatus) {}

    public record UpdateSectionRequest(String body) {}

    public record CreateCdcRequest(String title, Long templateId, String cdcType, String brief, Long planId) {}

    public record SetFormatTemplateRequest(Long formatTemplateId) {}
}
