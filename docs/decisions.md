# Décisions techniques et pièges rencontrés

## Modèle
- `qwen3` : trop lent sur cette machine (dépasse le timeout de lecture). Écarté.
- `qwen2.5:3b` : ~2× plus rapide mais invente des parties contractantes
  (« Alibaba Cloud » dans une clause de pénalités) et produit des dates
  contradictoires. Écarté.
- `qwen2.5:7b`, temperature 0.2 : référence retenue.
- `keep_alive: 30m` indispensable, sinon rechargement de 4,7 Go à chaque appel.
- `num_ctx: 8192` : sans quoi le prompt est tronqué silencieusement.

## Instances Ollama multiples
- `readTimeout` à 10 min : hérité tel quel du flux mono-instance d'avant le
  découpage par section (le commentaire disait "génération CDC complet ~3
  min", pas "une section"). Jamais revu depuis. Avec le réessai automatique
  sur une autre instance en cas d'échec, cette valeur devient aussi le temps
  perdu avant bascule si une instance est réellement bloquée -- constaté en
  conditions réelles lors de la vérification de la répartition (section 5
  d'un CDC de test) : l'instance distante a mis exactement 10 min avant
  d'échouer (`RestClientException` sur extraction de réponse -- une coupure
  de connexion en plein flux se présente ainsi, pas comme un timeout
  explicite), le réessai sur l'instance locale a ensuite pris 215 s.
  Résultat : ~18 min de temps total pour 13 sections sur deux instances,
  à peine mieux que la référence mono-instance (~20 min), alors que les 12
  autres sections s'étaient réparties correctement en ~15 min. Réduit à 6 min
  (marge x2 sur le pire cas théorique mesuré : ~300 s pour num_predict=1500 à
  ~5 tok/s sur l'instance la plus lente) -- pas retesté après coup avec une
  panne réelle, seulement en fonctionnement normal.

## Extraction des valeurs chiffrées
- Regex et non LLM : les colonnes doivent être stables pour que
  `percentile_cont` ait un sens. Un LLM nommerait les champs différemment
  d'un document à l'autre.
- `normalize()` doit aplatir les espaces AVANT extraction : l'extraction PDF
  coupe les phrases au milieu.
- Les nombres en toutes lettres sont convertis par dictionnaire
  (« quatre-vingt-dix (90) »). Limite connue : dictionnaire, pas analyseur.
- Ne JAMAIS convertir « 8 mois » en 240 jours pour le calcul des normes :
  granularité d'engagement différente, la médiane ne décrit plus rien.
- Garde de plausibilité sur la pénalité (`<= 5 %`) : sans elle, le plafond
  était capté comme pénalité journalière.
- Garde de plausibilité GTI/GTR : un CDC généré par le LLM a étiqueté deux
  lignes de tableau SLA « Rétablissement » (GTI et GTR confondus), captant un
  GTR de 2 h au lieu de 24 h. Une valeur fausse est pire qu'une valeur
  absente ici plus qu'ailleurs : un GTR faux entre dans une médiane SQL et
  devient une norme citée par le relecteur, sans qu'aucune étape en aval ne
  puisse la détecter. Règle : GTR doit être strictement supérieur à GTI,
  sinon les deux sont mis à null (`ClauseExtractor.extract`).

## Relecteur
- Cinq variantes de prompt essayées : faux positifs → silence → faux positifs.
  Le prompt n'est pas le bon levier.
- Solution : `DeviationChecker` calcule les écarts en Java. Le LLM ne reçoit
  que des faits établis et rédige.
- Un null d'extraction n'est pas une clause absente. Ne jamais laisser le
  modèle en conclure une omission.
- Valider le JSON du LLM avant insertion en `jsonb`, sinon exception opaque.

## JDBC
- Colonne `numeric(5,2)` → lire en `BigDecimal`, pas `Double`.
  `rs.getObject(name, Double.class)` échoue.
- `percentile_cont` sur une colonne `integer` renvoie un `double precision` :
  celui-là passe en `Double`. D'où l'incohérence apparente.

## Corpus
- Corpus généré avec une distribution centrée (pas uniforme) : sans centre de
  gravité, aucune norme n'est détectable et le relecteur ne signale rien.
- Les six CDC rédigés à la main sont le seul vrai test de l'extraction :
  ils ont révélé trois bugs que les 20 documents templatés ne montraient pas.
- Pour atteindre 15-30 pages par document sans dupliquer une exigence mot
  pour mot (repérable au premier coup d'œil, pas juste "peu réaliste") :
  ré-échantillonner un pool trop petit produit des doublons visibles dès que
  n dépasse sa taille. Solution : combiner sujet + objet séparément
  (`SUJETS_SYSTEME`/`SUJETS_PRESTATAIRE` × objets EF) pour multiplier le
  nombre de phrases uniques sans réécrire tout le pool à la main. Les
  sections tableau (specs_techniques, livrables) restent en revanche
  plafonnées à la taille réelle du pool : une ligne dupliquée se voit bien
  plus qu'une phrase EF reformulée.
- En-tête/pied de page/page de garde (tools/render_corpus.py) ne cassent ni
  ClauseExtractor ni le découpage par section : ce texte ne contient aucun
  des mots-clés des regex (délai, pénalité, garanti, disponibilité, GTR) et
  ne matche jamais `^\d{1,2}\.\s` (repéré comme titre de section).

## Plans de génération
- Instantané complet du plan dans `cdc_event` à chaque exécution (pas
  seulement une référence `plan_id`) : un plan reste modifiable après coup,
  et rien n'empêche de supprimer une étape ou de changer son ordre entre
  deux exécutions. Sans instantané, l'historique d'un vieux CDC afficherait
  le plan TEL QU'IL EST AUJOURD'HUI, pas tel qu'il était au moment de la
  génération -- une réécriture silencieuse du passé.
- Mode TRAME : un seul appel LLM, écrit dans `cdc.body` (le champ
  monolithique resté dormant depuis le passage à la génération par
  section), aucune `cdc_section` n'est touchée. Choisi plutôt que
  d'écrire un texte court dans CHAQUE section (statut dédié `TRAME` ou
  similaire) pour deux raisons : (1) la sémantique d'une trame est un
  sommaire du document entier, pas 14 sections individuellement à moitié
  remplies qu'il faudrait ensuite distinguer d'un vrai brouillon dans
  chaque écran qui lit `cdc_section.status` ; (2) ça évite d'inventer un
  état supplémentaire dans la machine à états DRAFT/APPROVED/FAILED déjà
  utilisée partout (approbation, export, Reviewer). `inclus` est ignoré en
  mode TRAME : une trame sert de sommaire complet du document, pas d'un
  sous-ensemble de sections.
- Un step de plan référence une section par `code`, pas par
  `template_section_id` ni par position : c'est le seul identifiant qui
  survit à la fois côté template (ordre/titre modifiables) et côté CDC déjà
  généré (dont l'`ordre` de section est figé depuis la création). Réutilise
  la même convention que SectionDetector et le filtre `RetrievalRepository`
  plutôt que d'introduire un troisième schéma de correspondance.

## Export PDF/DOCX
- Un bloc markdown "phrase d'intro + liste à puces, sans ligne vide entre
  les deux" (très courant dans les sections générées, ex. "Sont inclus :\n-
  item") classait tout le bloc en paragraphe brut si la règle de
  détection exigeait que TOUTES les lignes d'un même bloc soient des puces.
  Contenu jamais perdu (le texte "- item" reste lisible tel quel dans le
  paragraphe), juste pas mis en forme en liste -- repéré en comparant le
  texte extrait du DOCX au markdown source, pas juste en vérifiant que
  l'export ne plantait pas.
- Ancien `PdfExporter` maison (PDFBox, curseur Y dessiné à la main) remplacé
  par Flying Saucer (rendu HTML+CSS, sur OpenPDF) pour obtenir un sommaire
  avec vrais numéros de page et des en-têtes/pieds de page courants sans
  réimplémenter la pagination à la main -- PDFBox reste utilisé, mais
  seulement pour LIRE des PDF à l'ingestion.
- API Flying Saucer découverte par introspection du jar (`javap`), pas
  supposée depuis la documentation en ligne (le projet est peu documenté) :
  `ITextRenderer.findPagePositionsByID(Pattern)` (donne la page de chaque
  élément identifié par id) plantait en `NullPointerException` sur
  `this._root == null` tant que `layout()` seul avait été appelé -- il faut
  aussi `getOutputDevice().setRoot(getRootBox())` puis `.start(getDocument())`
  avant de pouvoir l'interroger, sans pour autant appeler `createPDF()` (pas
  besoin d'écrire de PDF juste pour connaître la pagination). Validé par un
  petit programme autonome avant d'écrire `PdfExporter` en entier, pas
  découvert en déboguant l'export final.
- En-têtes/pieds de page en PDF : Flying Saucer ne supporte pas les
  éléments courants de CSS Paged Media (`@top-center` etc.). Solution
  standard du moteur : un `PdfPageEventHelper` enregistré via
  `PDFCreationListener.preOpen` (`renderer.getWriter().setPageEvent(...)`),
  qui dessine directement sur `PdfContentByte` à chaque `onEndPage`.
- `CTColor.getVal()` (POI/XMLBeans, lecture d'un style Heading pour
  `TemplateStyleExtractor`) renvoie un `byte[3]` (RGB) pour une vraie
  couleur hex, mais une `String` pour le mot-clé "auto" -- ST_HexColor est
  un type union. Un `getVal().toString()` naïf sur le cas `byte[]` donne
  `"[B@75c56eb9"` au lieu de la couleur. Repéré en testant l'extraction sur
  un .docx construit avec une couleur Heading1 CONNUE (`C0392B`) et en
  comparant la valeur extraite au lieu de supposer que ça marchait.
- Sommaire DOCX avec numéros de page réels : un champ Word natif (`TOC \o
  "1-2" \h \z \u`, inséré en XML brut via `CTR.addNewFldChar()`/
  `addNewInstrText()`) reste VIDE tant qu'il n'a pas été "mis à jour" --
  `document.getSettings().setUpdateFields()` force Word à le faire tout
  seul à l'ouverture, sans quoi l'export semblerait cassé au premier coup
  d'œil alors que le champ est correct.
- Ancrer un bloc (mention de confidentialité) au bas d'une page de garde de
  hauteur variable (logo/organisation/contact tous optionnels) : validé
  empiriquement avant d'écrire `DocxExporter` en entier plutôt que supposé.
  En DOCX, Word n'a pas de positionnement absolu ; un tableau à deux lignes
  SANS bordures fonctionne car une ligne en `TableRowHeightRule.AT_LEAST`
  est padée par Word jusqu'à la hauteur demandée même si son contenu est
  plus court -- vérifié en générant un .docx de test et en inspectant
  `word/document.xml` (`<w:trHeight w:hRule="atLeast">`, `<w:vAlign
  w:val="bottom"/>`) plutôt qu'en ouvrant le fichier dans Word. En PDF,
  Flying Saucer accepte `position: relative`/`absolute` en CSS simple, donc
  la même idée marche directement (`.confidentialite { position: absolute;
  bottom: 0; }` dans un conteneur de hauteur fixe).
- Flying Saucer supporte nativement les URI `data:` dans `<img
  src="data:image/png;base64,...">` sans `UserAgentCallback` custom --
  vérifié par un petit programme autonome (PNG construit à la main en
  Python, `pdfimages -list` + `pdftoppm` pour confirmer visuellement
  l'image embarquée) avant d'écrire le code de production, cohérent avec la
  discipline déjà appliquée pour `findPagePositionsByID`.

## Génération section par section
- Le découpage par section (SectionDetector) repère les titres numérotés
  ("6. Spécifications techniques") et les associe aux codes du template par
  correspondance de titre normalisé. Les CDC réels (uploads/) ne suivent pas
  cette structure : leurs chunks restent `section_code = NULL` plutôt que
  d'être rattachés à la mauvaise section — comportement voulu, pas un bug.
- Retirer le body monolithique de `cdc` (remplacé par les sections) a cassé
  le Reviewer, qui lisait `cdc.body()`. Corrigé en reconstituant le texte à
  partir des sections DRAFT (`CdcSectionRepository.assembleDraftBody`)
  plutôt que de garder un champ dupliqué à resynchroniser.
- Le nom du client fictif du corpus (« Groupe EB ») fuitait dans les CDC
  générés : les extraits injectés dans le prompt comme inspiration de style
  contiennent le nom de LEUR client, et le modèle le recopie sans qu'on le
  lui demande -- un cas de plus dans la même famille que qwen2.5:3b inventant
  « Alibaba Cloud » (voir Modèle) : le modèle ne distingue pas spontanément
  "exemple de style" de "fait à reprendre".
- Une règle de prompt seule ne suffit pas à garantir ça : sur une
  régénération complète des 13 sections obligatoires du CDC 5, 10 sections
  contenaient encore « Groupe EB » malgré la règle explicite. Certaines
  sections courtes et fortement gabarisées (`objet` n'a que 3 formulations
  possibles dans tools/generate_corpus.py) sont recopiées quasi verbatim à
  température 0.2, et l'auto-référence (le CDC en cours de régénération
  retrouvé comme "exemple similaire" de lui-même, encore non ré-ingéré)
  aggravait le problème. Même en supprimant l'auto-référence, 2 sections sur
  13 continuaient de fuiter en pratique. Solution finale, à deux niveaux :
  - Prompt (`CdcSectionGenerationWorker.buildPrompt`) : ne jamais reprendre
    un nom d'organisation des extraits, désigner le client par
    `app.org-name` (env `ORG_NAME`) si configuré, sinon par « le donneur
    d'ordre ». Réduit la fréquence, ne suffit pas seul.
  - Filet de sécurité déterministe (`sanitizeClientName`) : substitution
    regex garantie après génération, avec fusion de l'article français
    plutôt que duplication (« Le Groupe EB » -> « Le donneur d'ordre », pas
    « Le le donneur d'ordre » ; « du Groupe EB » -> « du donneur d'ordre »).
    Même logique que ClauseExtractor/DeviationChecker : ce qui doit être
    garanti ne se confie pas au LLM seul.
  Vérifié à zéro occurrence sur les 13 sections du CDC 5 après régénération
  complète + ré-ingestion, et sur une section Planning fraîche d'un nouveau
  CDC.
