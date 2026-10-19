package com.eb.cdcrag.ingestion;

import com.eb.cdcrag.embedding.EmbeddingModel;
import com.eb.cdcrag.embedding.VectorLiterals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final Chunker chunker;
    private final EmbeddingModel embeddings;
    private final ClauseExtractor clauseExtractor;
    private final SectionDetector sectionDetector;
    private final JdbcTemplate jdbc;

    public IngestionService(Chunker chunker, EmbeddingModel embeddings,
                            ClauseExtractor clauseExtractor, SectionDetector sectionDetector,
                            JdbcTemplate jdbc) {
        this.chunker = chunker;
        this.embeddings = embeddings;
        this.clauseExtractor = clauseExtractor;
        this.sectionDetector = sectionDetector;
        this.jdbc = jdbc;
    }

    @Transactional
    public IngestResult ingest(String filename, String cdcType, String content) {
        // Découpage par section AVANT le chunking : chaque chunk hérite du
        // sectionCode de son segment, utilisé par la génération section par
        // section pour filtrer la recherche vectorielle (RetrievalRepository).
        List<String> chunkTexts = new ArrayList<>();
        List<String> chunkSections = new ArrayList<>();
        for (SectionDetector.Segment segment : sectionDetector.split(content)) {
            for (String piece : chunker.chunk(segment.text())) {
                chunkTexts.add(piece);
                chunkSections.add(segment.sectionCode());
            }
        }
        if (chunkTexts.isEmpty()) throw new IllegalArgumentException("Nothing to ingest: " + filename);

        // Extraction sur le texte complet, avant découpage : une clause ne doit pas
        // être coupée entre deux chunks.
        ClauseExtractor.Clauses c = clauseExtractor.extract(content);
        if (c.isEmpty()) log.warn("Aucune valeur contractuelle extraite de {}", filename);

        Long documentId = jdbc.queryForObject("""
                INSERT INTO document (filename, cdc_type, delai_jours, penalite_pct,
                                      plafond_pct, garantie_mois, sla_pct, gtr_heures, gti_heures)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """,
                Long.class, filename, cdcType, c.delaiJours(), c.penalitePct(),
                c.plafondPct(), c.garantieMois(), c.slaPct(), c.gtrHeures(), c.gtiHeures());

        List<float[]> vectors = embeddings.embed(chunkTexts, EmbeddingModel.Type.PASSAGE);
        for (int i = 0; i < chunkTexts.size(); i++) {
            jdbc.update(
                "INSERT INTO chunk (document_id, chunk_index, content, embedding, section_code) " +
                "VALUES (?, ?, ?, CAST(? AS vector), ?)",
                documentId, i, chunkTexts.get(i), VectorLiterals.toPgVector(vectors.get(i)), chunkSections.get(i));
        }
        return new IngestResult(documentId, filename, chunkTexts.size(), c);
    }

    public Optional<Long> findDocumentId(String filename) {
        return jdbc.query("SELECT id FROM document WHERE filename = ?",
                (rs, i) -> rs.getLong("id"), filename).stream().findFirst();
    }

    public record IngestResult(long documentId, String filename, int chunkCount,
                               ClauseExtractor.Clauses clauses) {}
}
