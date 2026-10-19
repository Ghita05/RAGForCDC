package com.eb.cdcrag.qa;

import com.eb.cdcrag.embedding.VectorLiterals;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class RetrievalRepository {

    private final JdbcTemplate jdbc;
    public RetrievalRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<RetrievedChunk> search(float[] queryVector, int topK, String cdcType) {
        return search(queryVector, topK, cdcType, null);
    }

    /** sectionCode filtre en plus sur chunk.section_code (voir SectionDetector) ;
     * les chunks non rattachés à une section connue (section_code NULL) sont
     * exclus dès qu'un sectionCode est demandé. */
    public List<RetrievedChunk> search(float[] queryVector, int topK, String cdcType, String sectionCode) {
        String vec = VectorLiterals.toPgVector(queryVector);

        String base = """
            SELECT c.content, d.filename, d.cdc_type,
                   1 - (c.embedding <=> CAST(? AS vector)) AS similarity
              FROM chunk c
              JOIN document d ON d.id = c.document_id
            """;

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        params.add(vec);
        if (cdcType != null && !cdcType.isBlank()) {
            conditions.add("d.cdc_type = ?");
            params.add(cdcType);
        }
        if (sectionCode != null && !sectionCode.isBlank()) {
            conditions.add("c.section_code = ?");
            params.add(sectionCode);
        }

        String sql = base
                + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                + " ORDER BY c.embedding <=> CAST(? AS vector) LIMIT ?";
        params.add(vec);
        params.add(topK);

        return jdbc.query(sql, (rs, i) -> new RetrievedChunk(
                rs.getString("content"),
                rs.getString("filename"),
                rs.getString("cdc_type"),
                rs.getDouble("similarity")), params.toArray());
    }

    public record RetrievedChunk(String content, String filename, String cdcType, double similarity) {}
}
