"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  estimateSteps,
  PlanStepsEditor,
  stepsFromPlan,
  stepsFromTemplate,
  toStepInputs,
  type PlanStepDraft,
} from "@/components/plan-steps-editor";
import { ApiError, getTemplate } from "@/lib/api";
import type { PlanMode, PlanRequest, PlanView, TemplateSummary } from "@/lib/types";
import { useCdcTypeSuggestions } from "@/lib/use-cdc-types";

interface Props {
  templates: TemplateSummary[];
  /** undefined = création d'un nouveau plan ; sinon édition de celui-ci
   * (le template n'est alors plus modifiable, voir PlanService.update). */
  plan?: PlanView;
  onSubmit: (req: PlanRequest) => Promise<PlanView>;
}

export function PlanForm({ templates, plan, onSubmit }: Props) {
  const router = useRouter();
  const isEdit = plan !== undefined;
  const [name, setName] = useState(plan?.summary.name ?? "");
  const [description, setDescription] = useState(plan?.summary.description ?? "");
  const [cdcType, setCdcType] = useState(plan?.summary.cdcType ?? "");
  const [templateId, setTemplateId] = useState<number | null>(
    plan?.summary.templateId ?? templates.find((t) => t.isDefault)?.id ?? templates[0]?.id ?? null,
  );
  const [mode, setMode] = useState<PlanMode>(plan?.summary.mode ?? "COMPLET");
  const [steps, setSteps] = useState<PlanStepDraft[]>(plan ? stepsFromPlan(plan.steps) : []);
  const [loadingSteps, setLoadingSteps] = useState(!isEdit);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cdcTypeSuggestions = useCdcTypeSuggestions();

  useEffect(() => {
    if (isEdit || templateId === null) return;
    void (async () => {
      setLoadingSteps(true);
      try {
        const view = await getTemplate(templateId);
        setSteps(stepsFromTemplate(view.sections));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Impossible de charger la structure.");
      } finally {
        setLoadingSteps(false);
      }
    })();
  }, [templateId, isEdit]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim() || templateId === null) return;
    setSubmitting(true);
    setError(null);
    try {
      const result = await onSubmit({
        name: name.trim(),
        description: description.trim() || null,
        templateId,
        cdcType: cdcType.trim() || null,
        mode,
        steps: toStepInputs(steps),
      });
      router.push(`/plans/${result.summary.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de l'enregistrement.");
    } finally {
      setSubmitting(false);
    }
  }

  const { calls, minutes } = estimateSteps(mode, steps);

  return (
    <main className="flex flex-1 flex-col">
      <div className="border-b border-border px-6 py-4">
        <Link
          href="/plans"
          className="mb-2 inline-flex w-fit items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="size-3.5" />
          Plans
        </Link>
        <h1 className="text-lg font-medium">{isEdit ? `Éditer « ${plan.summary.name} »` : "Nouveau plan"}</h1>
        <p className="text-sm text-muted-foreground">
          Un plan référence une structure et décrit comment générer à partir d&apos;elle : quelles
          sections, dans quel ordre, avec quelles précisions. Renseignez le nom et la structure,
          puis ajustez la liste des sections ci-dessous.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="flex flex-1 flex-col">
        <div className="flex-1 overflow-y-auto px-6 py-6 pb-4">
          <div className="mx-auto flex max-w-3xl flex-col gap-4">
            <div className="grid gap-2">
              <Label htmlFor="plan-name">Nom</Label>
              <Input id="plan-name" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="plan-description">Description</Label>
              <Textarea
                id="plan-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={2}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="plan-template">Structure</Label>
                <Select
                  value={templateId !== null ? String(templateId) : ""}
                  onValueChange={(v) => {
                    if (!v) return;
                    setTemplateId(Number(v));
                  }}
                  disabled={isEdit}
                >
                  <SelectTrigger id="plan-template" className="w-full">
                    <SelectValue placeholder="Sélectionner une structure" />
                  </SelectTrigger>
                  <SelectContent>
                    {templates.map((t) => (
                      <SelectItem key={t.id} value={String(t.id)}>
                        {t.name}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid gap-2">
                <Label htmlFor="plan-cdc-type">Type de CDC (optionnel)</Label>
                <Input
                  id="plan-cdc-type"
                  list="cdc-type-suggestions"
                  value={cdcType}
                  onChange={(e) => setCdcType(e.target.value)}
                  placeholder="ex. réseau"
                />
                <datalist id="cdc-type-suggestions">
                  {cdcTypeSuggestions.map((t) => (
                    <option key={t} value={t} />
                  ))}
                </datalist>
              </div>
            </div>

            {loadingSteps ? (
              <p className="text-sm text-muted-foreground">Chargement des sections de la structure…</p>
            ) : steps.length > 0 ? (
              <PlanStepsEditor mode={mode} onModeChange={setMode} steps={steps} onStepsChange={setSteps} />
            ) : null}

            {error && (
              <Alert variant="destructive">
                <AlertDescription>{error}</AlertDescription>
              </Alert>
            )}
          </div>
        </div>

        <div className="sticky bottom-0 flex items-center justify-between border-t border-border bg-background px-6 py-3">
          <span className="text-sm text-muted-foreground">
            {calls} appel{calls > 1 ? "s" : ""} · environ {minutes} minute{minutes > 1 ? "s" : ""}
          </span>
          <div className="flex items-center gap-2">
            <Button type="button" variant="outline" onClick={() => router.push("/plans")} disabled={submitting}>
              Annuler
            </Button>
            <Button type="submit" disabled={submitting || !name.trim() || templateId === null}>
              {submitting ? "Enregistrement…" : isEdit ? "Enregistrer" : "Créer le plan"}
            </Button>
          </div>
        </div>
      </form>
    </main>
  );
}
