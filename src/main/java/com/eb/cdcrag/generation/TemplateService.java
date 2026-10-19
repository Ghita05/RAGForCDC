package com.eb.cdcrag.generation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/** Orchestration transactionnelle pour les templates (création, duplication,
 * suppression) -- CdcTemplateRepository reste du SQL pur, comme
 * PlanRepository/PlanService. */
@Service
public class TemplateService {

    private final CdcTemplateRepository repository;
    public TemplateService(CdcTemplateRepository repository) { this.repository = repository; }

    public CdcTemplateRepository.TemplateView create(String name, String description, String cdcType) {
        validateName(name);
        long id = repository.insertTemplate(name.strip(), blankToNull(description), blankToNull(cdcType));
        return view(id);
    }

    public CdcTemplateRepository.TemplateView updateMeta(long id, String name, String description, String cdcType) {
        validateName(name);
        int updated = repository.updateTemplateMeta(id, name.strip(), blankToNull(description), blankToNull(cdcType));
        if (updated == 0) throw new NoSuchElementException("Template " + id + " introuvable");
        return view(id);
    }

    /** Copie le template ET ses sections (mêmes code/ordre/consigne) sous un
     * nouveau nom -- permet de partir d'une structure existante plutôt que de
     * tout redéfinir depuis zéro. */
    @Transactional
    public CdcTemplateRepository.TemplateView duplicate(long id, String newName) {
        CdcTemplateRepository.TemplateSummary source = repository.findTemplateById(id)
                .orElseThrow(() -> new NoSuchElementException("Template " + id + " introuvable"));
        var sections = repository.findSections(id);
        String name = (newName == null || newName.isBlank()) ? source.name() + " (copie)" : newName.strip();
        long newId = repository.insertTemplate(name, source.description(), source.cdcType());
        repository.insertSections(newId, sections.stream()
                .map(s -> new CdcTemplateRepository.SectionInput(
                        s.ordre(), s.code(), s.titre(), s.consigne(), s.obligatoire()))
                .toList());
        return view(newId);
    }

    /** Refuse la suppression du modèle par défaut (POST /api/cdc s'appuie
     * dessus quand aucun templateId/planId n'est fourni) et de tout modèle
     * encore référencé par un CDC ou un plan de génération (pas de cascade
     * sur ces clés étrangères, volontairement). */
    public void delete(long id) {
        CdcTemplateRepository.TemplateSummary template = repository.findTemplateById(id)
                .orElseThrow(() -> new NoSuchElementException("Template " + id + " introuvable"));
        if (template.isDefault()) {
            throw new IllegalStateException("Le modèle par défaut ne peut pas être supprimé");
        }
        try {
            repository.deleteTemplate(id);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalStateException(
                    "Ce modèle est utilisé par au moins un CDC ou un plan de génération : suppression impossible");
        }
    }

    public void deleteSection(long templateId, long sectionId) {
        repository.findTemplateById(templateId)
                .orElseThrow(() -> new NoSuchElementException("Template " + templateId + " introuvable"));
        int deleted = repository.deleteSection(sectionId);
        if (deleted == 0) throw new NoSuchElementException("Section " + sectionId + " introuvable");
    }

    public CdcTemplateRepository.TemplateView view(long id) {
        CdcTemplateRepository.TemplateSummary template = repository.findTemplateById(id)
                .orElseThrow(() -> new NoSuchElementException("Template " + id + " introuvable"));
        return new CdcTemplateRepository.TemplateView(template, repository.findSections(id));
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom du modèle est obligatoire");
        }
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.strip();
    }
}
