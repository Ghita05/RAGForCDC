package com.eb.cdcrag.review;

import com.eb.cdcrag.embedding.EmbeddingModel;
import com.eb.cdcrag.generation.CdcRepository;
import com.eb.cdcrag.generation.CdcSectionRepository;
import com.eb.cdcrag.ingestion.ClauseExtractor;
import com.eb.cdcrag.llm.OllamaClient;
import com.eb.cdcrag.qa.RetrievalRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ReviewWorker {

    private static final Logger log = LoggerFactory.getLogger(ReviewWorker.class);
    private static final int COMPARABLES_K = 3;

    private final ObjectMapper mapper = new ObjectMapper();

    private final CdcRepository cdcRepository;
    private final CdcSectionRepository sectionRepository;
    private final ReviewRepository reviewRepository;
    private final ClauseExtractor extractor;
    private final NormRepository norms;
    private final EmbeddingModel embeddings;
    private final RetrievalRepository retrieval;
    private final OllamaClient ollama;

    public ReviewWorker(CdcRepository cdcRepository, CdcSectionRepository sectionRepository,
                        ReviewRepository reviewRepository, ClauseExtractor extractor, NormRepository norms,
                        EmbeddingModel embeddings, RetrievalRepository retrieval,
                        OllamaClient ollama) {
        this.cdcRepository = cdcRepository;
        this.sectionRepository = sectionRepository;
        this.reviewRepository = reviewRepository;
        this.extractor = extractor;
        this.norms = norms;
        this.embeddings = embeddings;
        this.retrieval = retrieval;
        this.ollama = ollama;
    }

    @Async("generationExecutor")
    public void run(long reviewId, long cdcId) {
        long t0 = System.currentTimeMillis();
        try {
            var cdc = cdcRepository.findById(cdcId).orElseThrow();
            // Le CDC est désormais rédigé section par section (cdc_section) ;
            // le body monolithique n'existe plus, on le reconstitue.
            String body = sectionRepository.assembleMarkdown(cdcId);
            ClauseExtractor.Clauses clauses = extractor.extract(body);
            NormRepository.Norms n = norms.forType(cdc.cdcType());
            List<DeviationChecker.Deviation> deviations = DeviationChecker.check(clauses, n);
            log.info("Review {}: {} ecart(s) chiffre(s) detecte(s)", reviewId, deviations.size());

            float[] qv = embeddings.embed(
                    "délais d'exécution, pénalités de retard, garantie et modalités de réception",
                    EmbeddingModel.Type.QUERY);
            List<RetrievalRepository.RetrievedChunk> comparables =
                    retrieval.search(qv, COMPARABLES_K, cdc.cdcType());

            String raw = ollama.generate(
                    ReviewPrompt.build(cdc.cdcType(), body, deviations, comparables),
                    "Review %d (CDC %d)".formatted(reviewId, cdcId));
            String json = cleanJson(raw);
            ObjectNode root;
            try {
                root = (ObjectNode) mapper.readTree(json);
            } catch (Exception badJson) {
                log.warn("Review {}: JSON invalide renvoyé par le LLM", reviewId);
                reviewRepository.markFailed(reviewId, "JSON invalide: " + json);
                return;
            }
            reviewRepository.markDone(reviewId, mapper.writeValueAsString(withEvidence(root, cdc.cdcType(), n, deviations)));
            log.info("Review {}: terminée en {} s", reviewId, (System.currentTimeMillis() - t0) / 1000);
        } catch (Exception ex) {
            log.error("Review {}: échec", reviewId, ex);
            reviewRepository.markFailed(reviewId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /** Les LLM encadrent souvent le JSON de ```json ... ``` malgré la consigne. */
    private String cleanJson(String s) {
        String t = s.trim();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        }
        int start = t.indexOf('{'), end = t.lastIndexOf('}');
        if (start >= 0 && end > start) t = t.substring(start, end + 1);
        return t.trim();
    }

    /** Ajoute "echantillon" et "ecarts" au JSON rédigé par le LLM, avec les
     * valeurs numériques calculées en Java (DeviationChecker) -- jamais
     * rédigées ni recopiées par le modèle, pour qu'un constat "écart de
     * norme" reste vérifiable indépendamment de sa prose. */
    private ObjectNode withEvidence(ObjectNode root, String cdcType, NormRepository.Norms n,
                                    List<DeviationChecker.Deviation> deviations) {
        ObjectNode echantillon = mapper.createObjectNode();
        echantillon.put("cdcType", cdcType);
        echantillon.put("n", n == null ? 0 : n.n());
        root.set("echantillon", echantillon);

        ArrayNode ecarts = mapper.createArrayNode();
        for (DeviationChecker.Deviation d : deviations) {
            ObjectNode node = mapper.createObjectNode();
            node.put("champ", d.champ());
            node.put("valeur", d.valeur());
            node.put("unite", d.unite());
            node.put("median", d.median());
            node.put("min", d.min());
            node.put("max", d.max());
            node.put("n", d.n());
            node.put("sens", d.sens());
            ecarts.add(node);
        }
        root.set("ecarts", ecarts);
        return root;
    }
}
