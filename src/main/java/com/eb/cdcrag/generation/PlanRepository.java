package com.eb.cdcrag.generation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Accès SQL pur pour cdc_generation_plan / cdc_generation_plan_step.
 * L'orchestration transactionnelle (créer/modifier un plan ET ses étapes
 * ensemble) vit dans PlanService, pas ici. */
@Repository
public class PlanRepository {

    private final JdbcTemplate jdbc;
    public PlanRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static final String SUMMARY_SELECT = """
            SELECT p.id, p.name, p.description, p.template_id, t.name AS template_name,
                   p.cdc_type, p.mode, p.created_at, p.updated_at,
                   count(s.id) AS total_steps,
                   count(s.id) FILTER (WHERE s.inclus) AS included_steps
            FROM cdc_generation_plan p
            JOIN cdc_template t ON t.id = p.template_id
            LEFT JOIN cdc_generation_plan_step s ON s.plan_id = p.id
            """;

    public List<PlanSummary> findAll() {
        return jdbc.query(SUMMARY_SELECT + """
                GROUP BY p.id, p.name, p.description, p.template_id, t.name, p.cdc_type, p.mode,
                         p.created_at, p.updated_at
                ORDER BY p.updated_at DESC
                """, PlanRepository::mapSummary);
    }

    public Optional<PlanSummary> findSummaryById(long id) {
        return jdbc.query(SUMMARY_SELECT + """
                WHERE p.id = ?
                GROUP BY p.id, p.name, p.description, p.template_id, t.name, p.cdc_type, p.mode,
                         p.created_at, p.updated_at
                """, PlanRepository::mapSummary, id).stream().findFirst();
    }

    public List<PlanStep> findSteps(long planId) {
        return jdbc.query("""
                SELECT s.id, s.plan_id, s.ordre, s.template_section_id, ts.code, ts.titre,
                       ts.consigne, ts.obligatoire, s.guidance, s.inclus
                FROM cdc_generation_plan_step s
                JOIN cdc_template_section ts ON ts.id = s.template_section_id
                WHERE s.plan_id = ?
                ORDER BY s.ordre
                """, PlanRepository::mapStep, planId);
    }

    public long insertPlan(String name, String description, long templateId, String cdcType, String mode) {
        return jdbc.queryForObject("""
                INSERT INTO cdc_generation_plan (name, description, template_id, cdc_type, mode)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, name, description, templateId, cdcType, mode);
    }

    public void updatePlanRow(long id, String name, String description, String cdcType, String mode) {
        jdbc.update("""
                UPDATE cdc_generation_plan SET name = ?, description = ?, cdc_type = ?, mode = ?, updated_at = now()
                WHERE id = ?
                """, name, description, cdcType, mode, id);
    }

    public void deleteSteps(long planId) {
        jdbc.update("DELETE FROM cdc_generation_plan_step WHERE plan_id = ?", planId);
    }

    public void insertSteps(long planId, List<StepInput> steps) {
        if (steps.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO cdc_generation_plan_step (plan_id, ordre, template_section_id, guidance, inclus)
                VALUES (?, ?, ?, ?, ?)
                """, steps, steps.size(), (ps, s) -> {
                    ps.setLong(1, planId);
                    ps.setInt(2, s.ordre());
                    ps.setLong(3, s.templateSectionId());
                    if (s.guidance() == null) ps.setNull(4, Types.VARCHAR); else ps.setString(4, s.guidance());
                    ps.setBoolean(5, s.inclus());
                });
    }

    private static PlanSummary mapSummary(ResultSet rs, int i) throws SQLException {
        return new PlanSummary(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getLong("template_id"), rs.getString("template_name"), rs.getString("cdc_type"),
                rs.getString("mode"), rs.getInt("total_steps"), rs.getInt("included_steps"),
                toInstant(rs.getTimestamp("created_at")), toInstant(rs.getTimestamp("updated_at")));
    }

    private static PlanStep mapStep(ResultSet rs, int i) throws SQLException {
        return new PlanStep(rs.getLong("id"), rs.getLong("plan_id"), rs.getInt("ordre"),
                rs.getLong("template_section_id"), rs.getString("code"), rs.getString("titre"),
                rs.getString("consigne"), rs.getBoolean("obligatoire"), rs.getString("guidance"),
                rs.getBoolean("inclus"));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    public record PlanSummary(long id, String name, String description, long templateId, String templateName,
                              String cdcType, String mode, int totalSteps, int includedSteps,
                              Instant createdAt, Instant updatedAt) {}

    public record PlanStep(long id, long planId, int ordre, long templateSectionId, String code, String titre,
                           String consigne, boolean obligatoire, String guidance, boolean inclus) {}

    public record PlanView(PlanSummary summary, List<PlanStep> steps) {}

    public record StepInput(int ordre, long templateSectionId, String guidance, boolean inclus) {}
}
