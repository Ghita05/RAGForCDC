package com.eb.cdcrag.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/** Exécute un plan (enregistré ou ponctuel) sur un CDC : résout les étapes,
 * enregistre un instantané dans cdc_event, puis lance la génération.
 * Responsabilité séparée de CdcGeneratorService (qui gère le cycle de vie
 * du CDC lui-même) -- un plan référence toujours un template et ne stocke
 * que les écarts, l'exécution est une préoccupation à part entière. */
@Service
public class PlanExecutionService {

    private static final Logger log = LoggerFactory.getLogger(PlanExecutionService.class);
    private static final List<String> MODES = List.of("TRAME", "SECTIONS", "COMPLET");

    private final CdcRepository cdcRepository;
    private final CdcSectionRepository sectionRepository;
    private final CdcTemplateRepository templateRepository;
    private final PlanRepository planRepository;
    private final CdcEventRepository eventRepository;
    private final CdcSectionGenerationWorker worker;
    private final ObjectMapper objectMapper;

    public PlanExecutionService(CdcRepository cdcRepository, CdcSectionRepository sectionRepository,
                                CdcTemplateRepository templateRepository, PlanRepository planRepository,
                                CdcEventRepository eventRepository, CdcSectionGenerationWorker worker,
                                ObjectMapper objectMapper) {
        this.cdcRepository = cdcRepository;
        this.sectionRepository = sectionRepository;
        this.templateRepository = templateRepository;
        this.planRepository = planRepository;
        this.eventRepository = eventRepository;
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    public ExecutionResult execute(long cdcId, GenerateRequest request) {
        CdcRepository.CdcRecord cdc = cdcRepository.findById(cdcId)
                .orElseThrow(() -> new NoSuchElementException("CDC " + cdcId + " introuvable"));

        ResolvedPlan resolved = resolve(cdc, request);

        // Instantané complet du plan au moment de l'exécution : les plans
        // restent modifiables ensuite, l'historique doit rester exact.
        eventRepository.record(cdcId, "PLAN_EXECUTED", toJson(resolved));

        if (resolved.planId() != null) {
            cdcRepository.setPlanId(cdcId, resolved.planId());
        }

        if ("TRAME".equals(resolved.mode())) {
            List<String> titles = resolved.steps().stream()
                    .sorted(Comparator.comparingInt(ResolvedStep::ordre))
                    .map(ResolvedStep::titre)
                    .toList();
            String tramGuidance = resolved.steps().stream()
                    .map(ResolvedStep::guidance)
                    .filter(g -> g != null && !g.isBlank())
                    .reduce((a, b) -> a + " / " + b)
                    .orElse(null);
            worker.runTrame(cdcId, titles, tramGuidance);
            return new ExecutionResult(1);
        }

        List<ResolvedStep> included = resolved.steps().stream()
                .filter(ResolvedStep::inclus)
                .sorted(Comparator.comparingInt(ResolvedStep::ordre))
                .toList();
        if (included.isEmpty()) {
            throw new IllegalArgumentException("Aucune étape incluse à générer");
        }

        // Le step référence une section par son code (stable), pas par
        // l'ordre du plan (qui peut réordonner l'exécution) ni par
        // template_section_id directement : on retrouve la cdc_section
        // réelle de CE cdc, dont l'ordre propre (fixe, celui du template)
        // est ce qu'attendent markQueued/run.
        Map<String, CdcSectionRepository.Section> byCode = sectionRepository.findAllByCdc(cdcId).stream()
                .collect(Collectors.toMap(CdcSectionRepository.Section::code, s -> s));

        int queued = 0;
        for (ResolvedStep step : included) {
            CdcSectionRepository.Section section = byCode.get(step.code());
            if (section == null) {
                log.warn("CDC {}: étape de plan sans section correspondante (code {})", cdcId, step.code());
                continue;
            }
            sectionRepository.markQueued(cdcId, section.ordre());
            worker.run(cdcId, section.ordre(), step.guidance());
            queued++;
        }
        return new ExecutionResult(queued);
    }

    private ResolvedPlan resolve(CdcRepository.CdcRecord cdc, GenerateRequest request) {
        if (request.planId() != null) {
            if (request.mode() != null || request.etapes() != null) {
                throw new IllegalArgumentException("planId et mode/etapes sont exclusifs l'un de l'autre");
            }
            PlanRepository.PlanView plan = planRepository.findSummaryById(request.planId())
                    .map(summary -> new PlanRepository.PlanView(summary, planRepository.findSteps(summary.id())))
                    .orElseThrow(() -> new NoSuchElementException("Plan " + request.planId() + " introuvable"));
            if (plan.summary().templateId() != cdc.templateId()) {
                throw new IllegalStateException(
                        "Le plan \"" + plan.summary().name() + "\" ne correspond pas au template de ce CDC");
            }
            List<ResolvedStep> steps = plan.steps().stream()
                    .map(s -> new ResolvedStep(s.ordre(), s.code(), s.titre(), s.guidance(), s.inclus()))
                    .toList();
            return new ResolvedPlan(plan.summary().id(), plan.summary().mode(), steps);
        }

        if (request.mode() == null || !MODES.contains(request.mode())) {
            throw new IllegalArgumentException("mode est requis (un de " + MODES + ") pour un plan ponctuel");
        }
        List<CdcTemplateRepository.TemplateSection> templateSections = templateRepository.findSections(cdc.templateId());
        Map<Long, CdcTemplateRepository.TemplateSection> byId = templateSections.stream()
                .collect(Collectors.toMap(CdcTemplateRepository.TemplateSection::id, s -> s));
        List<GenerateRequest.EtapeInput> etapes = request.etapes() == null ? List.of() : request.etapes();
        List<ResolvedStep> steps = etapes.stream()
                .map(e -> {
                    CdcTemplateRepository.TemplateSection ts = byId.get(e.templateSectionId());
                    if (ts == null) {
                        throw new IllegalArgumentException("Section de template inconnue : " + e.templateSectionId());
                    }
                    return new ResolvedStep(e.ordre(), ts.code(), ts.titre(), e.guidance(), e.inclus());
                })
                .toList();
        return new ResolvedPlan(null, request.mode(), steps);
    }

    private String toJson(ResolvedPlan resolved) {
        try {
            return objectMapper.writeValueAsString(resolved);
        } catch (JsonProcessingException ex) {
            // Ne doit jamais arriver (types simples, pas de cycle) ; si ça
            // arrive quand même, l'événement garde une trace plutôt que de
            // faire échouer toute l'exécution pour un problème de journalisation.
            log.error("Échec de sérialisation de l'instantané du plan", ex);
            return "{\"error\":\"snapshot_serialization_failed\"}";
        }
    }

    public record GenerateRequest(Long planId, String mode, List<EtapeInput> etapes) {
        public record EtapeInput(int ordre, long templateSectionId, String guidance, boolean inclus) {}
    }

    public record ExecutionResult(int calls) {}

    private record ResolvedStep(int ordre, String code, String titre, String guidance, boolean inclus) {}

    private record ResolvedPlan(Long planId, String mode, List<ResolvedStep> steps) {}
}
