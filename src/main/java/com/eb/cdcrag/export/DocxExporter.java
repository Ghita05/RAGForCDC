package com.eb.cdcrag.export;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xwpf.usermodel.BreakType;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.TableRowHeightRule;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFNumbering;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.poi.xwpf.model.XWPFHeaderFooterPolicy;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTLvl;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import org.apache.poi.common.usermodel.PictureType;
import org.apache.poi.util.Units;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.List;

/** Rendu DOCX (Apache POI) : page de garde (avec emplacement de logo),
 * sommaire à champ TOC réel (numéros de page calculés par Word à
 * l'ouverture), sections numérotées avec hiérarchie de titres (Titre 1 =
 * section, Titre 2 = sous-partie), listes à puces/numérotées natives Word,
 * tableaux avec en-tête ombré et bordures, en-tête/pied de page avec numéro
 * de page. Habillage (polices, couleurs, marges) piloté par FormatStyle --
 * valeur par défaut ou extraite d'un modèle de mise en forme client (voir
 * TemplateStyleExtractor), le rendu ne connaît que ce record. */
public final class DocxExporter {

    private DocxExporter() {}

    public static byte[] export(ExportDocument doc, FormatStyle style) {
        try (XWPFDocument docx = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            setPageMargins(docx, style);
            BigInteger bulletNumId = createBulletNumbering(docx);
            BigInteger decimalNumId = createDecimalNumbering(docx);
            writeHeaderFooter(docx, doc, style);
            writeCoverPage(docx, doc, style);
            writeSommaire(docx, doc, style);
            writeSections(docx, doc, style, bulletNumId, decimalNumId);
            // Un champ TOC reste vide tant qu'il n'a pas été "mis à jour" --
            // ce réglage demande à Word de le faire automatiquement à
            // l'ouverture, sans action de l'utilisateur.
            docx.getSettings().setUpdateFields();
            docx.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void setPageMargins(XWPFDocument docx, FormatStyle style) {
        CTSectPr sectPr = docx.getDocument().getBody().isSetSectPr()
                ? docx.getDocument().getBody().getSectPr()
                : docx.getDocument().getBody().addNewSectPr();
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pageMar.setTop(BigInteger.valueOf(style.marginTopTwips()));
        pageMar.setBottom(BigInteger.valueOf(style.marginBottomTwips()));
        pageMar.setLeft(BigInteger.valueOf(style.marginLeftTwips()));
        pageMar.setRight(BigInteger.valueOf(style.marginRightTwips()));
    }

    // -- En-tête / pied de page ------------------------------------------

    private static void writeHeaderFooter(XWPFDocument docx, ExportDocument doc, FormatStyle style) {
        XWPFHeaderFooterPolicy policy = docx.createHeaderFooterPolicy();

        XWPFHeader header = policy.createHeader(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph headerPara = header.getParagraphArray(0) != null ? header.getParagraphArray(0) : header.createParagraph();
        headerPara.setBorderBottom(Borders.SINGLE);
        XWPFRun headerRun = headerPara.createRun();
        headerRun.setText(doc.title());
        headerRun.setFontFamily(style.fontFamily());
        headerRun.setFontSize(9);
        headerRun.setColor(style.accentColor());

        XWPFFooter footer = policy.createFooter(XWPFHeaderFooterPolicy.DEFAULT);
        XWPFParagraph footerPara = footer.getParagraphArray(0) != null ? footer.getParagraphArray(0) : footer.createParagraph();
        footerPara.setAlignment(ParagraphAlignment.CENTER);
        String piedDePage = doc.branding().piedDePage();
        if (piedDePage != null && !piedDePage.isBlank()) {
            XWPFRun brandingRun = footerPara.createRun();
            brandingRun.setFontFamily(style.fontFamily());
            brandingRun.setFontSize(9);
            brandingRun.setText(piedDePage + "  —  ");
        }
        XWPFRun footerRun = footerPara.createRun();
        footerRun.setFontFamily(style.fontFamily());
        footerRun.setFontSize(9);
        footerRun.setText("Page ");
        addFieldRun(footerPara, "PAGE");
        footerRun = footerPara.createRun();
        footerRun.setFontFamily(style.fontFamily());
        footerRun.setFontSize(9);
        footerRun.setText(" / ");
        addFieldRun(footerPara, "NUMPAGES");
    }

    // -- Page de garde -----------------------------------------------------

    // Hauteur A4 portrait en twips (297 mm), constante indépendante du style
    // (seules les marges varient avec FormatStyle).
    private static final int PAGE_HEIGHT_TWIPS = 16838;
    private static final int CONFIDENTIALITE_ROW_HEIGHT_TWIPS = 900;

    /** Page de garde construite dans un tableau invisible à deux lignes
     * (bordures retirées) plutôt qu'en paragraphes de niveau document : la
     * seule façon fiable dans Word de plaquer la mention de confidentialité
     * en bas de page quel que soit le contenu au-dessus (logo, nom
     * d'organisation, contact -- tous optionnels, donc de hauteur variable).
     * La première ligne a une hauteur MINIMALE (AT_LEAST) calculée pour
     * occuper tout l'espace utile sauf la bande réservée à la mention ; Word
     * la remplit de blanc si le contenu est plus court. */
    private static void writeCoverPage(XWPFDocument docx, ExportDocument doc, FormatStyle style) {
        ExportDocument.Branding brand = doc.branding();
        int usableHeight = PAGE_HEIGHT_TWIPS - style.marginTopTwips() - style.marginBottomTwips();
        int topRowHeight = Math.max(usableHeight - CONFIDENTIALITE_ROW_HEIGHT_TWIPS, 1000);

        XWPFTable table = docx.createTable(2, 1);
        table.setWidth("100%");
        table.removeBorders();

        XWPFTableRow topRow = table.getRow(0);
        topRow.setHeight(topRowHeight);
        topRow.setHeightRule(TableRowHeightRule.AT_LEAST);
        XWPFTableCell topCell = topRow.getCell(0);
        topCell.removeParagraph(0);

        writeLogo(topCell, brand, style);
        if (hasText(brand.nomOrganisation())) {
            addCenteredLine(topCell, style, brand.nomOrganisation(), true, 14, 100);
        }
        if (hasText(brand.direction())) {
            addCenteredLine(topCell, style, brand.direction(), false, 11, 40);
        }

        XWPFParagraph title = topCell.addParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        title.setSpacingBefore(600);
        XWPFRun titleRun = title.createRun();
        titleRun.setText(doc.title());
        titleRun.setBold(true);
        titleRun.setFontSize(28);
        titleRun.setFontFamily(style.fontFamily());
        titleRun.setColor(style.accentColor());

        addCoverLine(topCell, style, "Référence", doc.reference());
        addCoverLine(topCell, style, "Type", doc.cdcType() == null ? "—" : doc.cdcType());
        addCoverLine(topCell, style, "Date", doc.date());
        addCoverLine(topCell, style, "Statut", doc.statut());

        if (brand.hasContact()) {
            if (hasText(brand.contactService())) addCenteredLine(topCell, style, brand.contactService(), false, 9, 200);
            if (hasText(brand.contactAdresse())) addCenteredLine(topCell, style, brand.contactAdresse(), false, 9, 40);
            if (hasText(brand.contactCourriel())) addCenteredLine(topCell, style, brand.contactCourriel(), false, 9, 40);
        }

        XWPFTableRow bottomRow = table.getRow(1);
        bottomRow.setHeight(CONFIDENTIALITE_ROW_HEIGHT_TWIPS);
        bottomRow.setHeightRule(TableRowHeightRule.EXACT);
        XWPFTableCell bottomCell = bottomRow.getCell(0);
        bottomCell.setVerticalAlignment(XWPFTableCell.XWPFVertAlign.BOTTOM);
        bottomCell.removeParagraph(0);
        XWPFParagraph mentionPara = bottomCell.addParagraph();
        if (hasText(brand.mentionConfidentialite())) {
            mentionPara.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun mentionRun = mentionPara.createRun();
            mentionRun.setText(brand.mentionConfidentialite());
            mentionRun.setFontFamily(style.fontFamily());
            mentionRun.setFontSize(8);
            mentionRun.setColor("777777");
            mentionRun.setItalic(true);
        }

        docx.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /** Logo réel embarqué (redimensionné en conservant les proportions, sans
     * jamais agrandir un petit fichier) si configuré ; sinon un cadre en
     * pointillés marque l'emplacement, pour que la page de garde ne soit
     * jamais silencieusement incomplète. */
    private static void writeLogo(XWPFTableCell cell, ExportDocument.Branding brand, FormatStyle style) {
        if (brand.hasLogo()) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(brand.logo()));
                if (image != null) {
                    double maxWidthPt = 140, maxHeightPt = 80;
                    double scale = Math.min(1.0, Math.min(maxWidthPt / image.getWidth(), maxHeightPt / image.getHeight()));
                    int widthEmu = Units.toEMU(image.getWidth() * scale);
                    int heightEmu = Units.toEMU(image.getHeight() * scale);
                    PictureType type = "image/png".equals(brand.logoContentType()) ? PictureType.PNG : PictureType.JPEG;
                    XWPFParagraph p = cell.addParagraph();
                    p.setAlignment(ParagraphAlignment.CENTER);
                    p.setSpacingAfter(200);
                    p.createRun().addPicture(new ByteArrayInputStream(brand.logo()), type, "logo", widthEmu, heightEmu);
                    return;
                }
            } catch (IOException | InvalidFormatException e) {
                // Logo illisible ou corrompu : on retombe sur le placeholder
                // plutôt que de faire échouer tout l'export pour une image.
            }
        }
        writeLogoPlaceholder(cell, style);
    }

    private static void writeLogoPlaceholder(XWPFTableCell cell, FormatStyle style) {
        XWPFParagraph box = cell.addParagraph();
        box.setAlignment(ParagraphAlignment.CENTER);
        box.setSpacingBefore(200);
        box.setSpacingAfter(400);
        box.setBorderTop(Borders.DASHED);
        box.setBorderBottom(Borders.DASHED);
        box.setBorderLeft(Borders.DASHED);
        box.setBorderRight(Borders.DASHED);
        XWPFRun run = box.createRun();
        run.setText("Emplacement du logo");
        run.setFontFamily(style.fontFamily());
        run.setFontSize(9);
        run.setColor("999999");
        run.setItalic(true);
        // Trois lignes vides pour donner un peu de hauteur au cadre.
        run.addBreak();
        cell.addParagraph().createRun().addBreak();
    }

    private static void addCenteredLine(XWPFTableCell cell, FormatStyle style, String text, boolean bold,
                                        int fontSize, int spacingBefore) {
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(spacingBefore);
        XWPFRun run = p.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(style.fontFamily());
        run.setFontSize(fontSize);
    }

    private static void addCoverLine(XWPFTableCell cell, FormatStyle style, String label, String value) {
        XWPFParagraph p = cell.addParagraph();
        p.setAlignment(ParagraphAlignment.CENTER);
        p.setSpacingBefore(150);
        XWPFRun run = p.createRun();
        run.setText(label + " : " + value);
        run.setFontFamily(style.fontFamily());
        run.setFontSize(12);
    }

    // -- Sommaire ------------------------------------------------------------

    private static void writeSommaire(XWPFDocument docx, ExportDocument doc, FormatStyle style) {
        XWPFParagraph heading = docx.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setText("Sommaire");
        headingRun.setBold(true);
        headingRun.setFontSize(18);
        headingRun.setFontFamily(style.fontFamily());
        headingRun.setColor(style.accentColor());

        XWPFParagraph tocParagraph = docx.createParagraph();
        insertTocField(tocParagraph, "Mettre à jour le sommaire (clic droit -> Mettre à jour les champs).");

        docx.createParagraph().createRun().addBreak(BreakType.PAGE);
    }

    /** Champ TOC natif Word (\\o "1-2" : niveaux Titre 1 et Titre 2, \\h liens
     * cliquables, \\z masque les numéros en mode web, \\u utilise les niveaux
     * de plan) plutôt qu'une liste "N. Titre" statique : Word calcule les
     * vrais numéros de page à l'ouverture (voir setUpdateFields). Le texte
     * de repli ne s'affiche que si le champ n'a jamais été mis à jour. */
    private static void insertTocField(XWPFParagraph paragraph, String fallbackText) {
        CTR beginR = paragraph.getCTP().addNewR();
        beginR.addNewFldChar().setFldCharType(STFldCharType.BEGIN);

        CTR instrR = paragraph.getCTP().addNewR();
        var instrText = instrR.addNewInstrText();
        instrText.setStringValue(" TOC \\o \"1-2\" \\h \\z \\u ");
        instrText.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);

        CTR sepR = paragraph.getCTP().addNewR();
        sepR.addNewFldChar().setFldCharType(STFldCharType.SEPARATE);

        XWPFRun fallback = paragraph.createRun();
        fallback.setText(fallbackText);
        fallback.setItalic(true);
        fallback.setColor("777777");

        CTR endR = paragraph.getCTP().addNewR();
        endR.addNewFldChar().setFldCharType(STFldCharType.END);
    }

    private static void addFieldRun(XWPFParagraph paragraph, String instruction) {
        CTR beginR = paragraph.getCTP().addNewR();
        beginR.addNewFldChar().setFldCharType(STFldCharType.BEGIN);

        CTR instrR = paragraph.getCTP().addNewR();
        var instrText = instrR.addNewInstrText();
        instrText.setStringValue(" " + instruction + " ");
        instrText.setSpace(org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute.Space.PRESERVE);

        CTR sepR = paragraph.getCTP().addNewR();
        sepR.addNewFldChar().setFldCharType(STFldCharType.SEPARATE);

        CTR endR = paragraph.getCTP().addNewR();
        endR.addNewFldChar().setFldCharType(STFldCharType.END);
    }

    // -- Sections --------------------------------------------------------

    private static void writeSections(XWPFDocument docx, ExportDocument doc, FormatStyle style,
                                      BigInteger bulletNumId, BigInteger decimalNumId) {
        for (ExportDocument.ExportSection s : doc.sections()) {
            XWPFParagraph heading = docx.createParagraph();
            heading.setStyle("Heading1");
            heading.setSpacingBefore(400);
            heading.setSpacingAfter(200);
            XWPFRun headingRun = heading.createRun();
            headingRun.setText(s.ordre() + ". " + s.titre());
            headingRun.setBold(style.heading1Bold());
            headingRun.setFontSize(style.heading1FontSizePt());
            headingRun.setFontFamily(style.fontFamily());
            headingRun.setColor(style.heading1Color());

            for (MarkdownBlocks.Block block : s.blocks()) {
                writeBlock(docx, block, style, bulletNumId, decimalNumId);
            }
        }
    }

    private static void writeBlock(XWPFDocument docx, MarkdownBlocks.Block block, FormatStyle style,
                                   BigInteger bulletNumId, BigInteger decimalNumId) {
        switch (block) {
            case MarkdownBlocks.Heading h -> {
                XWPFParagraph para = docx.createParagraph();
                para.setStyle("Heading2");
                para.setSpacingBefore(200);
                para.setSpacingAfter(100);
                XWPFRun run = para.createRun();
                run.setText(h.text());
                run.setBold(style.heading2Bold());
                run.setFontSize(style.heading2FontSizePt());
                run.setFontFamily(style.fontFamily());
                run.setColor(style.heading2Color());
            }
            case MarkdownBlocks.Paragraph p -> {
                for (String line : p.lines()) {
                    XWPFParagraph para = docx.createParagraph();
                    XWPFRun run = para.createRun();
                    run.setText(line);
                    run.setFontSize(style.bodyFontSizePt());
                    run.setFontFamily(style.fontFamily());
                }
            }
            case MarkdownBlocks.ListBlock list -> {
                BigInteger numId = list.ordered() ? decimalNumId : bulletNumId;
                for (String item : list.items()) {
                    XWPFParagraph para = docx.createParagraph();
                    para.setNumID(numId);
                    XWPFRun run = para.createRun();
                    run.setText(item);
                    run.setFontSize(style.bodyFontSizePt());
                    run.setFontFamily(style.fontFamily());
                }
            }
            case MarkdownBlocks.Table table -> writeTable(docx, table, style);
        }
    }

    // -- Tableaux ----------------------------------------------------------

    private static void writeTable(XWPFDocument docx, MarkdownBlocks.Table table, FormatStyle style) {
        int cols = table.headers().size();
        if (cols == 0) return;
        XWPFTable xwpfTable = docx.createTable(1 + table.rows().size(), cols);
        xwpfTable.setInsideHBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "999999");
        xwpfTable.setInsideVBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "999999");
        xwpfTable.setTopBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "999999");
        xwpfTable.setBottomBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "999999");
        xwpfTable.setLeftBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "999999");
        xwpfTable.setRightBorder(XWPFTable.XWPFBorderType.SINGLE, 4, 0, "999999");

        for (int c = 0; c < cols; c++) {
            XWPFTableCell cell = xwpfTable.getRow(0).getCell(c);
            cell.setColor(style.tableHeaderBgColor());
            writeCell(cell, table.headers().get(c), style, true);
        }
        for (int r = 0; r < table.rows().size(); r++) {
            List<String> row = table.rows().get(r);
            for (int c = 0; c < cols; c++) {
                XWPFTableCell cell = xwpfTable.getRow(r + 1).getCell(c);
                writeCell(cell, c < row.size() ? row.get(c) : "", style, false);
            }
        }
        docx.createParagraph();
    }

    private static void writeCell(XWPFTableCell cell, String text, FormatStyle style, boolean bold) {
        XWPFParagraph para = cell.getParagraphs().get(0);
        XWPFRun run = para.createRun();
        run.setText(text);
        run.setBold(bold);
        run.setFontFamily(style.fontFamily());
        run.setFontSize(Math.max(8, style.bodyFontSizePt() - 1));
    }

    // -- Numérotation des listes -------------------------------------------

    /** Puce Word standard (glyphe  en police Symbol) plutôt qu'un
     * préfixe "• " en texte brut : une vraie liste à puces native, cohérent
     * avec le "correctement rendues" demandé -- notamment retrait pendable
     * et continuité visuelle avec le style de liste de Word. */
    private static BigInteger createBulletNumbering(XWPFDocument docx) {
        XWPFNumbering numbering = docx.createNumbering();
        CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.newInstance();
        ctAbstractNum.setAbstractNumId(BigInteger.valueOf(100));
        CTLvl lvl = ctAbstractNum.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewStart().setVal(BigInteger.ONE);
        lvl.addNewNumFmt().setVal(STNumberFormat.BULLET);
        lvl.addNewLvlText().setVal("");
        lvl.addNewLvlJc().setVal(STJc.LEFT);
        var ind = lvl.addNewPPr().addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));
        var rFonts = lvl.addNewRPr().addNewRFonts();
        rFonts.setAscii("Symbol");
        rFonts.setHAnsi("Symbol");

        XWPFAbstractNum abstractNum = new XWPFAbstractNum(ctAbstractNum);
        BigInteger abstractNumId = numbering.addAbstractNum(abstractNum);
        return numbering.addNum(abstractNumId);
    }

    private static BigInteger createDecimalNumbering(XWPFDocument docx) {
        XWPFNumbering numbering = docx.createNumbering();
        CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.newInstance();
        ctAbstractNum.setAbstractNumId(BigInteger.valueOf(101));
        CTLvl lvl = ctAbstractNum.addNewLvl();
        lvl.setIlvl(BigInteger.ZERO);
        lvl.addNewStart().setVal(BigInteger.ONE);
        lvl.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
        lvl.addNewLvlText().setVal("%1.");
        lvl.addNewLvlJc().setVal(STJc.LEFT);
        var ind = lvl.addNewPPr().addNewInd();
        ind.setLeft(BigInteger.valueOf(720));
        ind.setHanging(BigInteger.valueOf(360));

        XWPFAbstractNum abstractNum = new XWPFAbstractNum(ctAbstractNum);
        BigInteger abstractNumId = numbering.addAbstractNum(abstractNum);
        return numbering.addNum(abstractNumId);
    }
}
