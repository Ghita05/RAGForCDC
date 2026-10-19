# CLAUDE.md

Guide pour Claude Code sur ce dépôt.

## Stack
- Java 21, Spring Boot 3.4.1, Maven (wrapper: ./mvnw).
- Accès données : JdbcTemplate direct, PAS de JPA/Hibernate. Repositories =
  classes @Repository qui écrivent le SQL à la main (voir CdcRepository,
  RetrievalRepository).
- Postgres + pgvector (colonnes `vector(384)`, index HNSW, opérateur `<=>`).
  DB locale : `cdcrag`, user `ghita`.
- Embeddings : DJL + PyTorch en JVM (pas d'appel réseau).
- Génération texte : Ollama, modèle qwen2.5:7b, temperature 0.2,
  keep_alive 30m, num_ctx 8192 (voir docs/decisions.md pour le pourquoi).

## Instances Ollama
- `cdcrag.ollama.urls` (application.yml, override via `OLLAMA_URLS`) : liste
  d'URL Ollama séparées par des virgules, une par machine. Une seule URL
  reste un cas valide (comportement mono-instance inchangé).
- `OllamaClient` choisit l'instance PAR DISPONIBILITÉ (un `Semaphore(1)` par
  instance, prend la première libre en scrutant dans l'ordre de
  configuration), jamais par tour de rôle fixe : les machines n'ont pas la
  même vitesse (mesuré ~7 tok/s en local contre ~5 tok/s sur une machine
  distante), un round-robin laisserait la plus rapide inactive en attendant
  son tour pendant que la plus lente est encore occupée.
- Échec ou non-réponse d'une instance : réessai automatique sur une autre
  instance (exclue de la sélection pour cet appel), avant de remonter
  l'échec à l'appelant -- une section ne passe FAILED que si TOUTES les
  instances ont échoué. `OllamaClient.generate(prompt, context)` journalise
  quelle instance a traité quel appel (`context` est une étiquette libre,
  ex. "CDC 5 section 3 (specs_techniques)" -- jamais envoyée à Ollama).
- `generationExecutor` (AsyncConfig) a un thread par instance configurée
  (`OllamaClient.parseUrls(...).size()`), pas un nombre fixe : plus de
  threads que d'instances ne ferait qu'attendre une instance libre.

## Conventions de code
- Records Java pour les DTO/résultats de repository, pas de classes mutables.
- SQL en text block (`"""`) directement dans les méthodes de repository.
- Constructeur injection uniquement (pas de @Autowired sur champ).
- Un composant @Component/@Service par responsabilité unique (ex :
  ClauseExtractor ne fait que l'extraction regex, IngestionService orchestre).
- Logs SLF4J : WARN pour une extraction incomplète, ERROR + statut FAILED en
  base pour un échec de génération async.

## Règles métier à ne pas casser
- Extraction des valeurs chiffrées (ClauseExtractor) : TOUJOURS regex,
  jamais LLM. Ces valeurs alimentent des normes historiques
  (percentile_cont) ; un LLM nommerait les champs différemment d'un document
  à l'autre. Une valeur fausse est pire qu'une valeur absente -> renvoyer
  null en cas de doute, jamais deviner.
- Ne jamais convertir un délai en mois vers des jours (granularité
  d'engagement différente).
- Colonnes `numeric(5,2)` : lire en BigDecimal, jamais Double.
- Le Reviewer (DeviationChecker) calcule les écarts en Java ; le LLM ne fait
  que rédiger à partir de faits déjà établis. Un null d'extraction n'est
  jamais interprété comme une absence de clause.
- Génération LLM asynchrone : `generationExecutor` (AsyncConfig) a un thread
  par instance Ollama configurée (`cdcrag.ollama.urls`), pas un nombre fixe
  -- voir `## Instances Ollama`. La contrainte RAM d'origine (une seule
  génération 7b à la fois) reste valable PAR MACHINE : ne jamais lister deux
  fois la même machine dans `cdcrag.ollama.urls`. Ne pas dimensionner le
  pool autrement qu'en fonction du nombre d'instances sans en parler.
- Désignation du client dans le texte généré (`CdcSectionGenerationWorker`) :
  la consigne de prompt seule ne suffit pas -- le LLM recopie parfois le nom
  fictif du corpus synthétique ("Groupe EB") ou déforme l'expression par
  défaut ("donneur d'ordre" -> "donateur d'ordre"). Comme pour
  `ClauseExtractor`, filet de sécurité déterministe en post-traitement
  (`sanitizeClientName`) plutôt que de faire confiance au LLM seul :
  substitution regex après génération, jamais une simple consigne de prompt
  isolée. Le prompt exige aussi explicitement l'absence de tout terme
  anglais (ex. "disponibilité", jamais "availability") -- vérifié en
  régénérant une section, pas de garde-fou déterministe possible pour un
  anglicisme arbitraire (contrairement au nom du client, un dictionnaire
  d'anglicismes serait disproportionné).

## Plans de génération
- Un TEMPLATE décrit la structure d'un CDC (sections, titres, consignes de
  base). Un PLAN décrit COMMENT générer à partir d'un template : quelles
  sections, dans quel ordre d'exécution, avec quelle guidance en plus, dans
  quel mode. Un plan référence toujours un template et ne stocke que
  l'écart (`cdc_generation_plan_step.guidance` s'AJOUTE à la consigne du
  template, ne la remplace jamais -- voir
  `CdcSectionGenerationWorker.buildPrompt`). Le template d'un plan est
  immuable après création (`PlanService.update` ne prend pas de
  `templateId`) : ses étapes référencent les sections de CE template.
- Un step de plan référence une section par son `code` (identifiant stable,
  déjà utilisé par SectionDetector et le filtre de recherche vectorielle),
  jamais par `template_section_id` brut côté exécution : `PlanExecutionService`
  retrouve la `cdc_section` réelle du CDC par code, puis agit sur SON `ordre`
  propre (fixe, copié du template à la création). L'`ordre` du step, lui,
  est la séquence d'exécution du plan -- librement réordonnable, découplé de
  l'ordre d'affichage des sections.
- Trois modes : COMPLET et SECTIONS s'exécutent identiquement (étapes
  incluses, triées par ordre du plan) -- la différence est purement
  d'intention/libellé. TRAME est seul à avoir une exécution réellement
  différente : un seul appel LLM produit un cadrage général + une ligne
  "titre — intention" par section (voir `buildTramePrompt`), stocké dans
  `cdc.body` (champ monolithique dormant depuis le passage aux sections,
  réemployé ici) -- aucune `cdc_section` n'est écrite, `inclus` est ignoré
  (TRAME sert de sommaire complet, pas un extrait).
- Exécuter un plan enregistre TOUJOURS un `cdc_event` (`type =
  PLAN_EXECUTED`) avec un instantané complet (sections, ordre, guidance,
  mode) avant de lancer la génération. Un plan reste modifiable ensuite ;
  l'historique reste exact grâce à cet instantané plutôt qu'une référence
  vivante au plan.
- `CdcSectionGenerationWorker` : les deux surcharges `@Async run(cdcId,
  ordre)` / `run(cdcId, ordre, guidance)` délèguent à un même `doRun` privé
  synchrone plutôt que de s'appeler entre elles -- un appel `this.run(...)`
  interne depuis une méthode `@Async` contournerait le proxy Spring
  (auto-invocation) et s'exécuterait de façon synchrone, bloquant l'appelant.
- Le frontend (`plan-steps-editor.tsx`) implémente le glisser-déposer avec
  l'API HTML5 native (`draggable`, `onDragStart/Over/Drop`), sans
  bibliothèque : cohérent avec "pas de dépendance en plus de shadcn".

## Structures (templates, `com.eb.cdcrag.generation.*Template*`)
- Appelées "Structures" dans l'UI (renommé pour ne pas les confondre avec
  les "modèles de mise en forme", voir plus bas) ; le code garde
  "Template"/"template" partout (`TemplateService`, `TemplateController`,
  `CdcTemplateRepository`, `cdc_template`) -- seul le texte affiché a changé,
  pas les identifiants.
- CRUD complet (`TemplateService`/`TemplateController`) : création,
  modification des informations générales, duplication (copie template +
  sections), suppression -- avec deux garde-fous métier avant le `DELETE`
  SQL : la structure par défaut ne peut pas être supprimée (`POST /api/cdc`
  s'appuie dessus en l'absence de templateId/planId), et une structure
  encore référencée par un CDC ou un plan lève une
  `DataIntegrityViolationException` (pas de `ON DELETE CASCADE` sur ces FK,
  volontairement) traduite en 409.
- Suppression d'une section de template (`DELETE
  /api/templates/{id}/sections/{sectionId}`) : pas de renumérotation
  automatique de `ordre` après coup -- des trous dans la séquence sont sans
  conséquence (tri par `ordre`, pas de contrainte de contiguïté).

## Export (PDF / DOCX / Markdown)
- `GET /api/cdc/{id}/export?format=pdf|docx|md` (`ExportService`). Un seul
  modèle intermédiaire (`ExportDocument`, sections + blocs déjà parsés)
  construit une fois et rendu par `DocxExporter` (Apache POI) ou
  `PdfExporter` (Flying Saucer -- rendu HTML+CSS, sur OpenPDF). L'habillage
  (polices, couleurs, marges) vient d'un `FormatStyle`, résolu par
  `ExportService.resolveStyle` : le modèle de mise en forme associé au CDC,
  sinon celui marqué par défaut, sinon `FormatStyle.defaultStyle()` -- les
  deux renderers ne consomment que ce record, jamais l'origine des valeurs.
- `MarkdownBlocks` : parseur markdown volontairement minimal (titres,
  paragraphes, listes à puces/numérotées avec distinction `ordered`,
  tableaux GFM), pas de bibliothèque type CommonMark -- même logique que
  SectionDetector/ClauseExtractor, un petit parseur dédié à ce que le LLM
  produit réellement plutôt qu'un analyseur général. Segmente par ligne vide
  PUIS par changement de nature de ligne (texte -> puce -> tableau -> titre) :
  le LLM enchaîne souvent une phrase d'intro et une liste SANS ligne vide
  entre les deux ("Sont inclus :\n- item\n- item"), et une règle "toutes les
  lignes du bloc doivent être des puces" classerait tout en paragraphe et
  perdrait la mise en forme.
- Sommaire avec numéros de page réels dans LES DEUX formats :
  - DOCX : un vrai champ Word `TOC \o "1-2" \h \z \u` (raw OOXML via
    `CTR.addNewFldChar()`/`addNewInstrText()`), plus
    `document.getSettings().setUpdateFields()` pour que Word le recalcule
    tout seul à l'ouverture -- un champ TOC reste vide tant qu'il n'a pas
    été "mis à jour", ce réglage évite à l'utilisateur de le faire à la
    main (F9).
  - PDF : deux passes de rendu (`PdfExporter.probePagination` puis
    `renderFinal`). Chaque titre de section porte un `id="sec-N"` en HTML ;
    `ITextRenderer.findPagePositionsByID(...)` (après `layout()` +
    `getOutputDevice().setRoot(...)`/`.start(...)`, sans écrire de PDF) donne
    la page de chacun, et un marqueur `id="doc-end"` final donne le nombre
    total de pages. La deuxième passe reconstruit le même HTML avec le
    sommaire rempli des vrais numéros -- la pagination ne bouge pas entre
    les deux passes tant que le sommaire garde une largeur de colonne fixe
    pour les numéros.
- En-tête/pied de page (titre du document, "Page N / total") dessinés via
  un `PdfPageEventHelper` enregistré dans `PDFCreationListener.preOpen` :
  Flying Saucer ne supporte pas les en-têtes/pieds de page courants de CSS
  Paged Media (`@top-center` etc.), ce mécanisme bas niveau (dessin direct
  sur `PdfContentByte`) est la façon standard de les obtenir avec ce moteur.
- PDFBox reste réservé à la LECTURE des PDF (ingestion) -- l'ancien
  `PdfExporter` maison (dessin par curseur Y) a été remplacé par Flying
  Saucer, qui gère la pagination/les tableaux/les listes via de vraies
  règles CSS plutôt qu'à la main.

## Modèles de mise en forme (format_template)
- Un .docx fourni par le client, dont `TemplateStyleExtractor` lit les
  styles (police, taille/couleur/gras des styles Heading1/Heading2 -- l'ID
  de style Word, stable quelle que soit la langue de l'interface, PAS le
  nom affiché "Titre 1"/"Titre 2" ; marges de `sectPr/pgMar`) et les
  convertit en `FormatStyle`, sérialisé en JSON une seule fois à l'upload
  (`format_template.style_summary`) -- jamais ré-analysé à chaque export.
  Best-effort délibéré et documenté à l'utilisateur (page /mise-en-forme,
  bandeau du dialogue de téléversement) : un champ non détecté (le client
  n'a jamais utilisé les styles Heading natifs, par exemple) retombe sur la
  valeur par défaut de CE champ précis, jamais d'échec d'import global. Une
  page de garde arbitraire avec mise en page complexe n'est pas reproduite
  à l'identique -- seuls les styles le sont.
- Piège XMLBeans rencontré en testant l'extraction sur un docx à couleur de
  titre connue : `CTColor.getVal()` renvoie un `byte[3]` (RGB) pour une
  vraie couleur hex, pas une `String` -- un `.toString()` naïf dessus donne
  `"[B@..."` au lieu de la couleur. Toujours passer par un helper qui
  distingue `byte[]` (couleur réelle) de `String` (mot-clé "auto").
  `XWPFDefaultRunStyle.getRPr()` est protégé côté POI (pas d'accès à la
  police par défaut du document) : la police du style Heading1 sert de
  proxy pour "la police principale du document".
- `cdc.format_template_id` (nullable, `ON DELETE SET NULL`) associe un
  modèle à UN CDC précis, prioritaire sur `format_template.is_default`
  (un seul à la fois, `FormatTemplateService.setDefault` fait
  clear-puis-set dans une transaction -- même pattern que
  `cdc_template.is_default`). Contrairement aux structures, la suppression
  d'un modèle de mise en forme n'a pas de garde-fou : un CDC qui le
  référençait retombe simplement sur l'habillage par défaut au prochain
  export, jamais d'échec.

## Paramètres d'organisation (organisation_settings)
- Trois notions indépendantes pour un CDC exporté, à ne pas mélanger :
  `organisation_settings` définit le CONTENU de marque (nom, logo, mentions,
  gabarit de référence, pied de page) ; `format_template`/`FormatStyle`
  définit l'APPARENCE (polices, couleurs, marges) ; le template (`cdc_template`)
  définit les SECTIONS. `ExportDocument.Branding` (contenu) et `FormatStyle`
  (apparence) sont deux champs séparés passés indépendamment à
  `DocxExporter`/`PdfExporter` -- jamais fusionnés dans un seul record, pour
  que la distinction reste visible dans le code, pas seulement dans l'UI.
- Table singleton (une seule ligne, `id = 1` fixe, pas `GENERATED ALWAYS AS
  IDENTITY`) : `OrganisationSettingsRepository.save` fait un `INSERT ... ON
  CONFLICT (id) DO UPDATE` plutôt qu'une contrainte SQL dédiée -- cohérent
  avec le reste du projet (`cdc_template.is_default`/`format_template.is_default`
  n'ont pas non plus de contrainte DB, juste une invariance applicative) et
  il n'y a qu'un seul appelant en pratique (`OrganisationSettingsService`).
  `OrganisationSettingsService.get()` ne renvoie jamais absent
  (`OrganisationSettings.empty()` en repli) : un export ne doit jamais
  échouer faute de configuration -- chaque valeur non renseignée est
  simplement omise du rendu (pas de "null" affiché), voir `ExportDocument.Branding.hasLogo()`/`hasContact()`
  et les gardes `hasText(...)` dans `DocxExporter`/`PdfExporter`.
- Logo stocké en base (`BYTEA` + `content_type`), jamais chargé avec le
  reste des paramètres (`OrganisationSettingsRepository.find()` ne
  sélectionne que `logo IS NOT NULL AS has_logo` ; les octets viennent d'un
  second appel, `findLogo()`) -- même séparation que
  `format_template`/`docx_data`. Limité à PNG/JPG, 2 Mo, validé dans
  `OrganisationSettingsService.updateLogo`.
- `ReferenceTemplate.render(gabarit, annee, numero, type)` (package
  `export`) substitue `{annee}`, `{numero}`/`{numero:0+}` (le nombre de
  zéros fixe la largeur du remplissage) et `{type}` par regex --
  `{numero}` est l'`id` du CDC en base, PAS un compteur remis à zéro chaque
  année (pas de table de séquence dédiée, plus simple et toujours unique) ;
  documenté ainsi sur la page `/parametres`. Une variable inconnue ou une
  syntaxe invalide reste telle quelle dans le résultat plutôt que de faire
  échouer l'export.
- Page de garde ancrée en bas de page (mention de confidentialité) : dans
  les deux formats, la même technique -- un bloc de hauteur FIXE réservé
  pour la mention, le reste de la page de garde ayant une hauteur MINIMALE
  égale à (hauteur utile de la page moins cette réserve), pour que la
  mention atterrisse au même endroit quel que soit le contenu au-dessus
  (logo, organisation, contact -- tous optionnels).
  - DOCX : un tableau à deux lignes SANS bordures (`XWPFTable.removeBorders()`)
    plutôt que des paragraphes de niveau document -- Word n'a pas de
    positionnement absolu, mais une ligne de tableau en `TableRowHeightRule.AT_LEAST`
    est padée par Word jusqu'à la hauteur demandée même si son contenu est
    plus court, ce qui pousse la seconde ligne (`EXACT`, `vAlign = BOTTOM`)
    au bas de la page.
  - PDF : `.cover { position: relative; height: <hauteur utile>pt; }` +
    `.confidentialite { position: absolute; bottom: 0; }` -- Flying Saucer
    supporte ce sous-ensemble de CSS position simple.
  - Hauteur de page A4 codée en dur (`16838` twips / `841.9` pt) dans
    chaque exporteur -- seules les marges viennent de `FormatStyle`.
- Logo embarqué (pas de placeholder) dès qu'un logo est configuré :
  - DOCX : `XWPFRun.addPicture(InputStream, PictureType, filename, widthEmu,
    heightEmu)` -- dimensions lues via `ImageIO.read` puis mises à l'échelle
    pour tenir dans une boîte de 140x80pt SANS jamais agrandir un petit
    fichier (`Math.min(1.0, ...)`), converties en EMU via `Units.toEMU(pt)`.
  - PDF : Flying Saucer supporte nativement les URI `data:` dans `<img
    src="data:image/png;base64,...">` -- vérifié empiriquement avant
    d'écrire le code de production (pas de `UserAgentCallback` custom
    nécessaire), voir `PdfExporter.buildHtml`.
  - Logo illisible/corrompu : on retombe sur le placeholder en pointillés
    plutôt que faire échouer tout l'export (`catch (IOException |
    InvalidFormatException)` autour de la lecture, DocxExporter uniquement --
    PdfExporter n'a pas besoin de décoder l'image, juste de la ré-encoder en
    base64).

## Relecture : preuve chiffrée des écarts
- Le JSON de relecture (`review.findings`) a deux parties d'origine
  différente : `resume`/`constats` sont rédigés par le LLM (inchangé) ;
  `echantillon`/`ecarts` sont ajoutés PAR-DESSUS après coup par
  `ReviewWorker.withEvidence`, directement depuis les objets Java
  (`DeviationChecker.Deviation`, `NormRepository.Norms`) -- le LLM ne les
  voit jamais et ne peut donc pas les altérer en les rédigeant. Cohérent
  avec la règle déjà en place : le Reviewer calcule, le LLM rédige.
  Champs absents (`echantillon`/`ecarts` undefined) sur les relectures
  faites avant cet ajout -- le frontend doit rester tolérant (`?.`).
- Le frontend associe un constat "écart de norme" à son `Ecart` par
  correspondance de texte (`champ` cherché dans `titre`/`explication`, voir
  `findEcart` dans `review-dialog.tsx`) : le JSON ne relie pas les deux
  explicitement, mais le vocabulaire des `champ` (3 valeurs fixes) est celui
  injecté dans le prompt donné au LLM, donc présent dans sa prose.

## Corpus synthétique
- tools/generate_corpus.py : génération templée déterministe (graine fixe),
  PAS d'appel LLM -- corpus reproductible pour les benchmarks.
- corpus/manifest.csv est la vérité terrain : toute valeur contractuelle
  générée doit y apparaître.
- Distribution des délais centrée, jamais uniforme (sinon aucune norme
  n'est détectable).
- tools/render_corpus.py convertit 1/3 en PDF, 1/3 en DOCX, 1/3 reste .txt.
- Les 6 CDC de uploads/ sont rédigés à la main : c'est le seul vrai test de
  l'extraction, ne pas les modifier pour faire passer un test.

## Frontend (web/)
- Next.js (App Router) + TypeScript + Tailwind v4 + shadcn/ui, dans `web/`.
  Tous les écrans en composants client (`"use client"`) — fetch manuel +
  `useState`/`useEffect`, pas de TanStack Query/SWR, pas de bibliothèque de
  composants en plus de shadcn. `react-markdown` + `remark-gfm` pour le
  rendu markdown (tableaux). `next-themes` pour le thème clair/sombre (seule
  exception à "pas de dépendance supplémentaire" : éviter le flash de
  mauvais thème à la main est trop fragile pour le faire soi-même).
- Modale vs page : un formulaire qui tient en un ou deux champs (import de
  document, téléversement d'un modèle de mise en forme, confirmations) reste
  une modale ; tout le reste est une page à part entière (`/plans/nouveau`,
  `/plans/[id]`, `/structures/nouveau`, `/structures/[id]`, `/cdc/nouveau`,
  `/parametres`)
  avec, si le formulaire est long, un pied de page `sticky bottom-0` pour
  les actions/estimations (voir `plan-form.tsx`). Chaque page de liste et de
  création affiche une phrase d'intro expliquant à quoi elle sert (visible
  dès le premier chargement, pas seulement au survol).
- Piège Radix `Select` rencontré deux fois dans cette session : passer
  `value={x ? String(x) : undefined}` fait osciller le composant entre
  "non contrôlé" (`undefined`) et "contrôlé" dès que `x` devient défini, et
  Radix peut alors déclencher un `onValueChange("")` interne au moment de la
  transition -- `Number("")` vaut `0` en JS, ce qui corrompt silencieusement
  l'état (aucune exception, juste une valeur qui ne correspond plus à aucun
  `SelectItem`). Toujours garder le `Select` contrôlé dès le premier rendu
  (`value={x !== null ? String(x) : ""}`, jamais `undefined`) et ignorer les
  chaînes vides dans `onValueChange`.
- Types de CDC (`cdc.cdc_type`) : champ texte libre, jamais une liste figée
  -- `GET /api/cdc/types` (`CdcRepository.findDistinctTypes`, `UNION` sur
  `cdc` et `document`) donne les valeurs réellement présentes en base.
  `lib/use-cdc-types.ts` expose deux hooks distincts, pas un seul : un
  FILTRE (`useCdcTypes`, liste CDC) ne doit montrer que ce qui existe
  vraiment (une catégorie vide serait trompeuse) ; un sélecteur de CRÉATION
  (`useCdcTypeSuggestions`, formulaires CDC/structure/plan/import) complète
  ces valeurs des types courants (`CDC_TYPES`) pour rester utilisable avant
  le tout premier CDC d'un type donné. `CDC_TYPES`/`typeLabel` (`lib/types.ts`)
  ne servent plus qu'à un habillage cosmétique (mise en forme d'un type
  connu) -- jamais à déterminer quelles valeurs sont sélectionnables.
- Le backend Spring n'a pas de CORS configuré : `next.config.ts` proxifie
  `/api/*` vers `BACKEND_URL` (défaut `localhost:8080`, voir
  `.env.local.example`). Le code frontend n'appelle donc que des chemins
  relatifs `/api/...`, jamais l'origine Spring directement.
- `src/lib/types.ts` doit rester le miroir exact des records Java exposés
  par `com.eb.cdcrag.generation.*`, `.review.*` et `.ingestion.*` (Jackson
  sérialise les composants de record tels quels, en camelCase) : toute
  modification d'un record côté backend (champ ajouté/retiré) doit être
  répercutée ici.
- `SectionPanel` ne resynchronise pas son état local (édition en cours) via
  un `useEffect` sur les props : le parent le remonte avec
  `key={section.ordre}` au changement de section. `draft` est dérivé de
  `section.body` seulement au moment d'entrer en édition (pas à l'initialisation
  du state), pour rester à jour si le contenu a changé pendant l'affichage.
- Statut CDC affiché = phase dérivée (`cdcPhase()` dans `lib/types.ts`), pas
  le `status` brut (DRAFT/APPROVED) qui ne distingue pas où on en est
  réellement : Généré / En relecture / Partiellement validé / Validé, à
  partir de l'avancement des sections + la relecture la plus récente
  (`reviewStatus`, exposé par `GET /api/cdc` et `GET /api/cdc/{id}`). "Temps
  dans le statut actuel" reste une approximation : depuis `approvedAt` si
  validé, sinon depuis `createdAt` (le `status` brut DRAFT n'a pas de
  sous-transition trackée en base).
- Palette neutre + une seule couleur d'accent (bleu-ardoise, teinte oklch 255,
  identique en clair et en sombre -- seule la clarté change) ; `destructive`
  (rouge) reste l'exception sémantique pour les échecs. Pas d'ombres
  (`shadow-*` retiré des composants shadcn générés qui en ajoutent par
  défaut, ex. `select.tsx`), pas de dégradés. Thème clair par défaut
  (`defaultTheme="light"`, `enableSystem={false}` dans `ThemeProvider` --
  un CDC est un document, il se lit sur blanc), sélecteur clair/sombre dans
  `ThemeToggle` en bas de la sidebar.
- Icônes lucide sur les actions principales (Nouveau CDC, Nouveau plan,
  Valider, Régénérer, Dupliquer, Importer, Lancer une relecture...) pour que les
  boutons se repèrent sans les chercher ; les statuts (CDC et section)
  restent une pastille + texte, jamais une icône (`status-badge.tsx`).
  Boutons : bordure visible (`border-input`, plus soutenue que `border`) et
  remplissage généreux (`size="default"` = h-9/px-3.5, `size="sm"` = h-8/px-3
  dans `button.tsx`) -- densité oui, mais pas au prix de rendre les actions
  invisibles.
- App shell fixe : `AppSidebar` (`app-sidebar.tsx`) est monté une fois dans
  `layout.tsx`, pas par page -- ~220px, nom de produit générique fixe
  (`PRODUCT_NAME` dans `app-sidebar.tsx`) + nom d'organisation configurable
  via `NEXT_PUBLIC_ORG_NAME` (absent : la ligne ne s'affiche pas du tout,
  jamais de placeholder inventé). Les cinq entrées de nav (Cahiers des
  charges, Plans, Corpus, Structures, Mise en forme) sont toutes de vrais
  liens. Les pages n'ont pas de conteneur centré (`mx-auto max-w-*`) :
  pleine largeur à côté de la sidebar.
- Aucune référence à une organisation fictive dans l'UI elle-même (chrome,
  libellés, nom du template par défaut en base -- "Standard SI", pas
  "Standard SI — Groupe EB"). Le CONTENU généré (sections rédigées par le
  LLM) continue de mentionner "Groupe EB" : c'est hérité du corpus
  synthétique (`tools/generate_corpus.py`), qui construit son texte autour
  de cette identité fictive depuis le début du projet. Le nettoyer
  impliquerait de régénérer tout le corpus et de réingérer -- un chantier
  séparé, pas fait ici sans le demander explicitement.
- Police : `next/font/google` Inter, avec `variable: "--font-sans"` --
  ce nom doit correspondre exactement au token que `globals.css` référence
  (`@theme inline { --font-sans: var(--font-sans); }`). Le scaffold shadcn
  initial nommait la variable Geist `--font-geist-sans`, qui ne correspondait
  à rien : la police n'était donc jamais réellement appliquée.
- Tableaux denses (liste CDC, corpus, structures) : pas de conteneur arrondi,
  `<table>` brut pleine largeur (avec `overflow-x-auto` pour les petits
  écrans), lignes de 44px (`h-11`), séparateurs 1px (`border-b border-border`),
  colonne Type en minuscules et gris discret, colonne Avancement en barre de
  progression + compteur `tabular-nums`. Dates relatives via
  `lib/format.ts` (`Intl.RelativeTimeFormat`, aucune dépendance).
- `/parametres` (organisation_settings) : l'aperçu de page de garde reflète
  les valeurs SAISIES (état du formulaire), pas la dernière version
  enregistrée -- pas de debounce/appel serveur, juste un composant
  `CoverPreview` qui relit le même state React que les champs. Reproduit la
  logique de `ReferenceTemplate.java` côté client
  (`renderReferenceTemplate` dans `lib/types.ts`) pour prévisualiser la
  référence sans appel réseau ; le rendu réel à l'export reste calculé côté
  serveur, cette copie ne sert qu'à l'aperçu. Logo téléversé/supprimé
  immédiatement (pas différé à "Enregistrer", contrairement aux champs
  texte) : deux endpoints séparés (`POST`/`DELETE /api/organisation-settings/logo`)
  cohérent avec le fait que le logo est stocké et lu séparément côté
  backend (voir CLAUDE.md, section Paramètres d'organisation).
- Lancer : `cd web && npm run dev` (port 3000), backend Spring déjà démarré
  sur 8080.

## Avant de terminer
- `./mvnw -q compile` (ou `test` si des tests existent) après toute
  modification Java.
- Après régénération du corpus : purger les documents synthétiques en base,
  réingérer, et vérifier ClauseExtractor contre corpus/manifest.csv.
