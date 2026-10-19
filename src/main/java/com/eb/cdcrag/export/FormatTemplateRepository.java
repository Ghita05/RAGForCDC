package com.eb.cdcrag.export;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Accès SQL pur pour format_template. docx_data (le fichier original) n'est
 * jamais chargé dans les listes/résumés -- seulement pour ré-extraire les
 * styles si besoin -- pour ne pas remonter des BYTEA volumineux à chaque
 * GET /api/format-templates. */
@Repository
public class FormatTemplateRepository {

    private final JdbcTemplate jdbc;
    public FormatTemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long insert(String name, String filename, byte[] docxData, String styleSummaryJson) {
        return jdbc.queryForObject("""
                INSERT INTO format_template (name, filename, docx_data, style_summary, is_default)
                VALUES (?, ?, ?, CAST(? AS jsonb), false)
                RETURNING id
                """, Long.class, name, filename, docxData, styleSummaryJson);
    }

    public List<FormatTemplateSummary> findAll() {
        return jdbc.query("""
                SELECT id, name, filename, style_summary, is_default, created_at
                FROM format_template ORDER BY is_default DESC, created_at DESC
                """, FormatTemplateRepository::mapSummary);
    }

    public Optional<FormatTemplateSummary> findById(long id) {
        return jdbc.query("""
                SELECT id, name, filename, style_summary, is_default, created_at
                FROM format_template WHERE id = ?
                """, FormatTemplateRepository::mapSummary, id).stream().findFirst();
    }

    public Optional<byte[]> findDocxData(long id) {
        return jdbc.query("SELECT docx_data FROM format_template WHERE id = ?",
                (rs, i) -> rs.getBytes("docx_data"), id).stream().findFirst();
    }

    public Optional<FormatStyleRow> findStyle(long id) {
        return jdbc.query("SELECT style_summary FROM format_template WHERE id = ?",
                (rs, i) -> new FormatStyleRow(rs.getString("style_summary")), id).stream().findFirst();
    }

    public Optional<FormatStyleRow> findDefaultStyle() {
        return jdbc.query("SELECT style_summary FROM format_template WHERE is_default = true LIMIT 1",
                (rs, i) -> new FormatStyleRow(rs.getString("style_summary"))).stream().findFirst();
    }

    public void clearDefault() {
        jdbc.update("UPDATE format_template SET is_default = false WHERE is_default = true");
    }

    public int markDefault(long id) {
        return jdbc.update("UPDATE format_template SET is_default = true WHERE id = ?", id);
    }

    public int delete(long id) {
        return jdbc.update("DELETE FROM format_template WHERE id = ?", id);
    }

    private static FormatTemplateSummary mapSummary(ResultSet rs, int i) throws SQLException {
        return new FormatTemplateSummary(rs.getLong("id"), rs.getString("name"), rs.getString("filename"),
                rs.getString("style_summary"), rs.getBoolean("is_default"), toInstant(rs.getTimestamp("created_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record FormatTemplateSummary(long id, String name, String filename, String styleSummaryJson,
                                        boolean isDefault, Instant createdAt) {}

    /** Enveloppe minimale pour ne pas confondre "JSON déjà sérialisé en base"
     * et un FormatStyle désérialisé -- FormatTemplateService fait la
     * conversion via Jackson. */
    public record FormatStyleRow(String styleJson) {}
}
