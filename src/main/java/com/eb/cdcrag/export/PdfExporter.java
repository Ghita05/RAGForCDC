package com.eb.cdcrag.export;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.PDFCreationListener;
import org.xhtmlrenderer.pdf.PagePosition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Rendu PDF (Flying Saucer sur OpenPDF, nouvelle dépendance -- PDFBox reste
 * réservé à la lecture des PDF à l'ingestion). Le markdown est converti en
 * HTML une fois (voir buildHtml), avec une feuille de style dérivée de
 * FormatStyle : même source de vérité que DocxExporter, pas de CSS dupliquée
 * à la main. En-tête/pied de page (titre, numéro de page) dessinés via un
 * PdfPageEventHelper -- Flying Saucer ne supporte pas les en-têtes/pieds de
 * page courants de CSS Paged Media (@top-center etc.), ce mécanisme bas
 * niveau est la façon standard de les obtenir avec ce moteur.
 *
 * Un sommaire avec numéros de page réels demande de savoir, pour un contenu
 * donné, sur quelle page il atterrit -- impossible à l'avance sans mise en
 * page. Deux passes : la première pose une balise id="sec-N" sur chaque
 * titre de section et un marqueur id="doc-end" en fin de document, puis
 * interroge ITextRenderer.findPagePositionsByID (sans écrire de PDF) pour
 * connaître la page de chacun ; la seconde reconstruit le même document
 * avec le sommaire rempli des vrais numéros et l'écrit pour de bon. Les deux
 * passes ont la même pagination (le sommaire réserve une largeur de colonne
 * fixe pour les numéros, son nombre de pages ne change donc pas entre les
 * deux). */
public final class PdfExporter {

    private PdfExporter() {}

    private static final Pattern SECTION_OR_END = Pattern.compile("sec-\\d+|doc-end");

    public static byte[] export(ExportDocument doc, FormatStyle style) {
        try {
            Map<Integer, Integer> pageBySection = new HashMap<>();
            int totalPages = probePagination(doc, style, pageBySection);
            String finalHtml = buildHtml(doc, style, pageBySection);
            return renderFinal(finalHtml, doc.title(), style, totalPages, doc.branding().piedDePage());
        } catch (DocumentException e) {
            throw new IllegalStateException("Échec de l'export PDF", e);
        }
    }

    /** Première passe : layout seul (pas d'écriture de PDF), pour connaître
     * la page de chaque section et le nombre total de pages. */
    private static int probePagination(ExportDocument doc, FormatStyle style, Map<Integer, Integer> pageBySection) {
        String probeHtml = buildHtml(doc, style, Map.of());
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(probeHtml);
        renderer.layout();
        renderer.getOutputDevice().setRoot(renderer.getRootBox());
        renderer.getOutputDevice().start(renderer.getDocument());

        List<PagePosition> positions = renderer.findPagePositionsByID(SECTION_OR_END);
        int totalPages = 1;
        for (PagePosition p : positions) {
            if ("doc-end".equals(p.getId())) {
                totalPages = p.getPageNo() + 1;
            } else {
                int ordre = Integer.parseInt(p.getId().substring("sec-".length()));
                pageBySection.put(ordre, p.getPageNo() + 1);
            }
        }
        return totalPages;
    }

    private static byte[] renderFinal(String html, String docTitle, FormatStyle style, int totalPages, String piedDePage)
            throws DocumentException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(html);
            renderer.layout();
            renderer.setListener(new HeaderFooterListener(docTitle, style, totalPages, piedDePage));
            renderer.createPDF(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Dessine le titre du document en en-tête et, en pied de page, le texte
     * configuré (organisation_settings.pied_de_page) combiné au numéro de
     * page -- même contenu que DocxExporter. */
    private static final class HeaderFooterListener implements PDFCreationListener {
        private final String docTitle;
        private final FormatStyle style;
        private final int totalPages;
        private final String piedDePage;

        HeaderFooterListener(String docTitle, FormatStyle style, int totalPages, String piedDePage) {
            this.docTitle = docTitle;
            this.style = style;
            this.totalPages = totalPages;
            this.piedDePage = piedDePage;
        }

        @Override
        public void preOpen(ITextRenderer renderer) {
            PdfWriter writer = renderer.getWriter();
            BaseFont font;
            try {
                font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override
                public void onEndPage(PdfWriter writer, Document pdfDoc) {
                    PdfContentByte cb = writer.getDirectContent();
                    Rectangle page = pdfDoc.getPageSize();

                    cb.beginText();
                    cb.setFontAndSize(font, 8);
                    cb.setColorFill(hexColor(style.accentColor()));
                    cb.showTextAligned(Element.ALIGN_LEFT, safe(font, docTitle),
                            page.getLeft() + pointsFromTwips(style.marginLeftTwips()),
                            page.getTop() - 24, 0);
                    cb.setColorFill(java.awt.Color.BLACK);
                    cb.endText();

                    String pageLabel = "Page " + writer.getPageNumber() + " / " + totalPages;
                    String footerText = (piedDePage != null && !piedDePage.isBlank())
                            ? piedDePage + "  —  " + pageLabel : pageLabel;
                    cb.beginText();
                    cb.setFontAndSize(font, 9);
                    cb.showTextAligned(Element.ALIGN_CENTER, safe(font, footerText),
                            (page.getLeft() + page.getRight()) / 2, page.getBottom() + 24, 0);
                    cb.endText();
                }
            });
        }

        @Override
        public void preWrite(ITextRenderer renderer, int pageCount) {}

        @Override
        public void onClose(ITextRenderer renderer) {}
    }

    private static float pointsFromTwips(int twips) {
        return twips / 20f;
    }

    private static java.awt.Color hexColor(String hex) {
        return new java.awt.Color(Integer.parseInt(hex, 16));
    }

    /** Même filet de sécurité que l'ancien PdfExporter basé sur PDFBox : les
     * polices standard n'encodent pas forcément tout Unicode. */
    private static String safe(BaseFont font, String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(font.charExists(c) ? c : '?');
        }
        return sb.toString();
    }

    // -- Construction du HTML -----------------------------------------------

    private static String buildHtml(ExportDocument doc, FormatStyle style, Map<Integer, Integer> pageBySection) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><meta charset=\"UTF-8\"/><style>").append(css(style)).append("</style></head><body>");

        ExportDocument.Branding brand = doc.branding();
        html.append("<div class=\"cover\">");
        html.append("<div class=\"cover-top\">");
        if (brand.hasLogo()) {
            String base64 = Base64.getEncoder().encodeToString(brand.logo());
            html.append("<img class=\"logo\" src=\"data:").append(brand.logoContentType()).append(";base64,")
                    .append(base64).append("\" alt=\"logo\"/>");
        } else {
            html.append("<div class=\"logo-placeholder\">Emplacement du logo</div>");
        }
        if (hasText(brand.nomOrganisation())) {
            html.append("<p class=\"org-name\">").append(esc(brand.nomOrganisation())).append("</p>");
        }
        if (hasText(brand.direction())) {
            html.append("<p class=\"org-direction\">").append(esc(brand.direction())).append("</p>");
        }
        html.append("<h1 class=\"cover-title\">").append(esc(doc.title())).append("</h1>");
        html.append("<p>Référence : ").append(esc(doc.reference())).append("</p>");
        html.append("<p>Type : ").append(esc(doc.cdcType() == null ? "—" : doc.cdcType())).append("</p>");
        html.append("<p>Date : ").append(esc(doc.date())).append("</p>");
        html.append("<p>Statut : ").append(esc(doc.statut())).append("</p>");
        if (brand.hasContact()) {
            html.append("<div class=\"contact\">");
            if (hasText(brand.contactService())) html.append("<p>").append(esc(brand.contactService())).append("</p>");
            if (hasText(brand.contactAdresse())) html.append("<p>").append(esc(brand.contactAdresse())).append("</p>");
            if (hasText(brand.contactCourriel())) html.append("<p>").append(esc(brand.contactCourriel())).append("</p>");
            html.append("</div>");
        }
        html.append("</div>");
        if (hasText(brand.mentionConfidentialite())) {
            html.append("<p class=\"confidentialite\">").append(esc(brand.mentionConfidentialite())).append("</p>");
        }
        html.append("</div>");

        html.append("<div class=\"toc-page\">");
        html.append("<h2 class=\"toc-heading\">Sommaire</h2>");
        html.append("<table class=\"toc\">");
        for (ExportDocument.ExportSection s : doc.sections()) {
            Integer page = pageBySection.get(s.ordre());
            html.append("<tr><td>").append(s.ordre()).append(". ").append(esc(s.titre())).append("</td>")
                    .append("<td class=\"toc-page-no\">").append(page == null ? "" : page).append("</td></tr>");
        }
        html.append("</table></div>");

        for (ExportDocument.ExportSection s : doc.sections()) {
            html.append("<div class=\"section\" id=\"sec-").append(s.ordre()).append("\">");
            html.append("<h1 class=\"section-title\">").append(s.ordre()).append(". ").append(esc(s.titre())).append("</h1>");
            for (MarkdownBlocks.Block block : s.blocks()) {
                appendBlock(html, block);
            }
            html.append("</div>");
        }

        html.append("<span id=\"doc-end\"></span>");
        html.append("</body></html>");
        return html.toString();
    }

    private static void appendBlock(StringBuilder html, MarkdownBlocks.Block block) {
        switch (block) {
            case MarkdownBlocks.Heading h -> html.append("<h2 class=\"subheading\">").append(esc(h.text())).append("</h2>");
            case MarkdownBlocks.Paragraph p -> {
                for (String line : p.lines()) {
                    html.append("<p>").append(esc(line)).append("</p>");
                }
            }
            case MarkdownBlocks.ListBlock list -> {
                String tag = list.ordered() ? "ol" : "ul";
                html.append("<").append(tag).append(">");
                for (String item : list.items()) {
                    html.append("<li>").append(esc(item)).append("</li>");
                }
                html.append("</").append(tag).append(">");
            }
            case MarkdownBlocks.Table table -> {
                html.append("<table class=\"content-table\"><thead><tr>");
                for (String header : table.headers()) {
                    html.append("<th>").append(esc(header)).append("</th>");
                }
                html.append("</tr></thead><tbody>");
                for (List<String> row : table.rows()) {
                    html.append("<tr>");
                    for (String cell : row) {
                        html.append("<td>").append(esc(cell)).append("</td>");
                    }
                    html.append("</tr>");
                }
                html.append("</tbody></table>");
            }
        }
    }

    // Hauteur A4 portrait en points (297 mm), constante indépendante du style
    // (seules les marges varient avec FormatStyle) -- même logique que
    // DocxExporter.PAGE_HEIGHT_TWIPS, pour ancrer la mention de
    // confidentialité au bas de la page de garde quel que soit le contenu
    // au-dessus (logo, organisation, contact -- tous optionnels).
    private static final double PAGE_HEIGHT_PT = 841.9;

    private static String css(FormatStyle style) {
        double coverHeightPt = PAGE_HEIGHT_PT - style.marginTopPt() - style.marginBottomPt();
        return """
            @page { size: A4; margin: %.1fpt %.1fpt %.1fpt %.1fpt; }
            body { font-family: "%s", sans-serif; font-size: %dpt; color: #222; margin: 0; }
            .cover { position: relative; height: %.1fpt; text-align: center; }
            .cover-top { padding-top: 90pt; }
            .logo-placeholder { border: 1pt dashed #999; color: #999; font-style: italic; font-size: 9pt;
                width: 220pt; padding: 28pt 0; text-align: center; margin: 0 auto 24pt auto; }
            .logo { max-width: 140pt; max-height: 80pt; margin: 0 auto 20pt auto; display: block; }
            .org-name { font-size: 14pt; font-weight: bold; margin: 0 0 4pt 0; }
            .org-direction { font-size: 11pt; color: #555; margin: 0 0 20pt 0; }
            .cover-title { font-size: 26pt; font-weight: bold; color: #%s; margin: 24pt 0 20pt 0; }
            .cover p { font-size: 12pt; margin: 6pt 0; }
            .contact { margin-top: 24pt; }
            .contact p { font-size: 9pt; color: #666; margin: 2pt 0; }
            .confidentialite { position: absolute; bottom: 0; left: 0; right: 0; text-align: center;
                font-size: 8pt; font-style: italic; color: #777; margin: 0; }
            .toc-page { page-break-before: always; }
            .toc-heading { font-size: 18pt; font-weight: bold; color: #%s; }
            table.toc { width: 100%%; border-collapse: collapse; margin-top: 12pt; }
            table.toc td { padding: 3pt 0; font-size: 11pt; }
            table.toc td.toc-page-no { text-align: right; width: 40pt; }
            .section { page-break-before: always; }
            h1.section-title { font-size: %dpt; font-weight: %s; color: #%s; margin: 0 0 12pt 0; }
            h2.subheading { font-size: %dpt; font-weight: %s; color: #%s; margin: 10pt 0 6pt 0; }
            p { margin: 0 0 8pt 0; line-height: 1.35; }
            ul, ol { margin: 0 0 8pt 20pt; padding: 0; }
            li { margin-bottom: 3pt; line-height: 1.3; }
            table.content-table { width: 100%%; border-collapse: collapse; margin: 8pt 0 12pt 0; }
            table.content-table th { background-color: #%s; border: 0.5pt solid #999; padding: 4pt; text-align: left; font-size: 9pt; }
            table.content-table td { border: 0.5pt solid #999; padding: 4pt; font-size: 9pt; }
            """.formatted(
                style.marginTopPt(), style.marginRightPt(), style.marginBottomPt(), style.marginLeftPt(),
                style.fontFamily(), style.bodyFontSizePt(),
                coverHeightPt,
                style.accentColor(),
                style.accentColor(),
                style.heading1FontSizePt(), style.heading1Bold() ? "bold" : "normal", style.heading1Color(),
                style.heading2FontSizePt(), style.heading2Bold() ? "bold" : "normal", style.heading2Color(),
                style.tableHeaderBgColor());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
