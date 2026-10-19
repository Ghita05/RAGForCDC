// Miroir des records Java exposés par com.eb.cdcrag.generation.* et
// com.eb.cdcrag.review.* (Jackson sérialise les composants de record tels
// quels, en camelCase).

export type SectionStatus =
  | "EMPTY"
  | "QUEUED"
  | "GENERATING"
  | "DRAFT"
  | "FAILED"
  | "APPROVED";

export type CdcStatus = "DRAFT" | "APPROVED";

export const CDC_TYPES = [
  { value: "reseau", label: "Réseau" },
  { value: "logiciel", label: "Logiciel" },
  { value: "maintenance", label: "Maintenance" },
  { value: "infrastructure", label: "Infrastructure" },
] as const;

/** Libellé lisible pour un type de CDC. CDC_TYPES ne sert plus qu'à
 * l'affichage d'un habillage cosmétique pour les types courants -- les
 * valeurs réellement proposées dans les sélecteurs viennent de la base
 * (voir useCdcTypes/useCdcTypeSuggestions dans lib/use-cdc-types.ts), donc
 * un type saisi librement et absent de cette liste s'affiche tel quel. */
export function typeLabel(cdcType: string | null): string {
  if (!cdcType) return "—";
  return (CDC_TYPES.find((t) => t.value === cdcType)?.label ?? cdcType).toLowerCase();
}

export interface CdcSummary {
  id: number;
  title: string;
  cdcType: string | null;
  status: CdcStatus;
  totalSections: number;
  draftSections: number;
  approvedSections: number;
  createdAt: string;
  lastModifiedAt: string;
  approvedAt: string | null;
  reviewStatus: ReviewStatus | null;
}

export interface CdcRecord {
  id: number;
  title: string;
  cdcType: string | null;
  status: CdcStatus;
  error: string | null;
  templateId: number | null;
  brief: string | null;
  body: string | null;
  planId: number | null;
  planName: string | null;
  formatTemplateId: number | null;
  formatTemplateName: string | null;
}

export interface Section {
  id: number;
  cdcId: number;
  ordre: number;
  code: string;
  titre: string;
  consigne: string;
  obligatoire: boolean;
  body: string | null;
  status: SectionStatus;
  error: string | null;
}

export interface CdcView {
  cdc: CdcRecord;
  sections: Section[];
  reviewStatus: ReviewStatus | null;
}

export interface ApproveResult {
  cdc: CdcRecord;
  sections: Section[];
  documentId: number | null;
}

export interface ReviewStartResult {
  reviewId: number;
  cdcId: number;
  status: string;
}

export type ReviewStatus = "RUNNING" | "DONE" | "FAILED";

export interface ReviewRecord {
  id: number;
  cdcId: number;
  status: ReviewStatus;
  findings: string | null; // JSON encodé côté serveur (colonne jsonb) -> parseFindings()
  error: string | null;
}

export type ConstatCategorie =
  | "ecart_norme"
  | "section_manquante"
  | "contradiction"
  | "imprecision";

export type ConstatSeverite = "haute" | "moyenne" | "basse";

export interface ReviewConstat {
  categorie: ConstatCategorie;
  severite: ConstatSeverite;
  titre: string;
  explication: string;
  suggestion: string;
}

/** Écart chiffré, calculé en Java (DeviationChecker) et injecté dans le
 * JSON après rédaction par le LLM (ReviewWorker.withEvidence) -- jamais
 * rédigé ni recopié par le modèle, pour rester vérifiable indépendamment
 * de la prose du constat correspondant. */
export interface Ecart {
  champ: string;
  valeur: number;
  unite: string;
  median: number;
  min: number;
  max: number;
  n: number;
  sens: string;
}

export interface Echantillon {
  cdcType: string | null;
  n: number;
}

export interface ReviewFindings {
  resume: string;
  /** Absents sur les relectures faites avant l'ajout de ce champ (JSON
   * stocké tel quel en base) -- toujours vérifier avant utilisation. */
  echantillon?: Echantillon;
  ecarts?: Ecart[];
  constats: ReviewConstat[];
}

export function parseFindings(findings: string | null): ReviewFindings | null {
  if (!findings) return null;
  try {
    return JSON.parse(findings) as ReviewFindings;
  } catch {
    return null;
  }
}

export interface DocumentSummary {
  id: number;
  filename: string;
  cdcType: string | null;
  createdAt: string;
  delaiJours: number | null;
  penalitePct: number | null;
  plafondPct: number | null;
  garantieMois: number | null;
  slaPct: number | null;
  gtrHeures: number | null;
  gtiHeures: number | null;
}

export interface TemplateSummary {
  id: number;
  name: string;
  description: string | null;
  cdcType: string | null;
  owner: string | null;
  isDefault: boolean;
  sectionCount: number;
}

export interface TemplateRequest {
  name: string;
  description: string | null;
  cdcType: string | null;
}

export interface TemplateSection {
  id: number;
  ordre: number;
  code: string;
  titre: string;
  consigne: string;
  obligatoire: boolean;
}

export interface TemplateView {
  template: TemplateSummary;
  sections: TemplateSection[];
}

export interface SectionRequest {
  ordre: number;
  code: string;
  titre: string;
  consigne: string;
  obligatoire: boolean;
}

export type PlanMode = "TRAME" | "SECTIONS" | "COMPLET";

export const PLAN_MODE_LABEL: Record<PlanMode, string> = {
  TRAME: "Trame",
  SECTIONS: "Sections choisies",
  COMPLET: "Complet",
};

export interface PlanSummary {
  id: number;
  name: string;
  description: string | null;
  templateId: number;
  templateName: string;
  cdcType: string | null;
  mode: PlanMode;
  totalSteps: number;
  includedSteps: number;
  createdAt: string;
  updatedAt: string;
}

export interface PlanStep {
  id: number;
  planId: number;
  ordre: number;
  templateSectionId: number;
  code: string;
  titre: string;
  consigne: string;
  obligatoire: boolean;
  guidance: string | null;
  inclus: boolean;
}

export interface PlanView {
  summary: PlanSummary;
  steps: PlanStep[];
}

export interface StepInput {
  ordre: number;
  templateSectionId: number;
  guidance: string | null;
  inclus: boolean;
}

export interface PlanRequest {
  name: string;
  description: string | null;
  templateId: number;
  cdcType: string | null;
  mode: PlanMode;
  steps: StepInput[];
}

export interface CreateCdcRequest {
  title: string;
  templateId: number | null;
  cdcType: string | null;
  brief: string | null;
  planId: number | null;
}

export interface EtapeInput {
  ordre: number;
  templateSectionId: number;
  guidance: string | null;
  inclus: boolean;
}

/** Soit {planId} pour exécuter un plan enregistré, soit {mode, etapes} pour
 * un plan ponctuel -- exclusifs l'un de l'autre, voir PlanExecutionService. */
export interface GenerateRequest {
  planId?: number | null;
  mode?: PlanMode | null;
  etapes?: EtapeInput[] | null;
}

export interface ExecutionResult {
  calls: number;
}

export interface CdcEvent {
  id: number;
  cdcId: number;
  type: string;
  payload: string;
  createdAt: string;
}

/** ~95s par section, un seul appel pour TRAME quel que soit le nombre de
 * sections (voir docs/decisions.md). */
export function estimateGenerationSeconds(mode: PlanMode, includedSteps: number): number {
  return mode === "TRAME" ? 95 : includedSteps * 95;
}

export type CdcPhase = "GENERE" | "EN_RELECTURE" | "PARTIELLEMENT_VALIDE" | "VALIDE";

export const PHASE_LABEL: Record<CdcPhase, string> = {
  GENERE: "Généré",
  EN_RELECTURE: "En relecture",
  PARTIELLEMENT_VALIDE: "Partiellement validé",
  VALIDE: "Validé",
};

/** Le statut brut du CDC (DRAFT/APPROVED) ne distingue pas où on en est
 * réellement dans le cycle de vie : cette phase l'explicite pour l'affichage,
 * à partir de l'avancement des sections et de la relecture la plus récente. */
export function cdcPhase(cdc: Pick<CdcSummary, "status" | "approvedSections" | "reviewStatus">): CdcPhase {
  if (cdc.status === "APPROVED") return "VALIDE";
  if (cdc.reviewStatus === "RUNNING") return "EN_RELECTURE";
  if (cdc.approvedSections > 0) return "PARTIELLEMENT_VALIDE";
  return "GENERE";
}

/** Miroir exact de com.eb.cdcrag.export.FormatStyle -- couleurs en hex sans
 * "#" (ex. "2F4B7C"), marges en twips (1/1440 pouce). */
export interface FormatStyle {
  fontFamily: string;
  bodyFontSizePt: number;
  heading1Color: string;
  heading1FontSizePt: number;
  heading1Bold: boolean;
  heading2Color: string;
  heading2FontSizePt: number;
  heading2Bold: boolean;
  accentColor: string;
  tableHeaderBgColor: string;
  marginTopTwips: number;
  marginBottomTwips: number;
  marginLeftTwips: number;
  marginRightTwips: number;
}

export interface FormatTemplateSummary {
  id: number;
  name: string;
  filename: string;
  styleSummaryJson: string;
  isDefault: boolean;
  createdAt: string;
}

export interface FormatTemplateView {
  summary: FormatTemplateSummary;
  style: FormatStyle;
}

/** Miroir exact de com.eb.cdcrag.settings.OrganisationSettings -- contenu de
 * marque (page de garde, pied de page), à distinguer de FormatStyle
 * (apparence) et de TemplateView (structure/sections). Les octets du logo ne
 * transitent jamais ici : logoUrl() donne l'URL à utiliser dans un <img>. */
export interface OrganisationSettings {
  nomOrganisation: string | null;
  direction: string | null;
  mentionConfidentialite: string | null;
  formatReference: string | null;
  piedDePage: string | null;
  contactService: string | null;
  contactAdresse: string | null;
  contactCourriel: string | null;
  hasLogo: boolean;
  logoContentType: string | null;
}

/** Variables reconnues par ReferenceTemplate.java (backend) -- même liste,
 * affichée dans l'interface (page /parametres) pour documenter le gabarit. */
export const REFERENCE_TEMPLATE_VARIABLES = [
  { token: "{annee}", description: "Année courante (au moment de l'export)" },
  { token: "{numero}", description: "Identifiant du CDC en base (unique, jamais remis à zéro)" },
  { token: "{numero:000}", description: "Identifiant du CDC, complété de zéros sur 3 chiffres (ex. 007)" },
  { token: "{type}", description: "Type du CDC (vide si non renseigné)" },
] as const;

/** Reproduction cliente de com.eb.cdcrag.export.ReferenceTemplate.render --
 * uniquement pour l'aperçu en temps réel de la page de garde (page
 * /parametres), le rendu réel reste calculé côté serveur à l'export. */
export function renderReferenceTemplate(template: string | null, annee: number, numero: number, type: string | null): string {
  const t = template && template.trim() ? template : "CDC-{numero}";
  return t.replace(/\{(annee|numero|type)(:0+)?}/g, (match, variable: string, padding?: string) => {
    if (variable === "annee") return String(annee);
    if (variable === "numero") return padding ? String(numero).padStart(padding.length - 1, "0") : String(numero);
    if (variable === "type") return type ?? "";
    return match;
  });
}
