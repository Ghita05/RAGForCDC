package com.eb.cdcrag.review;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public class NormRepository {

    private final JdbcTemplate jdbc;
    public NormRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Normes historiques pour un type de CDC. Renvoie null si l'échantillon est trop faible. */
    public Norms forType(String cdcType) {
        return jdbc.query("""
                SELECT count(delai_jours)                                              AS n,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY delai_jours)        AS delai_med,
                       min(delai_jours)                                                AS delai_min,
                       max(delai_jours)                                                AS delai_max,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY penalite_pct)       AS pen_med,
                       min(penalite_pct)                                               AS pen_min,
                       max(penalite_pct)                                               AS pen_max,
                       percentile_cont(0.5) WITHIN GROUP (ORDER BY garantie_mois)      AS gar_med,
                       min(garantie_mois)                                              AS gar_min,
                       max(garantie_mois)                                              AS gar_max
                FROM document
                WHERE (? IS NULL OR cdc_type = ?) AND delai_jours IS NOT NULL
                """,
                (rs, i) -> new Norms(rs.getInt("n"),
                        rs.getObject("delai_med", Double.class), rs.getObject("delai_min", Integer.class),
                        rs.getObject("delai_max", Integer.class),
                        dbl(rs.getBigDecimal("pen_med")), dbl(rs.getBigDecimal("pen_min")),
                        dbl(rs.getBigDecimal("pen_max")),
                        rs.getObject("gar_med", Double.class), rs.getObject("gar_min", Integer.class),
                        rs.getObject("gar_max", Integer.class)),
                cdcType, cdcType).stream().findFirst().orElse(null);
    }

    /** numeric -> Double : le driver PostgreSQL ne fait pas la conversion directe. */
    private static Double dbl(BigDecimal v) { return v == null ? null : v.doubleValue(); }

    public record Norms(int n,
                        Double delaiMedian, Integer delaiMin, Integer delaiMax,
                        Double penaliteMedian, Double penaliteMin, Double penaliteMax,
                        Double garantieMedian, Integer garantieMin, Integer garantieMax) {

        /** Rendu compact destiné au prompt du LLM. */
        public String toPrompt(String cdcType) {
            if (n < 3 || delaiMedian == null || penaliteMedian == null || garantieMedian == null)
                return "(historique insuffisant pour établir des normes fiables)";
            return """
                Normes historiques pour les marchés de type « %s » (n = %d) :
                - Délai d'exécution : médiane %.0f jours (min %d, max %d)
                - Pénalité de retard : médiane %.2f %%/jour (min %.2f, max %.2f)
                - Garantie : médiane %.0f mois (min %d, max %d)
                """.formatted(cdcType, n, delaiMedian, delaiMin, delaiMax,
                    penaliteMedian, penaliteMin, penaliteMax,
                    garantieMedian, garantieMin, garantieMax);
        }
    }
}
