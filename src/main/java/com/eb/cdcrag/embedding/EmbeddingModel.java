package com.eb.cdcrag.embedding;

import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.translate.TranslateException;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Loads intfloat/multilingual-e5-small one time at startup 
 * Turns text into a normalized float[384]
 * The e5 family needs an instruction prefix that DJL
 * doesn't add for us, so we prepend it here:
 *   - PASSAGE for documents/chunks at ingestion
 *   - QUERY   for questions at search time
 */
@Component
public class EmbeddingModel {

    public enum Type { QUERY, PASSAGE }

    private ZooModel<String, float[]> model;

    @PostConstruct
    public void init() throws Exception {
        Criteria<String, float[]> criteria = Criteria.builder()
                .setTypes(String.class, float[].class)
                .optModelUrls("djl://ai.djl.huggingface.pytorch/intfloat/multilingual-e5-small")
                .optEngine("PyTorch")
                .optArgument("pooling", "mean")   // e5 uses mean pooling
                .optArgument("normalize", true)   // unit vectors -> cosine is correct
                .optTranslatorFactory(new TextEmbeddingTranslatorFactory())
                .build();
        this.model = criteria.loadModel();
    }

    public float[] embed(String text, Type type) {
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            return predictor.predict(prefix(type) + text);
        } catch (TranslateException e) {
            throw new RuntimeException("Embedding failed", e);
        }
    }

    public List<float[]> embed(List<String> texts, Type type) {
        List<float[]> out = new ArrayList<>(texts.size());
        try (Predictor<String, float[]> predictor = model.newPredictor()) {
            for (String t : texts) {
                out.add(predictor.predict(prefix(type) + t));
            }
        } catch (TranslateException e) {
            throw new RuntimeException("Embedding failed", e);
        }
        return out;
    }

    private String prefix(Type type) {
        return type == Type.QUERY ? "query: " : "passage: ";
    }

    @PreDestroy
    public void close() {
        if (model != null) model.close();
    }
}
