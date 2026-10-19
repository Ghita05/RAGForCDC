package com.eb.cdcrag.export;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parseur markdown volontairement minimal : pas de dépendance externe
 * (CommonMark, flexmark...), seulement ce que produit réellement le LLM
 * pour un corps de section (voir CdcSectionGenerationWorker.buildPrompt) --
 * titres, paragraphes, listes à puces/numérotées, tableaux GFM (`| a | b |`
 * avec une ligne de séparation `|---|---|`). Même logique que
 * SectionDetector / ClauseExtractor : un petit parseur dédié plutôt qu'une
 * bibliothèque générale pour un format d'entrée qu'on contrôle par le
 * prompt. */
public final class MarkdownBlocks {

    private MarkdownBlocks() {}

    private static final Pattern TABLE_SEPARATOR =
            Pattern.compile("^\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?$");
    private static final Pattern ORDERED_MARKER = Pattern.compile("^\\d+[.)]\\s+");
    private static final Pattern BULLET_MARKER = Pattern.compile("^[-*]\\s+");
    private static final Pattern LIST_MARKER = Pattern.compile("^(-|\\*|\\d+[.)])\\s+");
    private static final Pattern HEADING_MARKER = Pattern.compile("^(#{1,6})\\s+(.*)$");

    public sealed interface Block permits Heading, Paragraph, ListBlock, Table {}
    /** Titre au sein du corps d'une section (ex. le LLM ou une édition
     * manuelle ajoute un "## Sous-partie") -- distinct du titre de section
     * lui-même, rendu séparément par les exporters (Titre 1). level est le
     * nombre de `#` d'origine, conservé pour rester général même si les
     * deux exporters ne distinguent aujourd'hui qu'un seul niveau
     * (Titre 2, voir DocxExporter/PdfExporter). */
    public record Heading(int level, String text) implements Block {}
    public record Paragraph(List<String> lines) implements Block {}
    public record ListBlock(boolean ordered, List<String> items) implements Block {}
    public record Table(List<String> headers, List<List<String>> rows) implements Block {}

    private enum Kind { TABLE, LIST, HEADING, PARAGRAPH }

    /** Segmente d'abord sur les lignes vides, PUIS, à l'intérieur d'un même
     * paragraphe source, sur un changement de nature de ligne (texte ->
     * puce, puce -> tableau...) : le LLM enchaîne régulièrement une phrase
     * d'intro et une liste à puces SANS ligne vide entre les deux ("Sont
     * inclus : \n- item\n- item"), et une règle "toutes les lignes du bloc
     * doivent être des puces" classerait tout le bloc en Paragraph, perdant
     * la mise en forme de la liste. */
    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) return blocks;

        String[] rawLines = markdown.replace("\r\n", "\n").split("\n");
        List<String> current = new ArrayList<>();
        Kind currentKind = null;

        for (String rawLine : rawLines) {
            if (rawLine.isBlank()) {
                flush(blocks, current, currentKind);
                current.clear();
                currentKind = null;
                continue;
            }
            // La ligne de séparation d'un tableau ("|---|---|") commence
            // aussi par "|" donc kindOf() la classe déjà TABLE comme la
            // ligne d'en-tête -- pas de cas particulier nécessaire ici.
            Kind kind = kindOf(rawLine);
            if (currentKind != null && kind != currentKind) {
                flush(blocks, current, currentKind);
                current.clear();
            }
            current.add(rawLine);
            currentKind = kind;
        }
        flush(blocks, current, currentKind);
        return blocks;
    }

    private static Kind kindOf(String line) {
        String stripped = line.strip();
        if (stripped.startsWith("|")) return Kind.TABLE;
        if (HEADING_MARKER.matcher(stripped).matches()) return Kind.HEADING;
        if (LIST_MARKER.matcher(stripped).find()) return Kind.LIST;
        return Kind.PARAGRAPH;
    }

    private static void flush(List<Block> blocks, List<String> lines, Kind kind) {
        if (lines.isEmpty() || kind == null) return;

        if (kind == Kind.TABLE) {
            if (lines.size() >= 2 && isTableRow(lines.get(0)) && TABLE_SEPARATOR.matcher(lines.get(1).strip()).matches()) {
                List<String> headers = splitRow(lines.get(0));
                List<List<String>> rows = new ArrayList<>();
                for (int i = 2; i < lines.size(); i++) {
                    if (isTableRow(lines.get(i))) rows.add(splitRow(lines.get(i)));
                }
                blocks.add(new Table(headers, rows));
                return;
            }
            // Ligne(s) commençant par "|" mais sans ligne de séparation valide
            // juste après : pas une vraie table GFM (ex. un "|" isolé dans une
            // phrase) -- traité comme paragraphe plutôt que silencieusement perdu.
            blocks.add(new Paragraph(List.copyOf(lines)));
            return;
        }

        if (kind == Kind.HEADING) {
            // Une ligne de titre = un bloc Heading à elle seule : deux "#"
            // consécutifs sans ligne vide entre eux (rare, mais possible sur
            // une édition manuelle) restent deux titres distincts, pas fondus.
            for (String line : lines) {
                var m = HEADING_MARKER.matcher(line.strip());
                if (m.matches()) blocks.add(new Heading(m.group(1).length(), m.group(2).strip()));
            }
            return;
        }

        if (kind == Kind.LIST) {
            boolean ordered = ORDERED_MARKER.matcher(lines.get(0).strip()).find();
            Pattern marker = ordered ? ORDERED_MARKER : BULLET_MARKER;
            List<String> items = lines.stream()
                    .map(l -> marker.matcher(l.strip()).replaceFirst(""))
                    .toList();
            blocks.add(new ListBlock(ordered, items));
            return;
        }

        blocks.add(new Paragraph(List.copyOf(lines)));
    }

    private static boolean isTableRow(String line) {
        return line.strip().startsWith("|");
    }

    private static List<String> splitRow(String line) {
        String trimmed = line.strip();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return List.of(trimmed.split("\\|")).stream().map(String::strip).toList();
    }
}
