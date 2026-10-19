package com.eb.cdcrag.review;

import com.eb.cdcrag.qa.RetrievalRepository;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Le LLM ne décide plus de ce qui constitue un écart : les écarts lui sont
 * fournis, déjà calculés. Son rôle se limite à rédiger l'explication et la
 * suggestion, et à repérer les problèmes qualitatifs (contradiction interne,
 * section réellement absente, formulation imprécise).
 */
public final class ReviewPrompt {

    private ReviewPrompt() {}

    public static String build(String cdcType, String body,
                               List<DeviationChecker.Deviation> deviations,
                               List<RetrievalRepository.RetrievedChunk> comparables) {

        String ecarts = deviations.isEmpty()
            ? "Aucun écart chiffré détecté : toutes les valeurs extraites se situent dans l'enveloppe historique."
            : deviations.stream()
                .map(d -> "- " + d.champ() + " : " + d.valeurCdc() + " — " + d.sens() + " (" + d.norme() + ")")
                .reduce((a, b) -> a + "\n" + b).orElse("");

        String extraits = comparables.isEmpty()
            ? "(aucune clause comparable trouvée)"
            : IntStream.range(0, comparables.size())
                .mapToObj(i -> "[" + comparables.get(i).filename() + "]\n" + comparables.get(i).content())
                .reduce((a, b) -> a + "\n\n" + b).orElse("");

        return """
            Tu es un relecteur expert de cahiers des charges (CDC) pour une direction des achats.

            RÈGLES STRICTES :
            - Réponds UNIQUEMENT en JSON valide, sans texte ni balises autour.
            - Écris en français.
            - Les écarts chiffrés ci-dessous ont DÉJÀ été calculés. Reprends-les tels quels,
              sans recalculer, sans en inventer d'autres, sans comparer d'autres valeurs aux normes.
            - Si la liste des écarts est vide, ne crée AUCUN constat de catégorie « ecart_norme ».
            - Pour « section_manquante » : relis le CDC ci-dessous et vérifie qu'aucune section
              ne traite le sujet. Ne te fonde jamais sur les écarts pour conclure à une absence.
            - Ne cite jamais une valeur comme figurant dans le CDC si tu ne l'as pas lue dans son texte.
            - Un constat par problème.

            ÉCARTS CHIFFRÉS DÉJÀ ÉTABLIS (type « %s ») :
            %s

            CLAUSES COMPARABLES ISSUES DE CDC HISTORIQUES :
            %s

            CDC À RELIRE :
            %s

            Structure JSON attendue :
            {
              "resume": "deux phrases maximum",
              "constats": [
                {
                  "categorie": "ecart_norme | section_manquante | contradiction | imprecision",
                  "severite": "haute | moyenne | basse",
                  "titre": "libellé court",
                  "explication": "une à deux phrases",
                  "suggestion": "correction concrète"
                }
              ]
            }
            Renvoie "constats": [] si rien n'est à signaler.
            """.formatted(cdcType, ecarts, extraits, body);
    }
}
