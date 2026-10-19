package com.eb.cdcrag.export;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gabarit de référence de document (organisation_settings.format_reference,
 * ex. "CDC-{annee}-{numero:000}_{type}"). Variables reconnues :
 * - {annee} : année courante (export, pas création du CDC).
 * - {numero} ou {numero:000} (le nombre de zéros fixe la largeur du
 *   remplissage) : l'id du CDC en base -- pas un compteur remis à zéro
 *   chaque année, plus simple et toujours unique sans table de séquence
 *   dédiée. Documenté ainsi dans l'interface (page /parametres).
 * - {type} : le type du CDC (texte libre), vide si non renseigné.
 * Une variable inconnue ou une syntaxe invalide reste telle quelle dans le
 * résultat plutôt que de faire échouer l'export -- un gabarit mal formé ne
 * doit jamais empêcher de récupérer son document. */
public final class ReferenceTemplate {

    private ReferenceTemplate() {}

    private static final Pattern VARIABLE = Pattern.compile("\\{(annee|numero|type)(:0+)?}");

    public static String render(String template, int annee, long numero, String type) {
        if (template == null || template.isBlank()) template = "CDC-{numero}";
        Matcher m = VARIABLE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String variable = m.group(1);
            String padding = m.group(2);
            String value = switch (variable) {
                case "annee" -> String.valueOf(annee);
                case "numero" -> padding != null ? padNumero(numero, padding.length() - 1) : String.valueOf(numero);
                case "type" -> type == null ? "" : type;
                default -> m.group();
            };
            m.appendReplacement(out, Matcher.quoteReplacement(value));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String padNumero(long numero, int width) {
        return String.format("%0" + width + "d", numero);
    }
}
