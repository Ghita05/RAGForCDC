package com.eb.cdcrag.ingestion;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class DocumentRepository {

    private final JdbcTemplate jdbc;
    public DocumentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Corpus historique (synthétique + réel), les plus récemment ingérés en premier. */
    public List<DocumentSummary> findAll() {
        return jdbc.query("""
                SELECT id, filename, cdc_type, created_at, delai_jours, penalite_pct,
                       plafond_pct, garantie_mois, sla_pct, gtr_heures, gti_heures
                FROM document
                ORDER BY created_at DESC
                """,
                (rs, i) -> new DocumentSummary(rs.getLong("id"), rs.getString("filename"),
                        rs.getString("cdc_type"), toInstant(rs.getTimestamp("created_at")),
                        (Integer) rs.getObject("delai_jours"),
                        rs.getBigDecimal("penalite_pct"), rs.getBigDecimal("plafond_pct"),
                        (Integer) rs.getObject("garantie_mois"), rs.getBigDecimal("sla_pct"),
                        (Integer) rs.getObject("gtr_heures"), (Integer) rs.getObject("gti_heures")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record DocumentSummary(long id, String filename, String cdcType, Instant createdAt,
                                  Integer delaiJours, BigDecimal penalitePct, BigDecimal plafondPct,
                                  Integer garantieMois, BigDecimal slaPct, Integer gtrHeures,
                                  Integer gtiHeures) {}
}
