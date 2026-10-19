package com.eb.cdcrag.generation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class CdcRepository {

    private final JdbcTemplate jdbc;
    public CdcRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Crée le CDC (statut DRAFT par défaut). Le contenu vit désormais dans
     * les sections (cdc_section) plutôt que dans un champ body monolithique.
     * planId peut être null : un CDC créé sans plan enregistré, ou dont le
     * plan sera choisi seulement à l'exécution (voir setPlanId). */
    public long insert(String title, String cdcType, long templateId, String brief, Long planId) {
        return jdbc.queryForObject("""
                INSERT INTO cdc (title, cdc_type, template_id, brief, plan_id)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, title, cdcType, templateId, brief, planId);
    }

    public void markApproved(long id) {
        jdbc.update("UPDATE cdc SET status = 'APPROVED', error = NULL, approved_at = now() WHERE id = ?", id);
    }

    /** Posé à l'exécution d'un plan enregistré, si le CDC n'en avait pas
     * encore un (ou pour refléter le dernier plan utilisé). */
    public void setPlanId(long id, long planId) {
        jdbc.update("UPDATE cdc SET plan_id = ? WHERE id = ?", planId, id);
    }

    /** formatTemplateId peut être remis à null (retour à l'habillage par
     * défaut, voir ExportService.resolveStyle). */
    public void setFormatTemplateId(long id, Long formatTemplateId) {
        jdbc.update("UPDATE cdc SET format_template_id = ? WHERE id = ?", formatTemplateId, id);
    }

    /** Utilisé par le mode TRAME (un seul appel, pas de corps de section :
     * voir PlanExecutionService) -- body était le champ monolithique de
     * l'ancien flux de génération, resté inutilisé depuis le passage aux
     * sections, réemployé ici pour ce cas précis. */
    public void updateBody(long id, String body) {
        jdbc.update("UPDATE cdc SET body = ?, error = NULL WHERE id = ?", body, id);
    }

    public void markError(long id, String error) {
        jdbc.update("UPDATE cdc SET error = ? WHERE id = ?", error, id);
    }

    public Optional<CdcRecord> findById(long id) {
        return jdbc.query("""
                SELECT c.id, c.title, c.cdc_type, c.status, c.error, c.template_id, c.brief, c.body,
                       c.plan_id, p.name AS plan_name, c.format_template_id, f.name AS format_template_name
                FROM cdc c
                LEFT JOIN cdc_generation_plan p ON p.id = c.plan_id
                LEFT JOIN format_template f ON f.id = c.format_template_id
                WHERE c.id = ?
                """,
                (rs, i) -> new CdcRecord(rs.getLong("id"), rs.getString("title"),
                        rs.getString("cdc_type"), rs.getString("status"),
                        rs.getString("error"), (Long) rs.getObject("template_id"),
                        rs.getString("brief"), rs.getString("body"), (Long) rs.getObject("plan_id"),
                        rs.getString("plan_name"), (Long) rs.getObject("format_template_id"),
                        rs.getString("format_template_name")),
                id).stream().findFirst();
    }

    public Optional<String> findLatestReviewStatus(long cdcId) {
        return jdbc.query("""
                SELECT status FROM review WHERE cdc_id = ? ORDER BY created_at DESC LIMIT 1
                """, (rs, i) -> rs.getString("status"), cdcId).stream().findFirst();
    }

    /** Liste des CDC avec avancement (nombre de sections DRAFT/APPROVED sur le
     * total), dates et statut de la dernière relecture, les plus récents en
     * premier. reviewStatus vient de la relecture la plus récente (LATERAL,
     * pas de GROUP BY nécessaire puisque chaque jointure ne produit qu'une
     * ligne par CDC). */
    public List<CdcSummary> findAllSummaries() {
        return jdbc.query("""
                SELECT c.id, c.title, c.cdc_type, c.status, c.created_at, c.approved_at,
                       COALESCE(sec.total, 0)    AS total,
                       COALESCE(sec.draft, 0)    AS draft,
                       COALESCE(sec.approved, 0) AS approved,
                       GREATEST(c.created_at, c.approved_at, sec.max_updated) AS last_modified_at,
                       r.status AS review_status
                FROM cdc c
                LEFT JOIN (
                    SELECT cdc_id, count(*) AS total,
                           count(*) FILTER (WHERE status = 'DRAFT')    AS draft,
                           count(*) FILTER (WHERE status = 'APPROVED') AS approved,
                           max(updated_at) AS max_updated
                    FROM cdc_section GROUP BY cdc_id
                ) sec ON sec.cdc_id = c.id
                LEFT JOIN LATERAL (
                    SELECT status FROM review WHERE review.cdc_id = c.id
                    ORDER BY created_at DESC LIMIT 1
                ) r ON true
                ORDER BY c.id DESC
                """,
                (rs, i) -> new CdcSummary(rs.getLong("id"), rs.getString("title"),
                        rs.getString("cdc_type"), rs.getString("status"),
                        rs.getInt("total"), rs.getInt("draft"), rs.getInt("approved"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("last_modified_at")),
                        toInstant(rs.getTimestamp("approved_at")),
                        rs.getString("review_status")));
    }

    /** Types de CDC réellement présents en base (cdc ET document -- un type
     * peut n'exister que sur un document ingéré, pas encore utilisé pour un
     * CDC généré, ou inversement), pour alimenter les sélecteurs de type
     * côté frontend sans liste figée. */
    public List<String> findDistinctTypes() {
        return jdbc.queryForList("""
                SELECT DISTINCT cdc_type FROM cdc WHERE cdc_type IS NOT NULL AND cdc_type <> ''
                UNION
                SELECT DISTINCT cdc_type FROM document WHERE cdc_type IS NOT NULL AND cdc_type <> ''
                ORDER BY cdc_type
                """, String.class);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record CdcRecord(long id, String title, String cdcType, String status,
                            String error, Long templateId, String brief, String body,
                            Long planId, String planName, Long formatTemplateId, String formatTemplateName) {}

    public record CdcSummary(long id, String title, String cdcType, String status,
                             int totalSections, int draftSections, int approvedSections,
                             Instant createdAt, Instant lastModifiedAt, Instant approvedAt,
                             String reviewStatus) {}
}

