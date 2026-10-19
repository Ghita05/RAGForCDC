package com.eb.cdcrag.settings;

/** Paramètres de marque de l'organisation cliente (une seule ligne en base,
 * voir OrganisationSettingsRepository) : le CONTENU affiché sur la page de
 * garde et en pied de page des CDC exportés -- à distinguer du
 * FormatStyle (modèle de mise en forme, qui définit l'APPARENCE : polices,
 * couleurs, marges) et du template (qui définit les SECTIONS). hasLogo/
 * logoContentType seulement ici -- les octets du logo se récupèrent via
 * OrganisationSettingsRepository.findLogo(), pas chargés à chaque lecture
 * des paramètres. */
public record OrganisationSettings(
        String nomOrganisation,
        String direction,
        String mentionConfidentialite,
        String formatReference,
        String piedDePage,
        String contactService,
        String contactAdresse,
        String contactCourriel,
        boolean hasLogo,
        String logoContentType) {

    /** Valeurs neutres quand aucune ligne n'existe encore en base : l'export
     * ne doit jamais échouer faute de configuration. formatReference
     * reprend le comportement historique ("CDC-" + id du CDC) pour ne pas
     * changer silencieusement les références déjà émises avant l'ajout de
     * cette fonctionnalité. */
    public static OrganisationSettings empty() {
        return new OrganisationSettings(null, null, null, "CDC-{numero}", null, null, null, null, false, null);
    }
}
