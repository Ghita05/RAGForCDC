package com.eb.cdcrag.qa;

import com.eb.cdcrag.embedding.EmbeddingModel;
import com.eb.cdcrag.llm.OllamaClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

@Service
public class QaService {

    private static final int TOP_K = 5;

    private final EmbeddingModel embeddings;
    private final RetrievalRepository retrieval;
    private final OllamaClient ollama;

    public QaService(EmbeddingModel embeddings, RetrievalRepository retrieval, OllamaClient ollama) {
        this.embeddings = embeddings;
        this.retrieval = retrieval;
        this.ollama = ollama;
    }

    public QaResponse answer(String question, String cdcType) {
        float[] qv = embeddings.embed(question, EmbeddingModel.Type.QUERY);
        List<RetrievalRepository.RetrievedChunk> hits = retrieval.search(qv, TOP_K, cdcType);

        if (hits.isEmpty()) {
            return new QaResponse(
                "Aucun document pertinent n'a été trouvé dans la base.", List.of());
        }
        return new QaResponse(ollama.generate(buildPrompt(question, hits), "QA"), hits);
    }

    private String buildPrompt(String question, List<RetrievalRepository.RetrievedChunk> hits) {
        String context = IntStream.range(0, hits.size())
            .mapToObj(i -> "[Source " + (i + 1) + " — " + hits.get(i).filename() + "]\n" + hits.get(i).content())
            .reduce((a, b) -> a + "\n\n" + b).orElse("");

        return """
            Tu es un assistant expert en cahiers des charges (CDC).
            Réponds à la QUESTION en te basant UNIQUEMENT sur le CONTEXTE ci-dessous.
            Si le contexte ne contient pas l'information, réponds exactement :
            « L'information n'est pas présente dans les documents fournis. »
            Cite les sources utilisées, par exemple [Source 1].

            CONTEXTE :
            %s

            QUESTION : %s

            RÉPONSE :""".formatted(context, question);
    }

    public record QaResponse(String answer, List<RetrievalRepository.RetrievedChunk> sources) {}
}
