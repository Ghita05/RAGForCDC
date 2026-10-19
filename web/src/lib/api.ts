import type {
  ApproveResult,
  CdcEvent,
  CdcRecord,
  CdcSummary,
  CdcView,
  CreateCdcRequest,
  DocumentSummary,
  ExecutionResult,
  FormatTemplateView,
  GenerateRequest,
  OrganisationSettings,
  PlanRequest,
  PlanSummary,
  PlanView,
  ReviewRecord,
  ReviewStartResult,
  Section,
  SectionRequest,
  TemplateRequest,
  TemplateSection,
  TemplateSummary,
  TemplateView,
} from "@/lib/types";

export class ApiError extends Error {
  constructor(
    message: string,
    public status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) {
    const message = await extractErrorMessage(res);
    throw new ApiError(message, res.status);
  }
  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

async function extractErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { error?: string; message?: string };
    return body.message || body.error || res.statusText;
  } catch {
    return res.statusText || `Erreur ${res.status}`;
  }
}

export function listCdcs(): Promise<CdcSummary[]> {
  return request("/api/cdc");
}

/** Types de CDC réellement présents en base (cdc + document, voir
 * CdcRepository.findDistinctTypes) -- pas une liste figée côté frontend. */
export function listCdcTypes(): Promise<string[]> {
  return request("/api/cdc/types");
}

export function getCdc(id: number): Promise<CdcView> {
  return request(`/api/cdc/${id}`);
}

export function createCdc(req: CreateCdcRequest): Promise<CdcRecord> {
  return request("/api/cdc", { method: "POST", body: JSON.stringify(req) });
}

export function generateSection(cdcId: number, ordre: number): Promise<Section> {
  return request(`/api/cdc/${cdcId}/sections/${ordre}/generate`, { method: "POST" });
}

/** Exécute un plan (enregistré ou ponctuel) sur ce CDC -- voir PlanExecutionService. */
export function generatePlan(cdcId: number, req: GenerateRequest): Promise<ExecutionResult> {
  return request(`/api/cdc/${cdcId}/generate`, { method: "POST", body: JSON.stringify(req) });
}

export function getCdcEvents(cdcId: number): Promise<CdcEvent[]> {
  return request(`/api/cdc/${cdcId}/events`);
}

export function updateSection(cdcId: number, ordre: number, body: string): Promise<Section> {
  return request(`/api/cdc/${cdcId}/sections/${ordre}`, {
    method: "PUT",
    body: JSON.stringify({ body }),
  });
}

export function approveSection(cdcId: number, ordre: number): Promise<Section> {
  return request(`/api/cdc/${cdcId}/sections/${ordre}/approve`, { method: "POST" });
}

export function approveCdc(cdcId: number): Promise<ApproveResult> {
  return request(`/api/cdc/${cdcId}/approve`, { method: "POST" });
}

export function startReview(cdcId: number): Promise<ReviewStartResult> {
  const params = new URLSearchParams({ cdcId: String(cdcId) });
  return request(`/api/review?${params.toString()}`, { method: "POST" });
}

export function getReview(id: number): Promise<ReviewRecord> {
  return request(`/api/review/${id}`);
}

/** Une section obligatoire sur laquelle il reste du travail. */
export const ACTIVE_STATUSES = new Set(["QUEUED", "GENERATING"]);

export function listDocuments(): Promise<DocumentSummary[]> {
  return request("/api/documents");
}

export async function ingestFile(file: File, cdcType: string): Promise<void> {
  const form = new FormData();
  form.append("file", file);
  form.append("cdcType", cdcType);
  const res = await fetch("/api/ingest/file", { method: "POST", body: form });
  if (!res.ok) throw new ApiError(await extractErrorMessage(res), res.status);
}

export function listTemplates(): Promise<TemplateSummary[]> {
  return request("/api/templates");
}

export function getTemplate(id: number): Promise<TemplateView> {
  return request(`/api/templates/${id}`);
}

export function createTemplateSection(templateId: number, req: SectionRequest): Promise<TemplateSection> {
  return request(`/api/templates/${templateId}/sections`, {
    method: "POST",
    body: JSON.stringify(req),
  });
}

export function updateTemplateSection(
  templateId: number,
  sectionId: number,
  req: SectionRequest,
): Promise<TemplateSection> {
  return request(`/api/templates/${templateId}/sections/${sectionId}`, {
    method: "PUT",
    body: JSON.stringify(req),
  });
}

export function deleteTemplateSection(templateId: number, sectionId: number): Promise<void> {
  return request(`/api/templates/${templateId}/sections/${sectionId}`, { method: "DELETE" });
}

export function createTemplate(req: TemplateRequest): Promise<TemplateView> {
  return request("/api/templates", { method: "POST", body: JSON.stringify(req) });
}

export function updateTemplate(id: number, req: TemplateRequest): Promise<TemplateView> {
  return request(`/api/templates/${id}`, { method: "PUT", body: JSON.stringify(req) });
}

export function duplicateTemplate(id: number, name?: string): Promise<TemplateView> {
  return request(`/api/templates/${id}/duplicate`, {
    method: "POST",
    body: JSON.stringify({ name: name ?? null }),
  });
}

export function deleteTemplate(id: number): Promise<void> {
  return request(`/api/templates/${id}`, { method: "DELETE" });
}

export function listPlans(): Promise<PlanSummary[]> {
  return request("/api/plans");
}

export function getPlan(id: number): Promise<PlanView> {
  return request(`/api/plans/${id}`);
}

export function createPlan(req: PlanRequest): Promise<PlanView> {
  return request("/api/plans", { method: "POST", body: JSON.stringify(req) });
}

export function updatePlan(id: number, req: PlanRequest): Promise<PlanView> {
  return request(`/api/plans/${id}`, { method: "PUT", body: JSON.stringify(req) });
}

export function duplicatePlan(id: number, name?: string): Promise<PlanView> {
  return request(`/api/plans/${id}/duplicate`, {
    method: "POST",
    body: JSON.stringify({ name: name ?? null }),
  });
}

export function listFormatTemplates(): Promise<FormatTemplateView[]> {
  return request("/api/format-templates");
}

export function getFormatTemplate(id: number): Promise<FormatTemplateView> {
  return request(`/api/format-templates/${id}`);
}

export async function uploadFormatTemplate(name: string, file: File): Promise<FormatTemplateView> {
  const form = new FormData();
  form.append("name", name);
  form.append("file", file);
  const res = await fetch("/api/format-templates", { method: "POST", body: form });
  if (!res.ok) throw new ApiError(await extractErrorMessage(res), res.status);
  return res.json();
}

export function setDefaultFormatTemplate(id: number): Promise<FormatTemplateView> {
  return request(`/api/format-templates/${id}/set-default`, { method: "POST" });
}

export function deleteFormatTemplate(id: number): Promise<void> {
  return request(`/api/format-templates/${id}`, { method: "DELETE" });
}

export function setCdcFormatTemplate(cdcId: number, formatTemplateId: number | null): Promise<CdcRecord> {
  return request(`/api/cdc/${cdcId}/format-template`, {
    method: "PUT",
    body: JSON.stringify({ formatTemplateId }),
  });
}

export function getOrganisationSettings(): Promise<OrganisationSettings> {
  return request("/api/organisation-settings");
}

export function updateOrganisationSettings(settings: OrganisationSettings): Promise<OrganisationSettings> {
  return request("/api/organisation-settings", { method: "PUT", body: JSON.stringify(settings) });
}

export async function uploadOrganisationLogo(file: File): Promise<OrganisationSettings> {
  const form = new FormData();
  form.append("file", file);
  const res = await fetch("/api/organisation-settings/logo", { method: "POST", body: form });
  if (!res.ok) throw new ApiError(await extractErrorMessage(res), res.status);
  return res.json();
}

export function clearOrganisationLogo(): Promise<OrganisationSettings> {
  return request("/api/organisation-settings/logo", { method: "DELETE" });
}

/** URL à utiliser directement dans un <img> -- cache-bust via un paramètre
 * pour refléter un nouveau logo téléversé sans forcer un rechargement complet. */
export function organisationLogoUrl(cacheBust?: number): string {
  return `/api/organisation-settings/logo${cacheBust ? `?v=${cacheBust}` : ""}`;
}
