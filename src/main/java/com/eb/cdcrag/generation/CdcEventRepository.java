package com.eb.cdcrag.generation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class CdcEventRepository {

    private final JdbcTemplate jdbc;
    public CdcEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** payloadJson doit déjà être du JSON valide (voir PlanExecutionService). */
    public long record(long cdcId, String type, String payloadJson) {
        return jdbc.queryForObject("""
                INSERT INTO cdc_event (cdc_id, type, payload)
                VALUES (?, ?, CAST(? AS jsonb))
                RETURNING id
                """, Long.class, cdcId, type, payloadJson);
    }

    public List<CdcEvent> findByCdc(long cdcId) {
        return jdbc.query("""
                SELECT id, cdc_id, type, payload, created_at
                FROM cdc_event WHERE cdc_id = ? ORDER BY created_at DESC
                """,
                (rs, i) -> new CdcEvent(rs.getLong("id"), rs.getLong("cdc_id"), rs.getString("type"),
                        rs.getString("payload"), toInstant(rs.getTimestamp("created_at"))),
                cdcId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record CdcEvent(long id, long cdcId, String type, String payload, Instant createdAt) {}
}
