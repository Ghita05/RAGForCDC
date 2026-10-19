package com.eb.cdcrag.settings;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Table singleton (une seule ligne, id = 1 toujours) : upsert explicite
 * plutôt qu'une contrainte SQL dédiée -- cohérent avec le reste du projet
 * (aucune autre table n'utilise ce genre de contrainte), et il n'y a qu'un
 * seul appelant (OrganisationSettingsService) donc le risque de double
 * insertion concurrente est nul en pratique. */
@Repository
public class OrganisationSettingsRepository {

    private final JdbcTemplate jdbc;
    public OrganisationSettingsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<OrganisationSettings> find() {
        return jdbc.query("""
                SELECT nom_organisation, direction, mention_confidentialite, format_reference,
                       pied_de_page, contact_service, contact_adresse, contact_courriel,
                       logo IS NOT NULL AS has_logo, logo_content_type
                FROM organisation_settings WHERE id = 1
                """,
                (rs, i) -> new OrganisationSettings(
                        rs.getString("nom_organisation"), rs.getString("direction"),
                        rs.getString("mention_confidentialite"), rs.getString("format_reference"),
                        rs.getString("pied_de_page"), rs.getString("contact_service"),
                        rs.getString("contact_adresse"), rs.getString("contact_courriel"),
                        rs.getBoolean("has_logo"), rs.getString("logo_content_type")))
                .stream().findFirst();
    }

    public Optional<LogoData> findLogo() {
        return jdbc.query("SELECT logo, logo_content_type FROM organisation_settings WHERE id = 1 AND logo IS NOT NULL",
                (rs, i) -> new LogoData(rs.getBytes("logo"), rs.getString("logo_content_type")))
                .stream().findFirst();
    }

    /** logo/logoContentType à null = ne pas toucher au logo existant (voir
     * OrganisationSettingsService -- distinct de clearLogo, qui l'efface
     * explicitement). */
    public void save(OrganisationSettings s) {
        jdbc.update("""
                INSERT INTO organisation_settings
                    (id, nom_organisation, direction, mention_confidentialite, format_reference,
                     pied_de_page, contact_service, contact_adresse, contact_courriel, updated_at)
                VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (id) DO UPDATE SET
                    nom_organisation = EXCLUDED.nom_organisation,
                    direction = EXCLUDED.direction,
                    mention_confidentialite = EXCLUDED.mention_confidentialite,
                    format_reference = EXCLUDED.format_reference,
                    pied_de_page = EXCLUDED.pied_de_page,
                    contact_service = EXCLUDED.contact_service,
                    contact_adresse = EXCLUDED.contact_adresse,
                    contact_courriel = EXCLUDED.contact_courriel,
                    updated_at = now()
                """, s.nomOrganisation(), s.direction(), s.mentionConfidentialite(), s.formatReference(),
                s.piedDePage(), s.contactService(), s.contactAdresse(), s.contactCourriel());
    }

    public void saveLogo(byte[] logo, String contentType) {
        ensureRowExists();
        jdbc.update("UPDATE organisation_settings SET logo = ?, logo_content_type = ?, updated_at = now() WHERE id = 1",
                logo, contentType);
    }

    public void clearLogo() {
        jdbc.update("UPDATE organisation_settings SET logo = NULL, logo_content_type = NULL, updated_at = now() WHERE id = 1");
    }

    private void ensureRowExists() {
        jdbc.update("INSERT INTO organisation_settings (id) VALUES (1) ON CONFLICT (id) DO NOTHING");
    }

    public record LogoData(byte[] bytes, String contentType) {}
}
