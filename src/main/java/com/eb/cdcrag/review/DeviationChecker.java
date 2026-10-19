package com.eb.cdcrag.review;

import com.eb.cdcrag.ingestion.ClauseExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Détermine EN JAVA quelles valeurs s'écartent des normes historiques.
 *
 * Le LLM s'est montré peu fiable sur ce point : il compare des unités
 * incompatibles (dirhams/jour vs %/jour), signale comme absentes des valeurs
 * qu'il vient de citer, et qualifie d'écart une valeur strictement conforme.
 * Ici la comparaison est arithmétique et typée : soit l'écart existe, soit non.
 * Le LLM ne reçoit ensuite que des faits établis, et se contente de les rédiger.
 */
public final class DeviationChecker {

    private DeviationChecker() {}

    /** valeurCdc/norme restent des chaînes formatées pour le prompt du LLM
     * (ReviewPrompt) ; les champs numériques (valeur/median/min/max/n) sont
     * pour l'affichage structuré côté frontend -- le LLM ne les voit jamais
     * et ne peut donc pas les altérer en les rédigeant. */
    public record Deviation(String champ, String valeurCdc, String norme, String sens,
                            double valeur, String unite, double median, double min, double max, int n) {}

    public static List<Deviation> check(ClauseExtractor.Clauses c, NormRepository.Norms n) {
        List<Deviation> out = new ArrayList<>();
        if (n == null || n.n() < 3) return out;

        add(out, "Délai d'exécution", c.delaiJours(), n.delaiMedian(), n.delaiMin(), n.delaiMax(), "jours", n.n());
        add(out, "Pénalité de retard", c.penalitePct(), n.penaliteMedian(), n.penaliteMin(), n.penaliteMax(), "%/jour", n.n());
        add(out, "Garantie", c.garantieMois(), n.garantieMedian(), n.garantieMin(), n.garantieMax(), "mois", n.n());
        return out;
    }

    private static void add(List<Deviation> out, String champ, Number valeur,
                            Double median, Number min, Number max, String unite, int sampleSize) {
        // Valeur non extraite : aucune comparaison possible. On ne signale RIEN
        // ici — l'absence d'extraction n'est pas l'absence de clause.
        if (valeur == null || median == null || min == null || max == null) return;

        double v = valeur.doubleValue();
        double lo = min.doubleValue(), hi = max.doubleValue();
        if (v >= lo && v <= hi) return;   // dans l'enveloppe historique : conforme

        out.add(new Deviation(champ,
                trim(v) + " " + unite,
                "médiane " + trim(median) + " " + unite + " (historique " + trim(lo) + "–" + trim(hi) + ")",
                v > hi ? "supérieur au maximum historique" : "inférieur au minimum historique",
                v, unite, median, lo, hi, sampleSize));
    }

    private static String trim(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
}
