package com.eb.cdcrag.export;

/** Habillage visuel d'un export (DOCX et PDF) : source de vérité unique
 * consommée par les deux renderers -- DocxExporter l'applique via les API
 * POI, PdfExporter en dérive la feuille de style CSS du HTML intermédiaire.
 * Soit la valeur par défaut ({@link #defaultStyle()}), soit extraite d'un
 * modèle de mise en forme fourni par le client (voir TemplateStyleExtractor)
 * -- dans les deux cas, les exporters ne connaissent que ce record, jamais
 * l'origine des valeurs. Les couleurs sont des hex sans "#" (ex. "1F3864"). */
public record FormatStyle(
        String fontFamily,
        int bodyFontSizePt,
        String heading1Color,
        int heading1FontSizePt,
        boolean heading1Bold,
        String heading2Color,
        int heading2FontSizePt,
        boolean heading2Bold,
        String accentColor,
        String tableHeaderBgColor,
        int marginTopTwips,
        int marginBottomTwips,
        int marginLeftTwips,
        int marginRightTwips) {

    private static final String DEFAULT_ACCENT = "2F4B7C";

    public static FormatStyle defaultStyle() {
        return new FormatStyle(
                "Calibri", 11,
                DEFAULT_ACCENT, 20, true,
                DEFAULT_ACCENT, 14, true,
                DEFAULT_ACCENT, "E7EBF3",
                1440, 1440, 1440, 1440);
    }

    /** twips (1/1440 pouce) -> points, pour la CSS de PdfExporter. */
    public double marginTopPt() { return marginTopTwips / 20.0; }
    public double marginBottomPt() { return marginBottomTwips / 20.0; }
    public double marginLeftPt() { return marginLeftTwips / 20.0; }
    public double marginRightPt() { return marginRightTwips / 20.0; }
}
