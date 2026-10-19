package com.eb.cdcrag.review;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ReviewRepository {

    private final JdbcTemplate jdbc;
    public ReviewRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long start(long cdcId) {
        return jdbc.queryForObject(
                "INSERT INTO review (cdc_id) VALUES (?) RETURNING id", Long.class, cdcId);
    }

    public void markDone(long id, String findingsJson) {
        jdbc.update("UPDATE review SET status='DONE', findings=CAST(? AS jsonb), error=NULL WHERE id=?",
                findingsJson, id);
    }

    public void markFailed(long id, String error) {
        jdbc.update("UPDATE review SET status='FAILED', error=? WHERE id=?", error, id);
    }

    public Optional<ReviewRecord> findById(long id) {
        return jdbc.query("SELECT id, cdc_id, status, findings, error FROM review WHERE id=?",
                (rs, i) -> new ReviewRecord(rs.getLong("id"), rs.getLong("cdc_id"),
                        rs.getString("status"), rs.getString("findings"), rs.getString("error")),
                id).stream().findFirst();
    }

    public record ReviewRecord(long id, long cdcId, String status, String findings, String error) {}
}
