package com.eb.cdcrag.generation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CdcTemplateRepository {

    private final JdbcTemplate jdbc;
    public CdcTemplateRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Long> findDefaultTemplateId() {
        return jdbc.query("SELECT id FROM cdc_template WHERE is_default = true LIMIT 1",
                (rs, i) -> rs.getLong("id")).stream().findFirst();
    }

    public List<TemplateSection> findSections(long templateId) {
        return jdbc.query("""
                SELECT id, ordre, code, titre, consigne, obligatoire
                FROM cdc_template_section WHERE template_id = ? ORDER BY ordre
                """,
                (rs, i) -> new TemplateSection(rs.getLong("id"), rs.getInt("ordre"),
                        rs.getString("code"), rs.getString("titre"),
                        rs.getString("consigne"), rs.getBoolean("obligatoire")),
                templateId);
    }

    /** Sections du template par défaut. Liste vide si aucun template par défaut. */
    public List<TemplateSection> findDefaultSections() {
        return findDefaultTemplateId().map(this::findSections).orElse(List.of());
    }

    private static final String SUMMARY_SELECT = """
            SELECT t.id, t.name, t.description, t.cdc_type, t.owner, t.is_default,
                   count(ts.id) AS section_count
            FROM cdc_template t
            LEFT JOIN cdc_template_section ts ON ts.template_id = t.id
            """;
    private static final String SUMMARY_GROUP_BY =
            " GROUP BY t.id, t.name, t.description, t.cdc_type, t.owner, t.is_default ";

    public List<TemplateSummary> findAllTemplates() {
        return jdbc.query(SUMMARY_SELECT + SUMMARY_GROUP_BY + " ORDER BY t.is_default DESC, t.name",
                CdcTemplateRepository::mapSummary);
    }

    public Optional<TemplateSummary> findTemplateById(long id) {
        return jdbc.query(SUMMARY_SELECT + " WHERE t.id = ? " + SUMMARY_GROUP_BY,
                CdcTemplateRepository::mapSummary, id).stream().findFirst();
    }

    /** description et cdcType sont optionnels (texte libre) ; owner et
     * is_default restent gérés hors de cette création -- un nouveau modèle
     * n'est jamais créé par défaut (un seul à la fois, voir
     * findDefaultTemplateId). */
    public long insertTemplate(String name, String description, String cdcType) {
        return jdbc.queryForObject("""
                INSERT INTO cdc_template (name, description, cdc_type, is_default)
                VALUES (?, ?, ?, false)
                RETURNING id
                """, Long.class, name, description, cdcType);
    }

    public int updateTemplateMeta(long id, String name, String description, String cdcType) {
        return jdbc.update("""
                UPDATE cdc_template SET name = ?, description = ?, cdc_type = ? WHERE id = ?
                """, name, description, cdcType, id);
    }

    /** Échoue avec DataIntegrityViolationException si un CDC ou un plan
     * référence encore ce template (pas de ON DELETE CASCADE sur ces FK,
     * volontairement -- voir TemplateService.delete). */
    public void deleteTemplate(long id) {
        jdbc.update("DELETE FROM cdc_template WHERE id = ?", id);
    }

    /** Ajoute une section. `code` est un identifiant stable (utilisé par
     * SectionDetector et le filtre de recherche vectorielle) : contrairement
     * à ordre/titre/consigne/obligatoire, on ne le modifie pas après coup. */
    public long insertSection(long templateId, int ordre, String code, String titre,
                              String consigne, boolean obligatoire) {
        return jdbc.queryForObject("""
                INSERT INTO cdc_template_section (template_id, ordre, code, titre, consigne, obligatoire)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, templateId, ordre, code, titre, consigne, obligatoire);
    }

    /** Insertion en lot des sections d'un template dupliqué (voir
     * TemplateService.duplicate) -- mêmes code/ordre que la source, sur le
     * nouveau template_id. */
    public void insertSections(long templateId, List<SectionInput> sections) {
        if (sections.isEmpty()) return;
        jdbc.batchUpdate("""
                INSERT INTO cdc_template_section (template_id, ordre, code, titre, consigne, obligatoire)
                VALUES (?, ?, ?, ?, ?, ?)
                """, sections, sections.size(), (ps, s) -> {
                    ps.setLong(1, templateId);
                    ps.setInt(2, s.ordre());
                    ps.setString(3, s.code());
                    ps.setString(4, s.titre());
                    ps.setString(5, s.consigne());
                    ps.setBoolean(6, s.obligatoire());
                });
    }

    public int updateSection(long sectionId, int ordre, String titre, String consigne, boolean obligatoire) {
        return jdbc.update("""
                UPDATE cdc_template_section SET ordre = ?, titre = ?, consigne = ?, obligatoire = ?
                WHERE id = ?
                """, ordre, titre, consigne, obligatoire, sectionId);
    }

    public int deleteSection(long sectionId) {
        return jdbc.update("DELETE FROM cdc_template_section WHERE id = ?", sectionId);
    }

    private static TemplateSummary mapSummary(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new TemplateSummary(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
                rs.getString("cdc_type"), rs.getString("owner"), rs.getBoolean("is_default"),
                rs.getInt("section_count"));
    }

    public record TemplateSection(long id, int ordre, String code, String titre,
                                  String consigne, boolean obligatoire) {}

    public record TemplateSummary(long id, String name, String description, String cdcType, String owner,
                                  boolean isDefault, int sectionCount) {}

    public record TemplateView(TemplateSummary template, List<TemplateSection> sections) {}

    public record SectionInput(int ordre, String code, String titre, String consigne, boolean obligatoire) {}
}
