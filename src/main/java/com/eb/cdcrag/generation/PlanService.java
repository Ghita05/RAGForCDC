package com.eb.cdcrag.generation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class PlanService {

    private static final List<String> MODES = List.of("TRAME", "SECTIONS", "COMPLET");

    private final PlanRepository repository;

    public PlanService(PlanRepository repository) {
        this.repository = repository;
    }

    public List<PlanRepository.PlanSummary> list() {
        return repository.findAll();
    }

    public PlanRepository.PlanView get(long id) {
        PlanRepository.PlanSummary summary = repository.findSummaryById(id)
                .orElseThrow(() -> new NoSuchElementException("Plan " + id + " introuvable"));
        return new PlanRepository.PlanView(summary, repository.findSteps(id));
    }

    @Transactional
    public PlanRepository.PlanView create(PlanRequest req) {
        validate(req);
        long id = repository.insertPlan(req.name(), req.description(), req.templateId(), req.cdcType(), req.mode());
        repository.insertSteps(id, req.steps());
        return get(id);
    }

    /** Le template d'un plan ne change pas après création : ses étapes
     * référencent les sections de CE template. */
    @Transactional
    public PlanRepository.PlanView update(long id, PlanRequest req) {
        repository.findSummaryById(id).orElseThrow(() -> new NoSuchElementException("Plan " + id + " introuvable"));
        validate(req);
        repository.updatePlanRow(id, req.name(), req.description(), req.cdcType(), req.mode());
        repository.deleteSteps(id);
        repository.insertSteps(id, req.steps());
        return get(id);
    }

    @Transactional
    public PlanRepository.PlanView duplicate(long id, String newName) {
        PlanRepository.PlanView source = get(id);
        String name = (newName == null || newName.isBlank()) ? source.summary().name() + " (copie)" : newName.strip();
        List<PlanRepository.StepInput> steps = source.steps().stream()
                .map(s -> new PlanRepository.StepInput(s.ordre(), s.templateSectionId(), s.guidance(), s.inclus()))
                .toList();
        long newId = repository.insertPlan(name, source.summary().description(), source.summary().templateId(),
                source.summary().cdcType(), source.summary().mode());
        repository.insertSteps(newId, steps);
        return get(newId);
    }

    private void validate(PlanRequest req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("Le nom du plan est obligatoire");
        }
        if (!MODES.contains(req.mode())) {
            throw new IllegalArgumentException("Mode invalide : " + req.mode() + " (attendu : " + MODES + ")");
        }
    }

    public record PlanRequest(String name, String description, long templateId, String cdcType, String mode,
                              List<PlanRepository.StepInput> steps) {}
}
