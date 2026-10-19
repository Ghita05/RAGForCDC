package com.eb.cdcrag.generation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final CdcTemplateRepository repository;
    private final TemplateService service;

    public TemplateController(CdcTemplateRepository repository, TemplateService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public List<CdcTemplateRepository.TemplateSummary> list() {
        return repository.findAllTemplates();
    }

    @GetMapping("/{id}")
    public CdcTemplateRepository.TemplateView get(@PathVariable long id) {
        try {
            return service.view(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CdcTemplateRepository.TemplateView create(@RequestBody TemplateRequest req) {
        try {
            return service.create(req.name(), req.description(), req.cdcType());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    public CdcTemplateRepository.TemplateView update(@PathVariable long id, @RequestBody TemplateRequest req) {
        try {
            return service.updateMeta(id, req.name(), req.description(), req.cdcType());
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public CdcTemplateRepository.TemplateView duplicate(@PathVariable long id,
                                                         @RequestBody(required = false) DuplicateRequest req) {
        try {
            return service.duplicate(id, req == null ? null : req.name());
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id) {
        try {
            service.delete(id);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage());
        }
    }

    @PostMapping("/{id}/sections")
    public CdcTemplateRepository.TemplateSection createSection(@PathVariable long id,
                                                                @RequestBody SectionRequest req) {
        repository.findTemplateById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template " + id + " introuvable"));
        try {
            long sectionId = repository.insertSection(id, req.ordre(), req.code(), req.titre(),
                    req.consigne(), req.obligatoire());
            return repository.findSections(id).stream()
                    .filter(s -> s.id() == sectionId)
                    .findFirst()
                    .orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ordre ou code déjà utilisé pour ce template");
        }
    }

    @PutMapping("/{id}/sections/{sectionId}")
    public CdcTemplateRepository.TemplateSection updateSection(@PathVariable long id, @PathVariable long sectionId,
                                                                @RequestBody SectionRequest req) {
        try {
            int updated = repository.updateSection(sectionId, req.ordre(), req.titre(), req.consigne(), req.obligatoire());
            if (updated == 0) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Section " + sectionId + " introuvable");
            }
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet ordre existe déjà pour ce template");
        }
        return repository.findSections(id).stream()
                .filter(s -> s.id() == sectionId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Section " + sectionId + " introuvable"));
    }

    @DeleteMapping("/{id}/sections/{sectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSection(@PathVariable long id, @PathVariable long sectionId) {
        try {
            service.deleteSection(id, sectionId);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    public record SectionRequest(int ordre, String code, String titre, String consigne, boolean obligatoire) {}

    public record TemplateRequest(String name, String description, String cdcType) {}

    public record DuplicateRequest(String name) {}
}
