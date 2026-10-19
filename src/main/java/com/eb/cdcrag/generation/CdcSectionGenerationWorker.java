package com.eb.cdcrag.generation;

import com.eb.cdcrag.embedding.EmbeddingModel;
import com.eb.cdcrag.llm.OllamaClient;
import com.eb.cdcrag.qa.RetrievalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Component
public class CdcSectionGenerationWorker {

    private static final Logger log = LoggerFactory.getLogger(CdcSectionGenerationWorker.class);
    private static final int EXAMPLES_K = 4;
    /** Nom commun, sans article : l'article se fusionne à la substitution
     * (le/du/au Groupe EB -> le/du/au donneur d'ordre), voir sanitize(). */
    private static final String DEFAULT_CLIENT_LABEL = "donneur d'ordre";

    /** Filet de sécurité déterministe : le corpus synthétique n'a qu'une
     * seule organisation fictive ("Groupe EB"), répétée dans presque toutes
     * les phrases-gabarits de tools/generate_corpus.py. À température 0.2,
     * le modèle recopie parfois ce nom malgré la consigne du prompt (constaté
     * empiriquement : ~2 sections sur 13 sur une régénération complète, même
     * après retrait de l'auto-référence). Comme pour ClauseExtractor, on ne
     * fait pas confiance au LLM seul sur ce qui doit être garanti. */
    private static final Pattern ARTICLED_CORPUS_ORG =
            Pattern.compile("\\b(le|la|du|au)\\s+groupe\\s*eb\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE_CORPUS_ORG = Pattern.compile("groupe\\s*eb", Pattern.CASE_INSENSITIVE);

    /** Filet de sécurité déterministe pour une déformation lexicale
     * observée en pratique : le modèle écrit parfois "donateur d'ordre" à la
     * place de "donneur d'ordre" (confusion avec "donateur" = donor), malgré
     * la consigne du prompt -- même logique que ARTICLED_CORPUS_ORG, on ne
     * fait pas confiance au LLM seul sur une expression qui doit être
     * garantie. Ne s'applique que si l'expression générique par défaut est
     * réellement l'expression attendue (aucun nom d'organisation configuré) ;
     * l'article qui précède ("le"/"du"/"au") reste inchangé, seul le mot
     * fautif est corrigé. */
    private static final Pattern DONATEUR_D_ORDRE = Pattern.compile("donateurs?(\\s+d['’]ordre)", Pattern.CASE_INSENSITIVE);

    private final OllamaClient ollama;
    private final EmbeddingModel embeddings;
    private final RetrievalRepository retrieval;
    private final CdcRepository cdcRepository;
    private final CdcSectionRepository sectionRepository;
    /** Avec article, pour la consigne du prompt ("désigne le client par « %s »"). */
    private final String clientLabel;
    /** Sans article, pour recomposer un article fusionné (le/du/au) en sanitize(). */
    private final String bareClientLabel;
    /** false si un nom d'organisation est configuré (nom propre : pas de
     * fusion d'article) ; true pour le nom commun par défaut. */
    private final boolean genericClientLabel;

    public CdcSectionGenerationWorker(OllamaClient ollama, EmbeddingModel embeddings,
                                      RetrievalRepository retrieval, CdcRepository cdcRepository,
                                      CdcSectionRepository sectionRepository,
                                      @Value("${app.org-name:}") String orgName) {
        this.ollama = ollama;
        this.embeddings = embeddings;
        this.retrieval = retrieval;
        this.cdcRepository = cdcRepository;
        this.sectionRepository = sectionRepository;
        boolean hasOrgName = orgName != null && !orgName.isBlank();
        this.genericClientLabel = !hasOrgName;
        this.bareClientLabel = hasOrgName ? orgName.strip() : DEFAULT_CLIENT_LABEL;
        this.clientLabel = hasOrgName ? bareClientLabel : "le " + DEFAULT_CLIENT_LABEL;
    }

    /** Suppose que le statut a déjà été passé à QUEUED par l'appelant
     * (synchrone), pour que GET /api/cdc/{id} reflète l'état immédiatement.
     * QUEUED -> GENERATING est posé ici, au tout début du traitement
     * effectif : le pool n'a qu'un thread, une section peut rester QUEUED un
     * moment derrière d'autres tâches avant d'être vraiment traitée. */
    @Async("generationExecutor")
    public void run(long cdcId, int ordre) {
        doRun(cdcId, ordre, null);
    }

    /** guidance s'ajoute à la consigne du template pour cette génération
     * précise (voir PlanExecutionService), sans jamais la remplacer. */
    @Async("generationExecutor")
    public void run(long cdcId, int ordre, String guidance) {
        doRun(cdcId, ordre, guidance);
    }

    // Logique partagée par les deux méthodes @Async ci-dessus : un appel
    // interne this.run(...) depuis l'une vers l'autre contournerait le proxy
    // Spring (auto-invocation) et s'exécuterait de façon synchrone.
    private void doRun(long cdcId, int ordre, String guidance) {
        long t0 = System.currentTimeMillis();
        var cdc = cdcRepository.findById(cdcId).orElse(null);
        var section = sectionRepository.findByCdcAndOrdre(cdcId, ordre).orElse(null);
        if (cdc == null || section == null) {
            log.error("CDC {} section {}: introuvable au moment de la génération", cdcId, ordre);
            return;
        }
        sectionRepository.markGenerating(cdcId, ordre);
        try {
            String brief = (cdc.brief() == null || cdc.brief().isBlank()) ? cdc.title() : cdc.brief();
            String seed = section.titre() + " — " + brief;
            float[] qv = embeddings.embed(seed, EmbeddingModel.Type.QUERY);
            List<RetrievalRepository.RetrievedChunk> examples =
                    retrieval.search(qv, EXAMPLES_K, cdc.cdcType(), section.code());
            log.info("CDC {} section {} ({}): {} extrait(s) récupéré(s) : {}", cdcId, ordre, section.code(),
                    examples.size(), examples.stream().map(RetrievalRepository.RetrievedChunk::filename).distinct().toList());

            String text = ollama.generate(buildPrompt(cdc.cdcType(), brief, section, examples, guidance),
                    "CDC %d section %d (%s)".formatted(cdcId, ordre, section.code()));
            text = sanitizeClientName(text);
            sectionRepository.markDraft(cdcId, ordre, text);
            log.info("CDC {} section {}: {} caractères générés en {} s", cdcId, ordre, text.length(),
                    (System.currentTimeMillis() - t0) / 1000);
        } catch (Exception ex) {
            log.error("CDC {} section {}: échec de génération", cdcId, ordre, ex);
            sectionRepository.markFailed(cdcId, ordre, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    /** Mode TRAME (voir PlanExecutionService) : un seul appel, produit une
     * synthèse (cadrage + sommaire + intention de chaque section), stockée
     * dans cdc.body -- aucun corps de section n'est écrit ici, les sections
     * restent dans leur statut courant (typiquement EMPTY). */
    @Async("generationExecutor")
    public void runTrame(long cdcId, List<String> sectionTitles, String guidance) {
        long t0 = System.currentTimeMillis();
        var cdc = cdcRepository.findById(cdcId).orElse(null);
        if (cdc == null) {
            log.error("CDC {}: introuvable au moment de la génération de trame", cdcId);
            return;
        }
        try {
            String brief = (cdc.brief() == null || cdc.brief().isBlank()) ? cdc.title() : cdc.brief();
            String text = ollama.generate(buildTramePrompt(cdc.cdcType(), brief, sectionTitles, guidance),
                    "CDC %d trame".formatted(cdcId));
            text = sanitizeClientName(text);
            cdcRepository.updateBody(cdcId, text);
            log.info("CDC {}: trame générée ({} caractères) en {} s", cdcId, text.length(),
                    (System.currentTimeMillis() - t0) / 1000);
        } catch (Exception ex) {
            log.error("CDC {}: échec de génération de la trame", cdcId, ex);
            cdcRepository.markError(cdcId, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private String buildTramePrompt(String cdcType, String brief, List<String> sectionTitles, String guidance) {
        String sommaire = IntStream.range(0, sectionTitles.size())
                .mapToObj(i -> (i + 1) + ". " + sectionTitles.get(i))
                .reduce((a, b) -> a + "\n" + b).orElse("");

        String consigne = (guidance == null || guidance.isBlank())
                ? ""
                : "\n\nPrécision supplémentaire : " + guidance.strip();

        return """
            Tu es un expert en rédaction de cahiers des charges (CDC).
            Rédige une TRAME de cahier des charges : PAS le contenu
            détaillé de chaque section, seulement un cadrage général du projet
            (2-3 phrases) suivi d'une ligne d'intention (une phrase, ce que la
            section va couvrir) pour CHACUNE des sections listées ci-dessous,
            dans cet ordre. N'écris aucun corps de section complet.

            PROJET
            Type : %s
            Contexte : %s%s

            SECTIONS DU CAHIER DES CHARGES, DANS L'ORDRE :
            %s

            Format de sortie : le cadrage général d'abord, puis une ligne par
            section au format "N. Titre — intention en une phrase". Rédige
            uniquement ce contenu, sans commentaire.

            RÈGLE DE LANGUE : rédige intégralement en français, sans aucun terme
            anglais -- y compris pour le vocabulaire technique (utilise toujours
            l'équivalent français exact, ex. « disponibilité » et non
            « availability »).

            RÈGLE IMPORTANTE SUR LE NOM DU CLIENT : désigne le client
            UNIQUEMENT par l'expression exacte « %s », reprise mot pour mot à
            chaque occurrence -- jamais une variante, une déformation ou un
            autre nom d'organisation (pas de « le Groupe », pas de
            « l'organisation »).
            """.formatted(cdcType, brief, consigne, sommaire, clientLabel);
    }

    /** Remplace "Groupe EB" par le client réel, en fusionnant l'article
     * français plutôt qu'en le dupliquant ("Le Groupe EB" -> "Le donneur
     * d'ordre", pas "Le le donneur d'ordre" ; "du Groupe EB" -> "du donneur
     * d'ordre"). Un nom d'organisation configuré est un nom propre : pas de
     * fusion, substitution telle quelle. */
    private String sanitizeClientName(String text) {
        Matcher articled = ARTICLED_CORPUS_ORG.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (articled.find()) {
            String article = articled.group(1);
            String replacement;
            if (genericClientLabel) {
                String mergedArticle = switch (article.toLowerCase()) {
                    case "du" -> "du";
                    case "au" -> "au";
                    default -> "le";
                };
                if (Character.isUpperCase(article.charAt(0))) {
                    mergedArticle = Character.toUpperCase(mergedArticle.charAt(0)) + mergedArticle.substring(1);
                }
                replacement = mergedArticle + " " + bareClientLabel;
            } else {
                replacement = bareClientLabel;
            }
            articled.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        articled.appendTail(sb);

        String result = BARE_CORPUS_ORG.matcher(sb.toString()).replaceAll(Matcher.quoteReplacement(clientLabel));
        if (genericClientLabel) {
            result = DONATEUR_D_ORDRE.matcher(result).replaceAll("donneur$1");
        }
        return result;
    }

    private String buildPrompt(String cdcType, String brief, CdcSectionRepository.Section section,
                               List<RetrievalRepository.RetrievedChunk> examples, String guidance) {
        String context = examples.isEmpty()
            ? "(aucun exemple disponible)"
            : IntStream.range(0, examples.size())
                .mapToObj(i -> "[Exemple " + (i + 1) + " - " + examples.get(i).filename() + "]\n"
                        + examples.get(i).content())
                .reduce((a, b) -> a + "\n\n" + b).orElse("");

        // guidance (d'un plan) s'ajoute à la consigne du template, ne la
        // remplace jamais.
        String consigne = (guidance == null || guidance.isBlank())
                ? section.consigne()
                : section.consigne() + "\n\nPrécision supplémentaire pour cette génération : " + guidance.strip();

        return """
            Tu es un expert en rédaction de cahiers des charges (CDC).
            Rédige UNIQUEMENT la section « %s » d'un cahier des charges,
            pour le projet décrit ci-dessous. N'écris pas d'autre section, pas de
            titre ni de numérotation : uniquement le contenu de cette section.

            CONSIGNE POUR CETTE SECTION : %s

            PROJET
            Type : %s
            Contexte : %s

            EXTRAITS DE CDC SIMILAIRES POUR CETTE SECTION, à utiliser comme
            inspiration de style et de contenu, sans les recopier :
            %s

            RÈGLE DE LANGUE : rédige intégralement en français, sans aucun terme
            anglais -- y compris pour le vocabulaire technique (utilise toujours
            l'équivalent français exact, ex. « disponibilité » et non
            « availability »).

            RÈGLE IMPORTANTE SUR LE NOM DU CLIENT : les extraits ci-dessus
            proviennent d'autres CDC et mentionnent leur propre client (nom
            d'organisation, direction, etc.). N'utilise JAMAIS un de ces noms
            pour le projet en cours, même par imitation du style. Désigne le
            client UNIQUEMENT par l'expression exacte « %s », reprise mot pour
            mot à chaque occurrence -- jamais une variante, une déformation ou
            un synonyme (pas de « le Groupe », pas de « l'organisation », pas
            d'abréviation).

            Rédige uniquement le contenu de la section, sans commentaire.
            """.formatted(section.titre(), consigne, cdcType, brief, context, clientLabel);
    }
}
