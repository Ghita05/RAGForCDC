package com.eb.cdcrag.qa;

import com.eb.cdcrag.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class QaController {

    private final QaService qaService;
    private final EmbeddingModel embeddings;
    private final RetrievalRepository retrieval;

    public QaController(QaService qaService, EmbeddingModel embeddings, RetrievalRepository retrieval) {
        this.qaService = qaService;
        this.embeddings = embeddings;
        this.retrieval = retrieval;
    }

    @PostMapping("/qa")
    public QaService.QaResponse ask(
            @RequestParam String question,
            @RequestParam(required = false) String cdcType) {
        return qaService.answer(question, cdcType);
    }

    /** Recherche seule, sans appel au LLM : sert à évaluer la qualité de la recherche. */
    @PostMapping("/search")
    public List<RetrievalRepository.RetrievedChunk> search(
            @RequestParam String question,
            @RequestParam(required = false) String cdcType,
            @RequestParam(defaultValue = "5") int k) {
        float[] qv = embeddings.embed(question, EmbeddingModel.Type.QUERY);
        return retrieval.search(qv, k, cdcType);
    }
}
