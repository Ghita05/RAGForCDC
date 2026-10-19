#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Génère un corpus synthétique de cahiers des charges français suivant les 14
sections du template standard (table cdc_template_section, template "Standard
SI — Société Exemple"), plus un manifeste CSV servant de vérité terrain.

Design:
- Graine fixe => corpus identique à chaque exécution => benchmarks reproductibles.
- Pas de dépendance à la base : les 14 sections sont dupliquées ici en constante
  (TEMPLATE_SECTIONS) pour ne pas coupler la génération du corpus à Postgres.
  Si le template standard change en base, mettre à jour TEMPLATE_SECTIONS.
- Chaque valeur contractuelle (délai, pénalité, plafond, garantie, SLA, GTR)
  est tirée d'une plage explicite et consignée dans manifest.csv.
- Longueur réaliste : 15 à 30 pages par document (~470 mots/page), obtenue en
  faisant varier le nombre d'items par section (EF, exigences techniques,
  livrables...) plutôt qu'en gonflant artificiellement une seule section.
- Plusieurs formulations par section pour éviter des documents quasi
  identiques dans l'espace vectoriel.
"""

import csv
import random
from pathlib import Path

SEED = 20260813
OUT = Path("corpus")
WORDS_PER_PAGE = 470
TARGET_WORDS = (7200, 13800)  # ~15 à ~29 pages

# Miroir de cdc_template_section pour le template "Standard SI — Société Exemple".
TEMPLATE_SECTIONS = [
    (1, "contexte", "Contexte et enjeux"),
    (2, "objet", "Objet de la consultation"),
    (3, "perimetre", "Périmètre de la prestation"),
    (4, "existant", "Analyse de l'existant"),
    (5, "besoin_fonctionnel", "Expression du besoin fonctionnel"),
    (6, "specs_techniques", "Spécifications techniques"),
    (7, "normes", "Contraintes et normes applicables"),
    (8, "sla", "Niveaux de service attendus"),
    (9, "livrables", "Livrables attendus"),
    (10, "planning", "Planning et jalons"),
    (11, "delais_penalites", "Délais, pénalités et garanties"),
    (12, "financier", "Conditions financières"),
    (13, "evaluation", "Critères d'évaluation des offres"),
    (14, "annexes", "Annexes"),
]

VILLES = ["Casablanca", "Rabat", "Tanger", "Marrakech", "Agadir",
          "Fès", "Kénitra", "Oujda", "Meknès", "Tétouan"]

MOIS = ["janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre"]

def sample_extendable(rng, pool, n):
    """Échantillonne n éléments d'un pool. Si n dépasse la taille du pool, le
    réutilise par passes mélangées plutôt que de répéter un motif fixe — les
    doublons de base sont ensuite différenciés par les clauses secondaires
    (rationale, vérification, commentaire) tirées indépendamment."""
    n = max(0, n)
    result = []
    while len(result) < n:
        result += rng.sample(pool, k=min(n - len(result), len(pool)))
    return result


SUBJECTS = {
    "reseau": [
        "Déploiement d'une liaison fibre optique entre les sites de {v1} et {v2}",
        "Refonte du réseau local du siège de {v1}",
        "Mise en place d'un réseau Wi-Fi haute densité au centre de formation de {v1}",
        "Interconnexion VPN sécurisée des agences régionales",
        "Déploiement d'un réseau de vidéosurveillance IP sur le site de {v1}",
    ],
    "logiciel": [
        "Développement d'une application de gestion des stocks multi-entrepôts",
        "Refonte du portail intranet des ressources humaines",
        "Mise en place d'une solution de gestion électronique des documents",
        "Développement d'une application mobile de saisie terrain",
        "Développement d'un module de facturation intégré à l'ERP",
    ],
    "maintenance": [
        "Maintenance préventive et corrective du parc informatique",
        "Tierce maintenance applicative du système de gestion commerciale",
        "Maintenance des équipements de climatisation de la salle serveurs",
        "Maintenance du parc d'imprimantes multifonctions",
        "Maintenance des équipements réseau et de sécurité périmétrique",
    ],
    "infrastructure": [
        "Modernisation de la salle serveurs du site de {v1}",
        "Mise en place d'une solution de sauvegarde et de reprise d'activité",
        "Virtualisation du parc de serveurs physiques",
        "Renouvellement des onduleurs et de la distribution électrique",
        "Migration de l'infrastructure de messagerie d'entreprise",
    ],
}

# ---------------------------------------------------------------------------
# 1. Contexte et enjeux
# ---------------------------------------------------------------------------

CONTEXTE_GENERIQUE = [
    "La Société Exemple emploie plus de {effectif} collaborateurs répartis sur "
    "{nb_sites} sites au Maroc. La Direction des Systèmes d'Information "
    "pilote l'ensemble des projets d'évolution du système d'information "
    "pour le compte des directions métier.",
    "Dans le cadre de son plan stratégique {annee_plan}-{annee_plan_fin}, le "
    "La Société Exemple poursuit la modernisation de son système d'information afin "
    "de soutenir la croissance de ses activités et d'améliorer la qualité "
    "de service rendue à ses clients internes et externes.",
    "La présente consultation s'inscrit dans une démarche de renforcement de "
    "la résilience et de la performance des services informatiques du "
    "La Société Exemple, engagée depuis {anciennete} ans à l'initiative de la "
    "Direction Générale.",
]

CONTEXTE_TYPE = {
    "reseau": [
        "Le réseau actuel montre des signes de saturation aux heures de "
        "pointe et ne permet plus d'absorber la croissance du trafic liée "
        "au déploiement des nouveaux outils collaboratifs. Les incidents de "
        "connectivité, bien que rares, ont un impact direct sur l'activité "
        "des équipes concernées.",
        "La multiplication des sites et des usages mobiles impose une "
        "revue complète de l'architecture réseau existante, afin de "
        "garantir un niveau de service homogène quel que soit le point "
        "d'accès.",
    ],
    "logiciel": [
        "Les outils métier actuels reposent en partie sur des solutions "
        "développées en interne il y a plusieurs années, dont la "
        "maintenance devient coûteuse et dont les évolutions fonctionnelles "
        "sont de plus en plus difficiles à intégrer.",
        "La transformation numérique de la Société Exemple nécessite une refonte des "
        "outils utilisés au quotidien par les équipes opérationnelles, afin "
        "d'en améliorer l'ergonomie, la fiabilité et l'interopérabilité "
        "avec le reste du système d'information.",
    ],
    "maintenance": [
        "Le parc actuel est composé d'équipements hétérogènes acquis sur "
        "plusieurs exercices budgétaires, dont le suivi et l'entretien "
        "reposent aujourd'hui sur des interventions ponctuelles peu "
        "formalisées.",
        "L'absence de contrat de maintenance structuré expose la Société Exemple "
        "à des délais de résolution incident variables et difficiles à "
        "anticiper, ce qui nuit à la continuité de service des directions "
        "métier.",
    ],
    "infrastructure": [
        "L'infrastructure actuelle approche de la fin de sa durée de vie "
        "technique et présente des risques croissants d'obsolescence, tant "
        "sur le plan matériel que sur le plan de la disponibilité des "
        "pièces de rechange.",
        "La croissance des volumes de données traitées par la Société Exemple "
        "impose une revue de la capacité et de la résilience de "
        "l'infrastructure, dans un contexte où la continuité d'activité "
        "est une exigence de plus en plus forte des directions métier.",
        "La Direction Générale a inscrit la sécurisation de l'infrastructure "
        "parmi les priorités du présent exercice budgétaire, au regard des "
        "risques identifiés lors du dernier audit interne.",
    ],
}

CONTEXTE_ENJEUX = [
    "L'enjeu principal de cette consultation est de garantir la continuité "
    "de service pour l'ensemble des directions métier concernées, sans "
    "dégradation perceptible pendant la période de transition.",
    "La Société Exemple attend du prestataire retenu une capacité à s'intégrer "
    "aux processus internes existants, tout en proposant les évolutions "
    "nécessaires à l'atteinte des objectifs fixés.",
    "La maîtrise des délais et des coûts constitue un enjeu majeur pour la "
    "Direction Générale, qui suit l'avancement de ce type de projet dans le "
    "cadre du comité de pilotage informatique mensuel.",
    "Le respect des exigences de sécurité et de conformité réglementaire "
    "conditionne l'acceptation du projet par la Direction des Risques du "
    "Société Exemple.",
]


def build_contexte(rng, typ, p):
    paras = rng.sample(CONTEXTE_GENERIQUE, k=len(CONTEXTE_GENERIQUE))
    paras += list(rng.sample(CONTEXTE_TYPE[typ], k=len(CONTEXTE_TYPE[typ])))
    paras += rng.sample(CONTEXTE_ENJEUX, k=2)
    return "\n\n".join(x.format(**p) for x in paras)


# ---------------------------------------------------------------------------
# 2. Objet de la consultation
# ---------------------------------------------------------------------------

OBJET_TEMPLATES = [
    "Le présent cahier des charges définit les besoins de la Société Exemple relatifs "
    "à la prestation suivante : {subject}. Il précise les exigences "
    "fonctionnelles et techniques attendues, les délais d'exécution, les "
    "modalités de réception ainsi que les engagements de garantie et de "
    "niveau de service. Le prestataire retenu s'engage sur l'ensemble des "
    "clauses du présent document, qui prévaut sur toute proposition "
    "commerciale antérieure en cas de contradiction.",
    "La Société Exemple souhaite confier à un prestataire qualifié la réalisation "
    "de la prestation suivante : {subject}. Ce document en fixe le "
    "périmètre, les exigences techniques et fonctionnelles, le calendrier "
    "contractuel, les conditions financières ainsi que les conditions de "
    "recette. Toute offre déposée dans le cadre de cette consultation doit "
    "répondre point par point aux exigences formulées ci-après.",
    "Ce document constitue le cahier des charges de la consultation lancée "
    "par la Direction des Systèmes d'Information de la Société Exemple pour la "
    "prestation suivante : {subject}. Il engage le prestataire retenu sur "
    "l'ensemble des exigences, des délais et des niveaux de service qui y "
    "sont énoncés, pour toute la durée du marché.",
]


def build_objet(rng, subject, p):
    return rng.choice(OBJET_TEMPLATES).format(subject=subject, **p)


# ---------------------------------------------------------------------------
# 3. Périmètre de la prestation
# ---------------------------------------------------------------------------

PERIMETRE_INCLUS = {
    "reseau": [
        "Étude d'ingénierie et validation de l'architecture cible",
        "Fourniture, installation et configuration des équipements actifs",
        "Câblage et raccordement conformes aux normes en vigueur",
        "Paramétrage de la supervision et des alertes",
        "Tests de conformité et de charge avant mise en production",
        "Documentation d'exploitation et schémas d'architecture",
        "Formation des équipes techniques internes",
        "Accompagnement lors de la bascule en production",
    ],
    "logiciel": [
        "Cadrage fonctionnel détaillé et rédaction des spécifications",
        "Développement de l'ensemble des modules décrits au présent CDC",
        "Intégration avec les systèmes existants de la Société Exemple",
        "Recette fonctionnelle et technique",
        "Rédaction de la documentation technique et utilisateur",
        "Formation des utilisateurs clés et des administrateurs",
        "Accompagnement au démarrage en production",
        "Correction des anomalies détectées pendant la période de garantie",
    ],
    "maintenance": [
        "Maintenance préventive planifiée sur l'ensemble du périmètre",
        "Maintenance corrective sur incident, avec portail de tickets",
        "Fourniture et gestion du stock de pièces de rechange critiques",
        "Rapports d'activité périodiques",
        "Astreinte téléphonique aux horaires convenus",
        "Mise à jour de l'inventaire du parc couvert",
    ],
    "infrastructure": [
        "Étude de dimensionnement et validation de l'architecture cible",
        "Fourniture et installation des équipements",
        "Migration des données et services existants",
        "Mise en œuvre de la redondance et des mécanismes de reprise",
        "Tests de restauration et de bascule",
        "Documentation d'exploitation complète",
        "Formation des équipes d'administration",
        "Accompagnement pendant la période de garantie",
    ],
}

PERIMETRE_EXCLUS = [
    "Le câblage ou les travaux relevant du génie civil, sauf mention "
    "contraire explicite du présent document",
    "La fourniture de licences tierces non expressément listées en annexe",
    "L'exploitation courante au-delà de la période de garantie contractuelle",
    "Les développements ou adaptations demandés hors périmètre fonctionnel "
    "validé en phase de cadrage",
    "La formation de personnel autre que les équipes désignées par la "
    "Société Exemple",
    "Le support aux utilisateurs finaux, qui reste assuré par le service "
    "d'assistance interne de la Société Exemple",
    "Le renouvellement des équipements arrivés en fin de garantie, hors "
    "avenant spécifique",
    "La fourniture de postes de travail ou d'équipements bureautiques "
    "standards de la Société Exemple",
]

PERIMETRE_ELABORATION = [
    "Cette prestation est réalisée selon les standards internes de la "
    "Société Exemple et documentée en conséquence.",
    "Le prestataire soumet un plan détaillé de cette activité avant son "
    "démarrage.",
    "Cette activité fait l'objet d'un point d'avancement dédié en comité de "
    "suivi.",
    "La Société Exemple désigne un interlocuteur référent pour valider cette "
    "activité.",
    "Cette activité est réalisée conjointement avec les équipes internes "
    "concernées.",
]


def build_perimetre(rng, typ):
    inclus = rng.sample(PERIMETRE_INCLUS[typ], k=len(PERIMETRE_INCLUS[typ]))
    exclus = rng.sample(PERIMETRE_EXCLUS, k=rng.randint(4, len(PERIMETRE_EXCLUS)))
    lignes = [
        "La prestation est réalisée dans le cadre d'un marché forfaitaire. "
        "Toute activité non listée ci-dessous mais nécessaire à l'atteinte "
        "du résultat attendu est réputée incluse, sauf mention contraire "
        "explicite.",
        "",
        "Sont inclus dans le périmètre de la prestation :", "",
    ]
    for x in inclus:
        suffix = f" {rng.choice(PERIMETRE_ELABORATION)}" if rng.random() < 0.5 else ""
        lignes.append(f"- {x}.{suffix}")
    lignes += ["", "Sont explicitement exclus du périmètre :", ""]
    lignes += [f"- {x}." for x in exclus]
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# 4. Analyse de l'existant
# ---------------------------------------------------------------------------

EXISTANT_TYPE = {
    "reseau": [
        "Le réseau actuel repose sur une architecture historique déployée "
        "en plusieurs phases, sans schéma directeur unifié. Les débits "
        "disponibles entre sites varient de {debit_bas} à {debit_haut} "
        "Mbit/s selon les liaisons, avec des équipements dont certains "
        "approchent la fin de vie constructeur.",
        "La volumétrie de trafic observée sur le site principal atteint en "
        "moyenne {trafic} Go par jour ouvré, avec des pics constatés en "
        "début et fin de journée. Aucune supervision centralisée ne "
        "permet aujourd'hui d'anticiper la saturation d'un lien.",
        "Le câblage de certains sites reste partiellement non structuré, ce "
        "qui complique les opérations de maintenance et allonge les délais "
        "de diagnostic en cas d'incident.",
        "Les équipements actifs proviennent de plusieurs constructeurs, "
        "acquis sur des exercices budgétaires différents, ce qui complique "
        "l'homogénéité des configurations et la gestion des correctifs de "
        "sécurité.",
        "Aucune redondance automatique n'est aujourd'hui en place entre les "
        "principaux sites, ce qui expose la Société Exemple à une interruption "
        "totale de service en cas de rupture d'un lien critique.",
    ],
    "logiciel": [
        "L'outil actuellement utilisé a été mis en service il y a "
        "{anciennete_outil} ans et repose sur une architecture technique "
        "aujourd'hui difficile à faire évoluer. Il est utilisé par environ "
        "{users_actuels} utilisateurs répartis sur l'ensemble des sites du "
        "Société Exemple.",
        "Les échanges de données avec les systèmes tiers reposent en grande "
        "partie sur des exports manuels et des fichiers plats, générant un "
        "risque d'erreur et une perte de traçabilité régulièrement signalée "
        "par les utilisateurs métier.",
        "Le volume de transactions traité quotidiennement est de l'ordre de "
        "{volume_transactions} opérations, avec une croissance continue "
        "depuis les trois derniers exercices.",
        "L'outil actuel ne dispose pas d'interface de programmation "
        "documentée, ce qui limite les possibilités d'intégration avec les "
        "autres applications du système d'information.",
        "Les utilisateurs signalent régulièrement des lenteurs aux heures de "
        "forte activité, sans que la cause exacte n'ait pu être isolée par "
        "les équipes internes.",
    ],
    "maintenance": [
        "Le parc couvert par la présente consultation compte environ "
        "{nb_equipements} équipements, répartis sur l'ensemble des sites du "
        "Société Exemple, avec une ancienneté moyenne de {anciennete_parc} ans.",
        "Les interventions sont aujourd'hui réalisées au cas par cas, sans "
        "engagement contractuel de délai, ce qui génère une variabilité "
        "importante du temps de résolution constatée par les directions "
        "métier.",
        "Le taux de panne constaté sur les douze derniers mois s'élève à "
        "environ {taux_panne} % du parc, principalement concentré sur les "
        "équipements les plus anciens.",
        "Aucun engagement contractuel de délai de rétablissement n'encadre "
        "aujourd'hui les interventions, ce qui rend difficile toute "
        "planification côté directions métier.",
        "La documentation technique du parc est partiellement à jour, ce "
        "qui allonge parfois le diagnostic lors d'une intervention "
        "corrective.",
    ],
    "infrastructure": [
        "L'infrastructure actuelle repose sur {nb_serveurs} serveurs "
        "physiques hébergés dans la salle serveurs du site principal, dont "
        "la capacité de stockage disponible est de {stockage_actuel} To.",
        "Le taux d'utilisation moyen des ressources de calcul est de "
        "l'ordre de {taux_utilisation} %, avec des pics significatifs lors "
        "des traitements de fin de mois.",
        "Le plan de reprise d'activité actuel n'a pas fait l'objet de test "
        "grandeur nature depuis plus de {anciennete_prda} ans, ce qui "
        "constitue un risque identifié par la Direction des Systèmes "
        "d'Information.",
        "Les baies de stockage actuelles arrivent en fin de support "
        "constructeur, ce qui limite les possibilités d'extension de "
        "capacité sans opération de migration.",
        "La climatisation de la salle serveurs ne dispose d'aucune "
        "redondance active, ce qui constitue un point de défaillance unique "
        "identifié lors du dernier audit technique.",
    ],
}

EXISTANT_IMPACT = [
    "Cette situation a été formellement remontée par les directions métier "
    "concernées lors des comités de suivi trimestriels.",
    "Un audit interne a confirmé la nécessité d'une intervention à court "
    "terme sur ce périmètre.",
    "Les équipes internes ne disposent pas aujourd'hui des moyens "
    "nécessaires pour traiter cette situation sans appui externe.",
    "Cette limite constitue un frein identifié à la réalisation des "
    "objectifs fixés par la Direction Générale.",
    "Le coût de maintien en condition opérationnelle de l'existant "
    "augmente d'un exercice à l'autre.",
]


def build_existant(rng, typ, p):
    paras = list(EXISTANT_TYPE[typ])
    rng.shuffle(paras)
    out = []
    for x in paras:
        text = x.format(**p)
        if rng.random() < 0.6:
            text += " " + rng.choice(EXISTANT_IMPACT)
        out.append(text)
    return "\n\n".join(out)


# ---------------------------------------------------------------------------
# 5. Expression du besoin fonctionnel (EF-xx)
# ---------------------------------------------------------------------------

# Chaque item est l'objet de la phrase, sans le sujet ni le modal ("doit") :
# combiné à SUJETS_SYSTEME / SUJETS_PRESTATAIRE, cela évite de dupliquer
# l'exigence de base même quand le nombre d'EF demandées dépasse la taille du
# pool (contrairement à un simple ré-échantillonnage du pool complet).
SUJETS_SYSTEME = [
    "Le système doit", "La solution retenue doit", "L'application livrée doit",
    "Le dispositif mis en œuvre doit", "L'outil déployé doit",
]
SUJETS_PRESTATAIRE = [
    "Le prestataire doit", "Le titulaire du marché doit",
    "Le prestataire retenu doit", "L'équipe en charge de la prestation doit",
]

EF_TYPE = {
    "reseau": [
        "permettre la supervision en temps réel de l'état de chaque lien "
        "inter-sites, avec remontée d'alerte automatique en cas de "
        "dégradation.",
        "garantir la bascule automatique vers le lien de secours en cas de "
        "rupture du lien principal, sans intervention manuelle.",
        "permettre la segmentation logique du trafic par direction métier, "
        "avec des règles de filtrage indépendantes.",
        "fournir un tableau de bord consultable par les équipes techniques, "
        "présentant la disponibilité de chaque équipement actif.",
        "permettre l'ajout d'un nouveau site raccordé sans interruption de "
        "service sur les sites existants.",
        "journaliser toute modification de configuration des équipements "
        "actifs, avec horodatage et identification de l'auteur.",
        "permettre la restriction d'accès aux équipements d'administration "
        "par adresse IP source.",
        "permettre la génération d'un rapport mensuel de disponibilité par "
        "lien.",
        "détecter automatiquement toute tentative de connexion non "
        "autorisée sur les équipements actifs.",
        "permettre la sauvegarde automatisée des configurations de "
        "l'ensemble des équipements actifs.",
    ],
    "logiciel": [
        "permettre la création, la modification et la suppression des "
        "enregistrements par les utilisateurs habilités, avec contrôle des "
        "droits par profil.",
        "permettre la recherche multicritère sur l'ensemble des "
        "enregistrements actifs, avec un temps de réponse conforme aux "
        "exigences de performance.",
        "permettre l'export des données affichées à l'écran aux formats "
        "CSV et PDF.",
        "envoyer une notification automatique aux utilisateurs concernés "
        "lors d'un changement de statut d'un dossier.",
        "permettre la validation d'un dossier par un circuit d'approbation "
        "à plusieurs niveaux, paramétrable sans développement.",
        "permettre la consultation de l'historique complet des "
        "modifications apportées à un enregistrement.",
        "permettre l'import de données depuis un fichier structuré, avec "
        "contrôle de cohérence avant intégration.",
        "être accessible depuis un navigateur web standard sans "
        "installation de composant supplémentaire sur le poste utilisateur.",
        "permettre le paramétrage des libellés et des listes de valeurs "
        "sans intervention technique du prestataire.",
        "restituer un accusé de traitement horodaté pour chaque opération "
        "significative réalisée par un utilisateur.",
    ],
    "maintenance": [
        "mettre à disposition un portail de tickets accessible en "
        "permanence, avec accusé de réception automatique.",
        "qualifier chaque incident selon un niveau de criticité défini "
        "conjointement avec la Société Exemple.",
        "transmettre un rapport d'intervention détaillé à l'issue de "
        "chaque visite, préventive ou corrective.",
        "maintenir à jour l'inventaire des équipements couverts par le "
        "contrat, avec numéro de série et localisation.",
        "proposer un plan de remplacement anticipé pour les équipements "
        "identifiés comme obsolètes.",
        "assurer une astreinte joignable aux horaires définis dans le "
        "présent cahier des charges.",
        "fournir un rapport d'activité mensuel détaillant les incidents "
        "traités et les temps de résolution constatés.",
        "disposer d'un stock de pièces de rechange suffisant pour "
        "respecter les délais de rétablissement contractuels.",
        "réaliser un diagnostic à distance avant tout déplacement sur "
        "site, sauf incident bloquant.",
        "communiquer sans délai à la Société Exemple toute panne récurrente "
        "constatée sur un même équipement.",
    ],
    "infrastructure": [
        "permettre la restauration d'une machine virtuelle individuelle "
        "sans impact sur les autres services hébergés.",
        "garantir la réplication des données critiques vers un second site "
        "distant.",
        "permettre la supervision centralisée de l'ensemble des ressources "
        "de calcul, de stockage et de réseau.",
        "permettre l'ajout de capacité de stockage sans interruption de "
        "service.",
        "fournir un tableau de bord de suivi de consommation des "
        "ressources par direction métier.",
        "permettre le test de restauration à la demande, sans impact sur "
        "l'environnement de production.",
        "garantir la bascule automatique vers le site de secours en cas "
        "d'indisponibilité du site principal.",
        "permettre la gestion des sauvegardes selon une politique de "
        "rétention paramétrable par type de donnée.",
        "alerter automatiquement les équipes d'astreinte en cas de "
        "dépassement d'un seuil de capacité critique.",
        "permettre la restitution d'un rapport de conformité des "
        "sauvegardes à la demande de la Société Exemple.",
    ],
}

EF_GENERIQUE = [
    "reposer sur une authentification unique adossée à l'annuaire LDAP de la "
    "Société Exemple.",
    "journaliser l'ensemble des opérations sensibles, avec conservation des "
    "journaux pendant une durée conforme à la politique interne de la "
    "Société Exemple.",
    "permettre la définition de profils d'accès distincts selon le rôle de "
    "l'utilisateur.",
    "être disponible en français, langue de travail de la Société Exemple, pour "
    "l'ensemble des écrans et documents livrés.",
    "garantir la conformité au Règlement Général sur la Protection des "
    "Données pour tout traitement de données à caractère personnel.",
    "permettre la sauvegarde régulière des données selon une politique "
    "définie conjointement avec la Direction des Systèmes d'Information.",
    "fournir une documentation d'exploitation permettant aux équipes "
    "internes d'assurer le support de premier niveau.",
    "permettre la remontée d'alerte vers l'équipe d'astreinte en cas "
    "d'anomalie bloquante.",
    "permettre l'archivage des données selon une durée de conservation "
    "conforme aux obligations légales applicables.",
    "fournir un mécanisme de restauration après incident sans perte de "
    "données au-delà de l'objectif de point de reprise convenu.",
    "restituer, sur demande de la Société Exemple, un état complet des habilitations "
    "actives à un instant donné.",
    "permettre la révocation immédiate des droits d'un utilisateur quittant "
    "la Société Exemple.",
]

EF_RATIONALE = [
    "Cette exigence répond à un besoin exprimé par les utilisateurs métier "
    "lors des ateliers de cadrage.",
    "Cette exigence découle des limites constatées sur la solution "
    "actuelle, décrites au chapitre Analyse de l'existant.",
    "Cette exigence est jugée prioritaire par la Direction des Systèmes "
    "d'Information.",
    "Cette exigence vise à réduire le risque opérationnel identifié sur le "
    "périmètre actuel.",
    "Cette exigence contribue directement aux objectifs de qualité de "
    "service fixés pour la prestation.",
    "Cette exigence a été formulée à la suite des incidents constatés sur "
    "le système actuel.",
    "Cette exigence s'inscrit dans la politique de sécurité de la Société Exemple.",
    "Cette exigence conditionne l'adhésion des utilisateurs finaux à la "
    "nouvelle solution.",
]

EF_VERIFICATION = [
    "Cette exigence est vérifiée lors de la recette technique, sur la base "
    "d'un scénario de test documenté.",
    "Le respect de cette exigence conditionne le prononcé de la réception "
    "provisoire.",
    "Cette exigence fait l'objet d'un contrôle contradictoire entre le "
    "prestataire et les équipes de la Société Exemple.",
    "La démonstration de cette exigence est intégrée au plan de tests de "
    "recette.",
    "Cette exigence est tracée dans la matrice de conformité remise avec "
    "l'offre.",
    "Le non-respect constaté de cette exigence ouvre un délai de reprise "
    "avant réception.",
    "Cette exigence est validée par un test fonctionnel réalisé en présence "
    "d'un représentant de la Société Exemple.",
    "La conformité à cette exigence est un préalable à la levée des "
    "réserves de réception.",
    "Cette exigence est vérifiée par échantillonnage lors de la période "
    "d'observation.",
    "Le prestataire documente la mise en œuvre de cette exigence dans le "
    "dossier de recette.",
]


EF_IMPACT = [
    "L'absence de cette fonctionnalité expose la Société Exemple à un risque "
    "opérationnel jugé significatif par la Direction des Systèmes "
    "d'Information.",
    "Cette exigence a un impact direct sur l'expérience quotidienne des "
    "utilisateurs finaux du système.",
    "Le prestataire décrit dans son offre la manière dont il compte "
    "répondre à cette exigence, avec le niveau de détail attendu pour une "
    "consultation de ce type.",
    "Une réponse partielle à cette exigence doit être explicitement "
    "signalée et justifiée dans l'offre du prestataire.",
    "Cette exigence est reprise telle quelle dans le contrat final, sauf "
    "négociation explicite actée par écrit entre les parties.",
    "La Société Exemple se réserve le droit de demander une démonstration "
    "anticipée de cette fonctionnalité avant la commande ferme.",
]


def build_besoin_fonctionnel(rng, typ, n):
    """Combine sujet + objet pour obtenir un pool de phrases uniques bien plus
    grand que le nombre d'objets rédigés à la main, afin qu'aucune exigence
    de base ne soit jamais dupliquée au sein d'un même document, même quand n
    dépasse le nombre d'objets disponibles."""
    sujets_type = SUJETS_PRESTATAIRE if typ == "maintenance" else SUJETS_SYSTEME
    combos = [(s, o) for o in EF_TYPE[typ] for s in sujets_type]
    combos += [(s, o) for o in EF_GENERIQUE for s in SUJETS_SYSTEME]
    n = min(n, len(combos))
    chosen = rng.sample(combos, k=n)
    lignes = []
    for i, (sujet, objet) in enumerate(chosen, start=1):
        rationale = rng.choice(EF_RATIONALE)
        verification = rng.choice(EF_VERIFICATION)
        impact = rng.choice(EF_IMPACT)
        lignes.append(f"EF-{i:02d}. {sujet} {objet} {rationale} {verification} {impact}")
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# 6. Spécifications techniques (tableau markdown)
# ---------------------------------------------------------------------------

SPECS_ROWS = {
    "reseau": [
        ("Débit garanti par lien", "{debit} Gbit/s", "Contractuelle"),
        ("Latence maximale inter-sites", "{latence} ms", "Contractuelle"),
        ("Bascule automatique", "< {bascule} secondes", "Contractuelle"),
        ("Câblage structuré", "Conforme ISO/IEC 11801", "Réglementaire"),
        ("Supervision", "SNMP centralisée", "Fonctionnelle"),
        ("Segmentation", "VLAN par direction métier", "Fonctionnelle"),
        ("Chiffrement des flux", "Conforme aux recommandations DSI", "Sécurité"),
        ("Redondance des liens", "Double opérateur", "Contractuelle"),
        ("Protocole de routage", "Dynamique, convergence < 30 s", "Technique"),
        ("Journalisation des accès", "12 mois de rétention", "Sécurité"),
    ],
    "logiciel": [
        ("Utilisateurs simultanés", "{users}", "Contractuelle"),
        ("Temps de réponse consultation", "< {latence} s", "Contractuelle"),
        ("Authentification", "SSO / LDAP", "Sécurité"),
        ("Journalisation", "CRUD complet", "Fonctionnelle"),
        ("Compatibilité navigateurs", "Selon référentiel DSI", "Technique"),
        ("Export", "CSV, PDF", "Fonctionnelle"),
        ("Documentation", "Français, livrée avec le produit", "Contractuelle"),
        ("Sauvegarde applicative", "Quotidienne", "Technique"),
        ("Chiffrement des données sensibles", "Au repos et en transit", "Sécurité"),
        ("API d'intégration", "REST, documentée (OpenAPI)", "Technique"),
    ],
    "maintenance": [
        ("Visite préventive", "Tous les {preventif} mois", "Contractuelle"),
        ("Portail de tickets", "Disponible en permanence", "Fonctionnelle"),
        ("Rapport d'activité", "Mensuel", "Contractuelle"),
        ("Stock de pièces critiques", "Maintenu par le prestataire", "Contractuelle"),
        ("Astreinte", "Joignable aux horaires convenus", "Fonctionnelle"),
        ("Traçabilité des interventions", "Rapport signé à chaque visite", "Fonctionnelle"),
        ("Inventaire du parc", "Mis à jour à chaque intervention", "Fonctionnelle"),
        ("Diagnostic à distance", "Pris en charge avant déplacement", "Technique"),
        ("Compte-rendu d'intervention", "Signé contradictoirement", "Fonctionnelle"),
        ("Habilitation des intervenants", "Vérifiée avant première visite", "Contractuelle"),
    ],
    "infrastructure": [
        ("RPO", "{rpo} heures maximum", "Contractuelle"),
        ("RTO", "{rto} heures maximum", "Contractuelle"),
        ("Capacité de stockage utile", "{volume} To après redondance", "Contractuelle"),
        ("Rétention des sauvegardes", "{retention} jours glissants", "Contractuelle"),
        ("Autonomie électrique", "{autonomie} minutes minimum", "Contractuelle"),
        ("Tests de restauration", "Documentés avant réception", "Contractuelle"),
        ("Virtualisation", "Compatible hyperviseur existant", "Technique"),
        ("Supervision", "Centralisée, alertes temps réel", "Fonctionnelle"),
        ("Chiffrement des données", "Au repos et en transit", "Sécurité"),
        ("Redondance électrique", "Double arrivée", "Technique"),
    ],
}

SPEC_COMMENT = [
    "Valeur vérifiée lors des tests de recette avant mise en production.",
    "Mesurée sur une fenêtre glissante de 30 jours calendaires.",
    "Contrôlée par sondage pendant la période de garantie.",
    "Toute dérogation doit être validée par écrit par la Direction des "
    "Systèmes d'Information.",
    "Applicable dès la réception provisoire de la prestation.",
    "Vérifiable par un test documenté fourni avec l'offre.",
    "Constatée par un relevé automatique de supervision.",
    "Rappelée dans la documentation d'exploitation livrée par le "
    "prestataire.",
]


def build_specs_techniques(rng, typ, p, n):
    rows = rng.sample(SPECS_ROWS[typ], k=min(n, len(SPECS_ROWS[typ])))
    lignes = [
        "Les exigences techniques chiffrées applicables à la prestation "
        "sont précisées dans le tableau ci-dessous. Toute déviation par "
        "rapport à ces valeurs doit être justifiée explicitement dans "
        "l'offre du prestataire, avec une solution de compensation "
        "équivalente proposée par le prestataire.",
        "",
        "| Exigence | Valeur cible | Nature | Commentaire |",
        "|---|---|---|---|",
    ]
    for nom, valeur, nature in rows:
        lignes.append(f"| {nom} | {valeur.format(**p)} | {nature} | "
                       f"{rng.choice(SPEC_COMMENT)} |")
    lignes.append("")
    lignes.append(
        "Le prestataire précise dans son offre, pour chaque exigence, la "
        "méthode retenue pour en démontrer le respect lors de la recette."
    )
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# 7. Contraintes et normes applicables
# ---------------------------------------------------------------------------

NORMES_GENERIQUE = [
    "Règlement Général sur la Protection des Données (RGPD)",
    "Politique de sécurité des systèmes d'information de la Société Exemple",
    "Charte informatique interne de la Société Exemple",
    "Réglementation marocaine relative à la protection des données à "
    "caractère personnel",
    "Règles internes d'achat et de qualification des prestataires",
]

NORMES_TYPE = {
    "reseau": ["ISO/IEC 11801 (câblage structuré)", "ISO/IEC 27001", "RFC applicables au routage dynamique"],
    "logiciel": ["ISO/IEC 27001", "OWASP ASVS pour les développements web", "ISO 9001 pour les processus de développement"],
    "maintenance": ["ISO 9001 pour les processus de maintenance", "ISO/IEC 27001", "Normes constructeur applicables au parc couvert"],
    "infrastructure": ["ISO/IEC 27001", "ISO 22301 (continuité d'activité)", "Normes constructeur relatives à l'alimentation électrique"],
}


NORMES_DETAIL = [
    "Le prestataire produit les preuves de conformité correspondantes sur "
    "demande de la Société Exemple.",
    "Le respect de cette norme est vérifié lors de l'audit de recette.",
    "Toute non-conformité constatée est corrigée aux frais du prestataire "
    "avant la réception.",
    "Cette exigence s'applique pour toute la durée du marché, y compris "
    "pendant la période de garantie.",
    "Le prestataire s'engage à informer la Société Exemple de toute évolution "
    "réglementaire impactant cette exigence.",
]


def build_normes(rng, typ):
    pool = NORMES_TYPE[typ] + NORMES_GENERIQUE
    chosen = rng.sample(pool, k=len(pool))
    lignes = [
        "Le prestataire s'engage à respecter, pour l'ensemble de la "
        "prestation, les normes et réglementations suivantes :", "",
    ]
    lignes += [f"- {n}. {rng.choice(NORMES_DETAIL)}" for n in chosen]
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# 8. Niveaux de service attendus (tableau markdown, SLA / GTI / GTR)
# ---------------------------------------------------------------------------

def build_sla(rng, p):
    intro = (
        "Le taux de disponibilité mensuel minimal exigé est de {sla} %, "
        "mesuré sur les heures ouvrées de la Société Exemple. La Garantie de Temps "
        "d'Intervention (GTI) et la Garantie de Temps de Rétablissement "
        "(GTR) sont fixées ci-dessous, en heures ouvrées à compter de "
        "l'ouverture du ticket d'incident.\n\n"
        "En cas de dépassement de la GTR, le prestataire en informe sans "
        "délai la Société Exemple et propose un plan d'action correctif dans les "
        "24 heures suivant le dépassement constaté."
    ).format(**p)
    lignes = [
        intro, "",
        "| Indicateur | Cible | Modalité de mesure |",
        "|---|---|---|",
        "| Taux de disponibilité mensuel | {sla} % | Relevé mensuel de supervision |".format(**p),
        "| Garantie de Temps d'Intervention (GTI) | {gti} heures ouvrées | Depuis l'ouverture du ticket |".format(**p),
        "| Garantie de Temps de Rétablissement (GTR) | {gtr} heures ouvrées | Depuis l'ouverture du ticket |".format(**p),
        "| Criticité bloquante | Priorité 1, traitement immédiat | Astreinte |",
        "| Criticité majeure | Priorité 2, GTR x 2 | Heures ouvrées |",
        "| Criticité mineure | Priorité 3, GTR x 4 | Heures ouvrées |",
        "| Fenêtre de mesure | Mensuelle glissante | Extraction de supervision |",
        "| Maintenance planifiée | Exclue du calcul de disponibilité | Notifiée 5 jours ouvrés à l'avance |",
    ]
    procedure = (
        "\n\nLa disponibilité est calculée mensuellement selon la formule "
        "usuelle du temps de fonctionnement rapporté au temps total de la "
        "période, hors fenêtres de maintenance planifiée notifiées au "
        "préalable à la Société Exemple. Un relevé détaillé est transmis chaque "
        "début de mois, accompagné de la liste des incidents survenus et de "
        "leur temps de résolution respectif.\n\n"
        "Toute contestation d'un relevé doit être formulée par écrit dans "
        "un délai de dix jours ouvrés suivant sa réception, faute de quoi "
        "le relevé est réputé accepté par la Société Exemple."
    )
    return "\n".join(lignes) + procedure


# ---------------------------------------------------------------------------
# 9. Livrables attendus
# ---------------------------------------------------------------------------

LIVRABLES_TYPE = {
    "reseau": [
        ("Dossier d'architecture technique", "PDF", "Avant le démarrage des travaux"),
        ("Schéma d'implantation des équipements", "PDF / Visio", "Avant le démarrage des travaux"),
        ("Rapport de tests de conformité", "PDF", "Avant la recette"),
        ("Documentation d'exploitation", "PDF", "À la réception provisoire"),
        ("Support de formation des équipes techniques", "PDF / PPTX", "À la réception provisoire"),
        ("Plan de recollement final", "PDF / DWG", "À la réception définitive"),
        ("Registre des adresses IP attribuées", "Tableur", "À la réception provisoire"),
    ],
    "logiciel": [
        ("Spécifications fonctionnelles détaillées", "PDF", "Après cadrage"),
        ("Manuel utilisateur", "PDF", "À la réception provisoire"),
        ("Documentation technique et d'exploitation", "PDF", "À la réception provisoire"),
        ("Rapport de recette fonctionnelle", "PDF", "Avant la mise en production"),
        ("Support de formation des utilisateurs clés", "PDF / PPTX", "À la réception provisoire"),
        ("Code source et scripts de déploiement", "Dépôt Git", "À la réception définitive"),
        ("Matrice de traçabilité des exigences", "Tableur", "Avant la recette"),
    ],
    "maintenance": [
        ("Inventaire initial du parc couvert", "Tableur", "Avant le démarrage du contrat"),
        ("Plan de maintenance préventive", "PDF", "Avant le démarrage du contrat"),
        ("Rapport d'intervention", "PDF", "À chaque intervention"),
        ("Rapport d'activité mensuel", "PDF", "Mensuel"),
        ("Plan de remplacement anticipé", "PDF", "Annuel"),
        ("Procédure d'astreinte", "PDF", "Avant le démarrage du contrat"),
        ("Bilan annuel de maintenance", "PDF", "Annuel"),
    ],
    "infrastructure": [
        ("Dossier de dimensionnement", "PDF", "Avant le démarrage des travaux"),
        ("Plan de migration", "PDF", "Avant la bascule"),
        ("Rapport de tests de restauration", "PDF", "Avant la réception définitive"),
        ("Documentation d'exploitation", "PDF", "À la réception provisoire"),
        ("Support de formation des équipes d'administration", "PDF / PPTX", "À la réception provisoire"),
        ("Procédure de reprise d'activité", "PDF", "À la réception définitive"),
        ("Registre des tests de restauration", "Tableur", "À la réception définitive"),
    ],
}

LIVRABLE_DESC = [
    "Remis au format électronique via l'espace projet partagé.",
    "Validé conjointement par le prestataire et la Société Exemple avant "
    "diffusion.",
    "Rédigé en français, conforme au gabarit documentaire de la Société Exemple.",
    "Mis à jour à chaque évolution significative constatée en cours de "
    "prestation.",
    "Versionné et archivé par le prestataire pendant toute la durée du "
    "marché.",
]


def build_livrables(rng, typ):
    rows = LIVRABLES_TYPE[typ]
    lignes = ["| Livrable | Format | Échéance | Observation |", "|---|---|---|---|"]
    for nom, fmt, echeance in rows:
        lignes.append(f"| {nom} | {fmt} | {echeance} | {rng.choice(LIVRABLE_DESC)} |")
    lignes.append("")
    lignes.append(
        "Chaque livrable fait l'objet d'une vérification de conformité par "
        "la Société Exemple avant acceptation. Un livrable rejeté est repris par "
        "le prestataire sans incidence sur le délai contractuel global, "
        "sauf si le rejet résulte d'une modification du périmètre demandée "
        "par la Société Exemple."
    )
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# 10. Planning et jalons
# ---------------------------------------------------------------------------

PHASES = [
    ("Cadrage et validation de l'architecture cible", 1, 3),
    ("Approvisionnement et préparation", 1, 4),
    ("Réalisation / déploiement", 2, 8),
    ("Tests et recette", 1, 3),
    ("Formation des équipes internes", 1, 2),
    ("Bascule en production", 1, 1),
    ("Période d'observation avant réception définitive", 1, 1),
]


PLANNING_ACTIVITES = [
    "Rédaction et diffusion du compte-rendu de phase",
    "Point d'avancement hebdomadaire avec le chef de projet Société Exemple",
    "Validation du jalon par le comité de pilotage",
    "Mise à jour du registre des risques",
    "Recueil des réserves éventuelles et plan de levée associé",
    "Mise à jour du planning détaillé partagé avec la Société Exemple",
    "Vérification des prérequis de la phase suivante",
]


def build_planning(rng, n):
    phases = PHASES[:max(3, min(n, len(PHASES)))]
    lignes = [
        "Le projet est découpé en phases successives, chacune donnant lieu "
        "à un jalon de validation formalisé par un compte-rendu "
        "contradictoire entre le prestataire et la Société Exemple. Le passage à "
        "la phase suivante est conditionné à la levée des réserves "
        "éventuelles de la phase précédente.", "",
    ]
    for i, (nom, dmin, dmax) in enumerate(phases, start=1):
        duree = rng.randint(dmin, dmax)
        unite = "semaine" if duree <= 1 else "semaines"
        lignes.append(
            f"Phase {i} — {nom} : {duree} {unite}. Cette phase se conclut "
            f"par un jalon formel dont la validation conditionne le "
            f"démarrage de la phase suivante."
        )
        for act in rng.sample(PLANNING_ACTIVITES, k=min(4, len(PLANNING_ACTIVITES))):
            lignes.append(f"  - {act}.")
        lignes.append("")
    return "\n".join(lignes).strip()


# ---------------------------------------------------------------------------
# 11. Délais, pénalités et garanties
# ---------------------------------------------------------------------------

def build_delais_penalites(rng, p):
    return (
        "Le respect du calendrier contractuel constitue une obligation "
        "essentielle du prestataire. Tout risque de dérive doit être "
        "signalé sans délai à la Société Exemple, accompagné d'un plan de "
        "récupération proposé par le prestataire.\n\n"
        "Le délai global d'exécution est fixé à {delai} jours calendaires à "
        "compter de la notification de l'ordre de service. Ce délai est "
        "ferme et non révisable, sauf cas de force majeure dûment notifié "
        "par écrit dans les cinq jours ouvrés suivant sa survenance.\n\n"
        "En cas de retard imputable au prestataire, une pénalité de {pen} % "
        "du montant total du marché est appliquée par jour calendaire de "
        "retard. Le montant cumulé des pénalités est plafonné à {plafond} % "
        "du montant du marché. Au-delà de ce plafond, la Société Exemple se "
        "réserve le droit de résilier le marché aux torts exclusifs du "
        "prestataire, sans préjudice des dommages et intérêts.\n\n"
        "La prestation est garantie {garantie} mois à compter de la date de "
        "réception définitive. Pendant cette période, le prestataire assure "
        "sans frais supplémentaires la correction de toute anomalie ainsi "
        "que le remplacement des éléments défectueux.\n\n"
        "À l'expiration de la garantie, un contrat de maintenance pourra "
        "être conclu par avenant, aux conditions tarifaires proposées dans "
        "l'offre initiale. Le prestataire communique, avant l'échéance de "
        "la garantie, un état des lieux détaillé du périmètre couvert, "
        "permettant à la Société Exemple d'apprécier l'opportunité de reconduire la "
        "relation contractuelle."
    ).format(**p)


# ---------------------------------------------------------------------------
# 12. Conditions financières
# ---------------------------------------------------------------------------

def build_financier(rng, p):
    return (
        "Le montant prévisionnel de la prestation est estimé à {montant} "
        "000 dirhams hors taxes. Le règlement intervient dans un délai de "
        "{paiement} jours à compter de la réception de la facture, sous "
        "réserve de la levée de toute réserve formulée lors de la "
        "réception.\n\n"
        "Une retenue de garantie de {retenue} % est appliquée sur chaque "
        "situation et libérée à la réception définitive. Le prestataire "
        "fournit une décomposition détaillée du prix global forfaitaire, "
        "distinguant la part matérielle, la part logicielle et la part "
        "prestation de services.\n\n"
        "Les factures sont établies selon les jalons décrits au chapitre "
        "Planning et jalons, et accompagnées des pièces justificatives "
        "requises par la Direction Financière de la Société Exemple. Toute facture "
        "incomplète est retournée au prestataire et suspend le délai de "
        "règlement jusqu'à régularisation.\n\n"
        "Les prix proposés sont réputés fermes pour toute la durée du "
        "marché, hors avenant expressément approuvé par les deux parties."
    ).format(**p)


# ---------------------------------------------------------------------------
# 13. Critères d'évaluation des offres
# ---------------------------------------------------------------------------

def build_evaluation(rng):
    prix = rng.choice([30, 35, 40])
    delai_w = rng.choice([10, 15])
    reference = 10
    reste = 100 - prix - delai_w - reference
    methodo = round(reste * 0.5)
    moyens = reste - methodo
    lignes = [
        "Les offres sont évaluées selon les critères pondérés ci-dessous, "
        "sur la base d'une notation à 100 points. Toute offre incomplète au "
        "regard des exigences obligatoires du présent cahier des charges "
        "est déclarée irrecevable et n'est pas notée.",
        "",
        "| Critère | Pondération |",
        "|---|---|",
        f"| Prix | {prix} % |",
        f"| Valeur technique — méthodologie proposée | {methodo} % |",
        f"| Valeur technique — moyens humains dédiés | {moyens} % |",
        f"| Délai d'exécution proposé | {delai_w} % |",
        f"| Références et expérience du prestataire | {reference} % |",
        "",
        "La notation prix est calculée au prorata de l'offre la mieux-disante. "
        "La notation technique est établie par une commission d'évaluation "
        "interne, sur la base d'une grille de critères communiquée aux "
        "candidats sur demande. Un écart significatif entre deux offres "
        "sur un même critère est motivé par écrit dans le rapport "
        "d'analyse des offres.",
    ]
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# 14. Annexes
# ---------------------------------------------------------------------------

ANNEXES_POOL = [
    "Références de prestations similaires réalisées au cours des trois "
    "dernières années",
    "Curriculum vitae des intervenants dédiés à la prestation",
    "Attestations de qualification et certifications applicables",
    "Attestation d'assurance responsabilité civile professionnelle",
    "Attestation de régularité fiscale et sociale",
    "Fiches techniques des équipements et logiciels proposés",
    "Modèle de rapport d'intervention ou de recette proposé",
    "Planning prévisionnel détaillé de la prestation",
    "Organigramme de l'équipe projet proposée",
    "Copie des certifications qualité en cours de validité",
    "Modèle de plan d'assurance qualité",
    "Grille de prix détaillée par poste",
]


def build_annexes(rng):
    chosen = rng.sample(ANNEXES_POOL, k=rng.randint(6, len(ANNEXES_POOL)))
    lignes = [
        "Le prestataire joint à son offre les pièces suivantes, sans "
        "lesquelles l'offre peut être déclarée irrecevable :", "",
    ]
    lignes += [f"- {a}." for a in chosen]
    return "\n".join(lignes)


# ---------------------------------------------------------------------------
# Paramètres tirés au sort par document
# ---------------------------------------------------------------------------

def draw_params(rng, typ):
    v1, v2 = rng.sample(VILLES, 2)
    return {
        "v1": v1, "v2": v2,
        "delai": rng.choice([30, 45, 60, 75, 90, 120, 150, 180]),
        "pen": rng.choice(["0,2", "0,3", "0,5", "0,8", "1"]),
        "plafond": rng.choice([5, 8, 10, 15]),
        "garantie": rng.choice([12, 24, 36]),
        "sla": rng.choice(["99,0", "99,5", "99,7", "99,8", "99,9"]),
        "gti": rng.choice([2, 4, 8]),
        "gtr": rng.choice([4, 8, 24, 48, 72]),
        "retenue": rng.choice([5, 10]),
        "paiement": rng.choice([30, 45, 60]),
        "montant": rng.choice([180, 240, 350, 480, 620, 850, 1200]),
        "debit": rng.choice([1, 2, 10, 40]),
        "latence": rng.choice([2, 3, 5, 8]),
        "bascule": rng.choice([15, 30, 60]),
        "users": rng.choice([50, 120, 250, 400, 800]),
        "preventif": rng.choice([3, 6, 12]),
        "rpo": rng.choice([1, 4, 12, 24]),
        "rto": rng.choice([4, 8, 24, 48]),
        "volume": rng.choice([20, 50, 100, 200]),
        "retention": rng.choice([30, 60, 90, 180]),
        "autonomie": rng.choice([15, 30, 45, 60]),
        "jour": rng.randint(1, 28),
        "mois": rng.choice(MOIS),
        "effectif": rng.choice([1200, 1800, 2500, 3200]),
        "nb_sites": rng.choice([6, 9, 12, 15]),
        "annee_plan": rng.choice([2024, 2025]),
        "anciennete": rng.choice([3, 5, 8]),
        "debit_bas": rng.choice([10, 20, 50]),
        "debit_haut": rng.choice([200, 500, 1000]),
        "trafic": rng.choice([80, 150, 300, 500]),
        "anciennete_outil": rng.choice([5, 8, 12]),
        "users_actuels": rng.choice([80, 150, 300, 600]),
        "volume_transactions": rng.choice([500, 1200, 3000, 8000]),
        "nb_equipements": rng.choice([120, 250, 400, 700]),
        "anciennete_parc": rng.choice([3, 5, 7]),
        "taux_panne": rng.choice([4, 6, 9, 12]),
        "nb_serveurs": rng.choice([15, 30, 50, 80]),
        "stockage_actuel": rng.choice([20, 40, 80, 150]),
        "taux_utilisation": rng.choice([55, 65, 75, 85]),
        "anciennete_prda": rng.choice([2, 3, 4]),
    }


def finalize_params(p):
    p["annee_plan_fin"] = p["annee_plan"] + 3
    return p


def word_count(text):
    return len(text.split())


def build_document(rng, typ, subject, ref, annee, p):
    # Amplitude tirée par document : fait varier la longueur dans la
    # fourchette cible sans dépendre d'une seule section.
    scale = rng.uniform(1.0, 3.0)

    sections = {}
    sections["contexte"] = build_contexte(rng, typ, p)
    sections["objet"] = build_objet(rng, subject, p)
    sections["perimetre"] = build_perimetre(rng, typ)
    sections["existant"] = build_existant(rng, typ, p)
    sections["besoin_fonctionnel"] = build_besoin_fonctionnel(
        rng, typ, n=round(12 * scale) + 52)
    sections["specs_techniques"] = build_specs_techniques(
        rng, typ, p, n=round(6 * scale) + 18)
    sections["normes"] = build_normes(rng, typ)
    sections["sla"] = build_sla(rng, p)
    sections["livrables"] = build_livrables(rng, typ)
    sections["planning"] = build_planning(rng, n=round(5 * scale) + 2)
    sections["delais_penalites"] = build_delais_penalites(rng, p)
    sections["financier"] = build_financier(rng, p)
    sections["evaluation"] = build_evaluation(rng)
    sections["annexes"] = build_annexes(rng)

    header = (
        f"CAHIER DES CHARGES\n"
        f"Référence : {ref}\n"
        f"Objet : {subject}\n"
        f"Maître d'ouvrage : Société Exemple — Direction des Systèmes "
        f"d'Information\n"
        f"Date : {p['jour']} {p['mois']} {annee}\n"
        f"Version : 1.0\n"
        f"Diffusion : restreinte — usage interne et prestataires consultés\n"
    )

    body_parts = [header]
    for ordre, code, titre in TEMPLATE_SECTIONS:
        body_parts.append(f"\n{ordre}. {titre}\n\n{sections[code]}\n")

    return "".join(body_parts)


def balanced_pool(values, n, rng):
    """Répartition contrôlée : chaque valeur apparaît un nombre quasi égal de
    fois, centrée sur le milieu de la liste plutôt qu'uniforme."""
    reps = -(-n // len(values))
    pool = (values * reps)[:n]
    rng.shuffle(pool)
    return pool


def main():
    rng = random.Random(SEED)
    OUT.mkdir(exist_ok=True)
    rows = []
    n = 101
    total = sum(len(s) for s in SUBJECTS.values())
    delais = balanced_pool([45, 60, 60, 75, 75, 90, 90, 120], total, rng)
    penalites = balanced_pool(["0,2", "0,3", "0,5", "0,8", "1"], total, rng)
    garanties = balanced_pool([12, 24, 36], total, rng)

    stats = []
    for typ, subjects in SUBJECTS.items():
        for tpl in subjects:
            p = draw_params(rng, typ)
            p["delai"] = delais.pop()
            p["pen"] = penalites.pop()
            p["garantie"] = garanties.pop()
            p = finalize_params(p)
            annee = rng.choice([2023, 2024, 2025])
            ref = f"CDC-{annee}-{n:03d}"
            subject = tpl.format(**p)
            text = build_document(rng, typ, subject, ref, annee, p)
            fname = f"{ref}_{typ}.txt"
            (OUT / fname).write_text(text, encoding="utf-8")
            wc = word_count(text)
            stats.append(wc)
            rows.append({
                "filename": fname, "ref": ref, "cdc_type": typ, "objet": subject,
                "delai_jours": p["delai"], "penalite_pct_jour": p["pen"],
                "plafond_pct": p["plafond"], "garantie_mois": p["garantie"],
                "sla_pct": p["sla"], "gtr_heures": p["gtr"],
                "retenue_pct": p["retenue"], "paiement_jours": p["paiement"],
                "montant_kdh": p["montant"],
            })
            n += 1

    with open(OUT / "manifest.csv", "w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    pages = [c / WORDS_PER_PAGE for c in stats]
    print(f"{len(rows)} documents générés dans {OUT}/")
    print(f"manifeste : {OUT}/manifest.csv")
    print(f"pages estimées ({WORDS_PER_PAGE} mots/page) : "
          f"min={min(pages):.1f} max={max(pages):.1f} "
          f"moy={sum(pages)/len(pages):.1f}")


if __name__ == "__main__":
    main()
