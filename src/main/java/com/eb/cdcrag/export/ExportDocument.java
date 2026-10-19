package com.eb.cdcrag.export;

import java.util.List;

/** Modèle intermédiaire partagé par DocxExporter et PdfExporter : construit
 * une seule fois à partir du CDC et de ses sections (voir ExportService),
 * pour que la page de garde/sommaire/pagination restent identiques entre
 * les deux formats plutôt que dupliquées dans chaque renderer. */
public record ExportDocument(String title, String cdcType, String reference, String date, String statut,
                             List<ExportSection> sections, Branding branding) {

    public record ExportSection(int ordre, String titre, List<MarkdownBlocks.Block> blocks) {}

    /** Contenu de marque (organisation_settings) : à distinguer de FormatStyle,
     * qui pilote l'APPARENCE (polices, couleurs, marges) partagée par les deux
     * exporteurs -- voir OrganisationSettings. Tous les champs sont nullable
     * (y compris logo) : un export ne doit jamais échouer faute de paramètres
     * renseignés, chaque exporteur omet silencieusement ce qui est absent. */
    public record Branding(String nomOrganisation, String direction, byte[] logo, String logoContentType,
                           String mentionConfidentialite, String piedDePage, String contactService,
                           String contactAdresse, String contactCourriel) {

        public static Branding empty() {
            return new Branding(null, null, null, null, null, null, null, null, null);
        }

        public boolean hasLogo() { return logo != null && logo.length > 0; }

        public boolean hasContact() { return contactService != null || contactAdresse != null || contactCourriel != null; }
    }
}
