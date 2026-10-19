package com.eb.cdcrag.ingestion;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait les valeurs chiffrées d'un CDC : clauses contractuelles (délai,
 * pénalité, plafond, garantie) et exigences techniques (SLA, GTR).
 *
 * Volontairement déterministe (regex) plutôt que confié au LLM : ces valeurs
 * servent à calculer des normes historiques. Une valeur fausse est pire
 * qu'une valeur absente, donc en cas de doute on renvoie null.
 */
@Component
public class ClauseExtractor {

    private static final Pattern DELAI = Pattern.compile(
            "d[ée]lai[^.]{0,80}?\\(?(\\d{1,3})\\)?\\s*(jours?|mois|semaines?)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PENALITE = Pattern.compile(
            "p[ée]nalit[ée][^.]{0,120}?(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*%",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PLAFOND = Pattern.compile(
            "plafonn?[ée]e?[^.]{0,80}?(\\d{1,2}(?:[.,]\\d{1,2})?)\\s*%",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GARANTIE = Pattern.compile(
            "garanti[ée]?e?[^.]{0,100}?\\(?(\\d{1,3})\\)?\\s*(mois|ans?|an)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SLA = Pattern.compile(
            "(?:disponibilit[ée]|taux de service)[^.]{0,100}?(\\d{2}(?:[.,]\\d{1,2})?)\\s*%",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GTR = Pattern.compile(
            "(?:GTR|temps de r[ée]tablissement)[^.]{0,80}?(\\d{1,3})\\s*heures?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern GTI = Pattern.compile(
            "(?:GTI|temps d['’]intervention)[^.]{0,80}?(\\d{1,3})\\s*heures?",
            Pattern.CASE_INSENSITIVE);

    public Clauses extract(String text) {
        String t = normalize(text);
        Integer gti = gti(t);
        Integer gtr = gtr(t);
        // Garde de plausibilité, même logique que pour la pénalité (<= 5 %) :
        // un GTR ne peut pas être plus court, ni égal, qu'un GTI. Un CDC qui
        // confond les deux libellés (déjà observé : deux lignes de tableau
        // toutes deux étiquetées « Rétablissement ») fausserait sinon
        // silencieusement la médiane SQL, qui devient une norme citée par le
        // relecteur — rien en aval ne peut alors détecter l'erreur.
        if (gti != null && gtr != null && gtr <= gti) {
            gti = null;
            gtr = null;
        }
        return new Clauses(
                delaiEnJours(t),
                penalite(t),
                pct(PLAFOND, t),
                garantieEnMois(t),
                pct(SLA, t),
                gtr,
                gti);
    }

    /**
     * Normalise avant extraction :
     *  - une seule ligne (l'extraction PDF coupe les phrases au milieu)
     *  - nombres en toutes lettres convertis en chiffres : les CDC rédigés, comme
     *    ceux produits par le LLM, écrivent « une garantie d'un an » ou « douze mois ».
     *
     * Limite connue : dictionnaire, pas analyseur. Les formes composées non listées
     * ne sont pas converties — l'extraction renvoie alors null, signalé par un WARN.
     */
    private String normalize(String text) {
        String t = text.replaceAll("\\s+", " ");
        for (var e : MOTS_NOMBRES) {
            t = t.replaceAll("(?i)\\b" + e.getKey() + "\\b", e.getValue());
        }
        return t;
    }

    /** Trié par longueur décroissante : « quatre-vingt-dix » doit passer avant « quatre ». */
    private static final List<Map.Entry<String, String>> MOTS_NOMBRES = Stream.of(
            Map.entry("quatre-vingt-dix", "90"), Map.entry("quatre-vingts?", "80"),
            Map.entry("soixante-dix", "70"),     Map.entry("soixante", "60"),
            Map.entry("cinquante", "50"),        Map.entry("quarante", "40"),
            Map.entry("trente-six", "36"),       Map.entry("trente", "30"),
            Map.entry("vingt-quatre", "24"),     Map.entry("vingt", "20"),
            Map.entry("dix-huit", "18"),         Map.entry("quinze", "15"),
            Map.entry("douze", "12"),            Map.entry("dix", "10"),
            Map.entry("cent", "100"),
            Map.entry("neuf", "9"), Map.entry("huit", "8"), Map.entry("sept", "7"),
            Map.entry("six", "6"),  Map.entry("cinq", "5"), Map.entry("quatre", "4"),
            Map.entry("trois", "3"), Map.entry("deux", "2"), Map.entry("une?", "1"))
        .sorted(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getKey().length()).reversed())
        .toList();

    private Integer delaiEnJours(String t) {
        Matcher m = DELAI.matcher(t);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String unit = m.group(2).toLowerCase();
            if (unit.startsWith("jour") && n >= 15 && n <= 1095) return n;
            if (unit.startsWith("mois") || unit.startsWith("semaine")) return null;
        }
        return null;
    }

    private Integer garantieEnMois(String t) {
        Matcher m = GARANTIE.matcher(t);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            String unit = m.group(2).toLowerCase();
            int mois = unit.startsWith("an") ? n * 12 : n;
            if (mois >= 3 && mois <= 120) return mois;
        }
        return null;
    }

    /** Une pénalité journalière réaliste reste sous 5 % ; au-delà, on a capté un plafond. */
    private Double penalite(String t) {
        Matcher m = PENALITE.matcher(t);
        while (m.find()) {
            try {
                double v = Double.parseDouble(m.group(1).replace(',', '.'));
                if (v > 0 && v <= 5) return v;
            } catch (NumberFormatException ignored) { }
        }
        return null;
    }

    /** Garantie de temps de rétablissement, en heures. Une semaine au maximum. */
    private Integer gtr(String t) {
        Matcher m = GTR.matcher(t);
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h >= 1 && h <= 168) return h;
        }
        return null;
    }

    /** Garantie de temps d'intervention, en heures. Une semaine au maximum. */
    private Integer gti(String t) {
        Matcher m = GTI.matcher(t);
        while (m.find()) {
            int h = Integer.parseInt(m.group(1));
            if (h >= 1 && h <= 168) return h;
        }
        return null;
    }

    private Double pct(Pattern p, String t) {
        Matcher m = p.matcher(t);
        if (!m.find()) return null;
        try {
            return Double.parseDouble(m.group(1).replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record Clauses(Integer delaiJours, Double penalitePct, Double plafondPct,
                          Integer garantieMois, Double slaPct, Integer gtrHeures,
                          Integer gtiHeures) {
        public boolean isEmpty() {
            return delaiJours == null && penalitePct == null && plafondPct == null
                && garantieMois == null && slaPct == null && gtrHeures == null
                && gtiHeures == null;
        }
    }
}
