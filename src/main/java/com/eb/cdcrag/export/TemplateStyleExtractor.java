package com.eb.cdcrag.export;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTColor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHpsMeasure;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Lit les styles d'un .docx fourni par le client : polices, styles de
 * titres (Heading1/Heading2 -- l'id de style Word, stable quelle que soit
 * la langue de l'interface, contrairement au nom affiché "Titre 1"),
 * marges, couleurs. Best-effort délibéré : un champ non détecté (le client
 * n'a jamais utilisé les styles Heading natifs, par exemple) retombe sur la
 * valeur par défaut de ce champ précis plutôt que de faire échouer tout
 * l'import -- cohérent avec la limite documentée à l'utilisateur (page
 * /mise-en-forme) : les styles sont repris, pas la mise en page complète
 * d'une page de garde arbitraire. */
public final class TemplateStyleExtractor {

    private TemplateStyleExtractor() {}

    public static FormatStyle extract(byte[] docxBytes) {
        FormatStyle defaults = FormatStyle.defaultStyle();
        try (InputStream in = new ByteArrayInputStream(docxBytes); XWPFDocument document = new XWPFDocument(in)) {
            XWPFStyles styles = document.getStyles();

            int bodyFontSizePt = defaults.bodyFontSizePt();
            if (styles != null && styles.getDefaultRunStyle() != null && styles.getDefaultRunStyle().getFontSize() > 0) {
                bodyFontSizePt = styles.getDefaultRunStyle().getFontSize();
            }

            HeadingStyle h1 = readHeadingStyle(styles, "Heading1", defaults.heading1Color(),
                    defaults.heading1FontSizePt(), defaults.heading1Bold());
            HeadingStyle h2 = readHeadingStyle(styles, "Heading2", defaults.heading2Color(),
                    defaults.heading2FontSizePt(), defaults.heading2Bold());
            // XWPFDefaultRunStyle n'expose pas sa police publiquement (getRPr()
            // protégé côté POI) : Heading1 sert de proxy raisonnable pour "la
            // police principale du document" -- la plupart des modèles Word
            // définissent au moins leurs titres avec une police explicite.
            String fontFamily = h1.fontFamily() != null ? h1.fontFamily() : defaults.fontFamily();

            int marginTop = defaults.marginTopTwips();
            int marginBottom = defaults.marginBottomTwips();
            int marginLeft = defaults.marginLeftTwips();
            int marginRight = defaults.marginRightTwips();
            if (document.getDocument().getBody().isSetSectPr()) {
                var sectPr = document.getDocument().getBody().getSectPr();
                if (sectPr.isSetPgMar()) {
                    CTPageMar pgMar = sectPr.getPgMar();
                    marginTop = intValue(pgMar.getTop(), marginTop);
                    marginBottom = intValue(pgMar.getBottom(), marginBottom);
                    marginLeft = intValue(pgMar.getLeft(), marginLeft);
                    marginRight = intValue(pgMar.getRight(), marginRight);
                }
            }

            String accentColor = h1.color() != null ? h1.color() : defaults.accentColor();
            String tableHeaderBg = lightTint(accentColor);

            return new FormatStyle(
                    fontFamily, bodyFontSizePt,
                    h1.color() != null ? h1.color() : defaults.heading1Color(), h1.sizePt(), h1.bold(),
                    h2.color() != null ? h2.color() : defaults.heading2Color(), h2.sizePt(), h2.bold(),
                    accentColor, tableHeaderBg,
                    marginTop, marginBottom, marginLeft, marginRight);
        } catch (IOException | RuntimeException e) {
            // Un .docx illisible ou inattendu ne doit pas empêcher l'upload :
            // FormatTemplateService signale l'échec, l'appelant décide (voir
            // FormatTemplateController) -- ici on remonte l'erreur telle quelle.
            throw new IllegalArgumentException("Impossible de lire les styles de ce document : " + e.getMessage(), e);
        }
    }

    private record HeadingStyle(String color, int sizePt, boolean bold, String fontFamily) {}

    private static HeadingStyle readHeadingStyle(XWPFStyles styles, String styleId,
                                                 String fallbackColor, int fallbackSize, boolean fallbackBold) {
        if (styles == null || !styles.styleExist(styleId)) {
            return new HeadingStyle(fallbackColor, fallbackSize, fallbackBold, null);
        }
        XWPFStyle style = styles.getStyle(styleId);
        CTRPr rPr = style.getCTStyle().isSetRPr() ? style.getCTStyle().getRPr() : null;
        if (rPr == null) return new HeadingStyle(fallbackColor, fallbackSize, fallbackBold, null);

        String color = fallbackColor;
        if (!rPr.getColorList().isEmpty()) {
            String hex = hexColorOf(rPr.getColorList().get(0));
            if (hex != null) color = hex;
        }

        int sizePt = fallbackSize;
        if (!rPr.getSzList().isEmpty()) {
            CTHpsMeasure sz = rPr.getSzList().get(0);
            sizePt = intValue(sz.getVal(), fallbackSize * 2) / 2;
        }

        String fontFamily = fontOf(rPr);

        boolean bold = fallbackBold;
        if (!rPr.getBList().isEmpty()) {
            bold = onOffTrue(rPr.getBList().get(0));
        }

        return new HeadingStyle(color, sizePt, bold, fontFamily);
    }

    /** ST_HexColor est un type union : XMLBeans renvoie un byte[3] (RGB) pour
     * une vraie couleur hex, mais une String pour le mot-clé "auto". Un
     * getVal().toString() naïf sur le cas byte[] donne "[B@..." au lieu de
     * la couleur -- piège rencontré en testant l'extraction sur un docx
     * construit avec une couleur Heading1 connue. */
    private static String hexColorOf(CTColor c) {
        Object val = c.getVal();
        if (val instanceof byte[] rgb && rgb.length == 3) {
            return "%02X%02X%02X".formatted(rgb[0] & 0xFF, rgb[1] & 0xFF, rgb[2] & 0xFF);
        }
        String s = String.valueOf(val);
        return "auto".equalsIgnoreCase(s) ? null : s.toUpperCase();
    }

    private static String fontOf(CTRPr rPr) {
        if (rPr == null || rPr.getRFontsList().isEmpty()) return null;
        String ascii = rPr.getRFontsList().get(0).getAscii();
        return (ascii == null || ascii.isBlank()) ? null : ascii;
    }

    private static int intValue(Object union, int fallback) {
        if (union == null) return fallback;
        try {
            return Integer.parseInt(union.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** OOXML : un élément on/off présent sans @w:val vaut "vrai" ; sinon la
     * valeur peut être "true"/"false"/"1"/"0"/"on"/"off". */
    private static boolean onOffTrue(CTOnOff onOff) {
        if (!onOff.isSetVal()) return true;
        String v = String.valueOf(onOff.getVal()).trim().toLowerCase();
        return v.equals("true") || v.equals("1") || v.equals("on");
    }

    /** Mélange la couleur d'accent avec du blanc (~85 %) pour un fond d'en-tête
     * de tableau lisible : le modèle client ne définit pas de "style de
     * tableau" que POI puisse relire de façon fiable, ce ton est donc dérivé
     * plutôt que copié. */
    private static String lightTint(String hex) {
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            int tr = (int) (r + (255 - r) * 0.85);
            int tg = (int) (g + (255 - g) * 0.85);
            int tb = (int) (b + (255 - b) * 0.85);
            return "%02X%02X%02X".formatted(tr, tg, tb);
        } catch (Exception e) {
            return "E7EBF3";
        }
    }
}
