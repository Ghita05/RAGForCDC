package com.eb.cdcrag.generation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class CdcSectionRepository {

    private final JdbcTemplate jdbc;
    public CdcSectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Crée une ligne cdc_section EMPTY pour chaque section du template. */
    public void insertEmptySections(long cdcId, List<CdcTemplateRepository.TemplateSection> sections) {
        jdbc.batchUpdate("""
                INSERT INTO cdc_section (cdc_id, template_section_id, ordre, titre, status)
                VALUES (?, ?, ?, ?, 'EMPTY')
                """,
                sections, sections.size(),
                (PreparedStatement ps, CdcTemplateRepository.TemplateSection s) -> {
                    ps.setLong(1, cdcId);
                    ps.setLong(2, s.id());
                    ps.setInt(3, s.ordre());
                    ps.setString(4, s.titre());
                });
    }

    /** Posée à la mise en file (appel synchrone), avant même que le pool à un
     * seul thread ait pu commencer le traitement. Ne pas confondre avec
     * GENERATING, posée par le worker au tout début du traitement effectif. */
    public void markQueued(long cdcId, int ordre) {
        jdbc.update("""
                UPDATE cdc_section SET status = 'QUEUED', error = NULL, updated_at = now()
                WHERE cdc_id = ? AND ordre = ?
                """, cdcId, ordre);
    }

    public void markGenerating(long cdcId, int ordre) {
        jdbc.update("""
                UPDATE cdc_section SET status = 'GENERATING', error = NULL, updated_at = now()
                WHERE cdc_id = ? AND ordre = ?
                """, cdcId, ordre);
    }

    public void markDraft(long cdcId, int ordre, String body) {
        jdbc.update("""
                UPDATE cdc_section SET status = 'DRAFT', body = ?, error = NULL, updated_at = now()
                WHERE cdc_id = ? AND ordre = ?
                """, body, cdcId, ordre);
    }

    public void markFailed(long cdcId, int ordre, String error) {
        jdbc.update("""
                UPDATE cdc_section SET status = 'FAILED', error = ?, updated_at = now()
                WHERE cdc_id = ? AND ordre = ?
                """, error, cdcId, ordre);
    }

    /** DRAFT -> APPROVED. N'effectue la transition que depuis DRAFT : renvoie
     * le nombre de lignes modifiées (0 si la section n'existe pas ou n'était
     * pas en DRAFT), pour que l'appelant distingue les deux cas. */
    public int markApproved(long cdcId, int ordre) {
        return jdbc.update("""
                UPDATE cdc_section SET status = 'APPROVED', error = NULL, updated_at = now()
                WHERE cdc_id = ? AND ordre = ? AND status = 'DRAFT'
                """, cdcId, ordre);
    }

    public Optional<Section> findByCdcAndOrdre(long cdcId, int ordre) {
        return jdbc.query(SELECT + " WHERE cs.cdc_id = ? AND cs.ordre = ?",
                CdcSectionRepository::mapSection, cdcId, ordre).stream().findFirst();
    }

    public List<Section> findAllByCdc(long cdcId) {
        return jdbc.query(SELECT + " WHERE cs.cdc_id = ? ORDER BY cs.ordre",
                CdcSectionRepository::mapSection, cdcId);
    }

    public List<Section> findObligatoireByCdc(long cdcId) {
        return findAllByCdc(cdcId).stream().filter(Section::obligatoire).toList();
    }

    /** Concatène les sections rédigées (DRAFT ou APPROVED), dans l'ordre du
     * template, avec le même format de titre numéroté ("6. Spécifications
     * techniques") que le corpus synthétique afin que SectionDetector
     * reconnaisse chaque section à la réingestion. Utilisé par le Reviewer
     * (qui raisonne sur le CDC entier), par /approve (texte à ingérer) et
     * par /export. Les sections encore EMPTY/QUEUED/GENERATING/FAILED sont
     * ignorées : un null d'extraction sur une section non rédigée n'est pas
     * une anomalie. Filtrer sur le contenu (body != null) plutôt que sur un
     * statut précis évite qu'une section APPROVED disparaisse du texte
     * assemblé. */
    public String assembleMarkdown(long cdcId) {
        return findAllByCdc(cdcId).stream()
                .filter(s -> s.body() != null)
                .map(s -> s.ordre() + ". " + s.titre() + "\n\n" + s.body())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
    }

    private static final String SELECT = """
            SELECT cs.id, cs.cdc_id, cs.ordre, cs.titre, cs.body, cs.status, cs.error,
                   ts.code, ts.consigne, ts.obligatoire
            FROM cdc_section cs JOIN cdc_template_section ts ON ts.id = cs.template_section_id
            """;

    private static Section mapSection(ResultSet rs, int i) throws SQLException {
        return new Section(rs.getLong("id"), rs.getLong("cdc_id"), rs.getInt("ordre"),
                rs.getString("code"), rs.getString("titre"), rs.getString("consigne"),
                rs.getBoolean("obligatoire"), rs.getString("body"), rs.getString("status"),
                rs.getString("error"));
    }

    public record Section(long id, long cdcId, int ordre, String code, String titre,
                          String consigne, boolean obligatoire, String body, String status,
                          String error) {}
}
