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
import { cn } from "@/lib/utils";
import {
  estimateSteps,
  PlanStepsEditor,
  stepsFromPlan,
  stepsFromTemplate,
  toStepInputs,
  type PlanStepDraft,
} from "@/components/plan-steps-editor";
import {
  ApiError,
  createCdc,
  generatePlan,
  getPlan,
  getTemplate,
  listPlans,
  listTemplates,
} from "@/lib/api";
import type { PlanMode, PlanSummary, TemplateSummary } from "@/lib/types";
import { useCdcTypeSuggestions } from "@/lib/use-cdc-types";

type PlanOrigin = "adhoc" | "saved";
type Step = 1 | 2;

function initialState() {
  return {
    step: 1 as Step,
    title: "",
    templateId: null as number | null,
    cdcType: "",
    brief: "",
    origin: "adhoc" as PlanOrigin,
    selectedPlanId: null as number | null,
    createdCdcId: null as number | null,
    mode: "COMPLET" as PlanMode,
    steps: [] as PlanStepDraft[],
  };
}

export default function NewCdcPage() {
  const router = useRouter();
  const [templates, setTemplates] = useState<TemplateSummary[] | null>(null);
  const [plans, setPlans] = useState<PlanSummary[] | null>(null);
  const [state, setState] = useState(initialState);
  const [loadingStep2, setLoadingStep2] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cdcTypeSuggestions = useCdcTypeSuggestions();

  useEffect(() => {
    listTemplates()
      .then((list) => {
        setTemplates(list);
        setState((s) =>
          s.templateId === null ? { ...s, templateId: list.find((t) => t.isDefault)?.id ?? list[0]?.id ?? null } : s,
        );
      })
      .catch(() => setError("Impossible de charger les templates."));
    listPlans()
      .then(setPlans)
      .catch(() => setError("Impossible de charger les plans."));
  }, []);

  async function handleContinue(e: React.FormEvent) {
    e.preventDefault();
    if (!state.title.trim()) return;
    if (state.origin === "adhoc" && state.templateId === null) return;
    if (state.origin === "saved" && state.selectedPlanId === null) return;

    setSubmitting(true);
    setError(null);
    try {
      const cdc = await createCdc({
        title: state.title.trim(),
        templateId: state.origin === "adhoc" ? state.templateId : null,
        cdcType: state.cdcType.trim() || null,
        brief: state.brief.trim() || null,
        planId: state.origin === "saved" ? state.selectedPlanId : null,
      });

      setLoadingStep2(true);
      if (state.origin === "saved" && state.selectedPlanId !== null) {
        const plan = await getPlan(state.selectedPlanId);
        setState((s) => ({
          ...s,
          step: 2,
          createdCdcId: cdc.id,
          mode: plan.summary.mode,
          steps: stepsFromPlan(plan.steps),
        }));
      } else {
        const view = await getTemplate(cdc.templateId!);
        setState((s) => ({
          ...s,
          step: 2,
          createdCdcId: cdc.id,
          mode: "COMPLET",
          steps: stepsFromTemplate(view.sections),
        }));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de la création du CDC.");
    } finally {
      setSubmitting(false);
      setLoadingStep2(false);
    }
  }

  async function handleGenerate() {
    if (state.createdCdcId === null) return;
    setSubmitting(true);
    setError(null);
    try {
      await generatePlan(state.createdCdcId, { mode: state.mode, etapes: toStepInputs(state.steps) });
      router.push(`/cdc/${state.createdCdcId}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec du lancement de la génération.");
    } finally {
      setSubmitting(false);
    }
  }

  const { calls, minutes } = estimateSteps(state.mode, state.steps);

  return (
    <main className="flex flex-1 flex-col">
      <div className="border-b border-border px-6 py-4">
        <Link
          href="/"
          className="mb-2 inline-flex w-fit items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="size-3.5" />
          Cahiers des charges
        </Link>
        <h1 className="text-lg font-medium">Nouveau cahier des charges</h1>
        <p className="text-sm text-muted-foreground">
          {state.step === 1
            ? "Le CDC et ses sections vides sont créés à cette étape ; la génération se lance à l'étape suivante, à partir d'un plan."
            : state.origin === "saved"
              ? "Plan pré-rempli à partir du plan sélectionné, ajustable pour cette génération."
              : "Plan pré-rempli à partir du template, ajustable pour cette génération."}
        </p>
      </div>

      {state.step === 1 ? (
        <form onSubmit={handleContinue} className="flex flex-1 flex-col">
          <div className="flex-1 px-6 py-6">
            <div className="mx-auto flex max-w-2xl flex-col gap-4">
              <div className="grid gap-2">
                <Label htmlFor="cdc-title">Titre</Label>
                <Input
                  id="cdc-title"
                  value={state.title}
                  onChange={(e) => setState((s) => ({ ...s, title: e.target.value }))}
                  required
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="grid gap-2">
                  <Label htmlFor="cdc-type">Type (optionnel)</Label>
                  <Input
                    id="cdc-type"
                    list="cdc-type-suggestions"
                    value={state.cdcType}
                    onChange={(e) => setState((s) => ({ ...s, cdcType: e.target.value }))}
                    placeholder="ex. réseau"
                  />
                  <datalist id="cdc-type-suggestions">
                    {cdcTypeSuggestions.map((t) => (
                      <option key={t} value={t} />
                    ))}
                  </datalist>
                </div>

                {state.origin === "adhoc" && (
                  <div className="grid gap-2">
                    <Label htmlFor="cdc-template">Structure</Label>
                    <Select
                      value={state.templateId !== null ? String(state.templateId) : ""}
                      onValueChange={(v) => {
                        if (!v) return;
                        setState((s) => ({ ...s, templateId: Number(v) }));
                      }}
                    >
                      <SelectTrigger id="cdc-template" className="w-full">
                        <SelectValue placeholder="Sélectionner une structure" />
                      </SelectTrigger>
                      <SelectContent>
                        {templates?.map((t) => (
                          <SelectItem key={t.id} value={String(t.id)}>
                            {t.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}
              </div>

              <div className="grid gap-2">
                <Label htmlFor="cdc-brief">Brief</Label>
                <Textarea
                  id="cdc-brief"
                  placeholder="Décrit le projet en une ou deux phrases."
                  value={state.brief}
                  onChange={(e) => setState((s) => ({ ...s, brief: e.target.value }))}
                  rows={3}
                />
              </div>

              <div className="grid gap-2">
                <Label>Plan de génération</Label>
                <div className="inline-flex w-fit rounded-lg border border-input p-0.5">
                  <button
                    type="button"
                    className={cn(
                      "rounded-md px-3 py-1 text-sm transition-colors",
                      state.origin === "adhoc"
                        ? "bg-secondary text-secondary-foreground"
                        : "text-muted-foreground hover:text-foreground",
                    )}
                    onClick={() => setState((s) => ({ ...s, origin: "adhoc" }))}
                  >
                    Plan ponctuel
                  </button>
                  <button
                    type="button"
                    className={cn(
                      "rounded-md px-3 py-1 text-sm transition-colors",
                      state.origin === "saved"
                        ? "bg-secondary text-secondary-foreground"
                        : "text-muted-foreground hover:text-foreground",
                    )}
                    onClick={() => setState((s) => ({ ...s, origin: "saved" }))}
                  >
                    Plan enregistré
                  </button>
                </div>

                {state.origin === "saved" && (
                  <Select
                    value={state.selectedPlanId !== null ? String(state.selectedPlanId) : ""}
                    onValueChange={(v) => {
                      if (!v) return;
                      const plan = plans?.find((p) => p.id === Number(v));
                      setState((s) => ({
                        ...s,
                        selectedPlanId: Number(v),
                        cdcType: s.cdcType || plan?.cdcType || "",
                      }));
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder="Sélectionner un plan" />
                    </SelectTrigger>
                    <SelectContent>
                      {plans?.length === 0 && (
                        <div className="px-2 py-1.5 text-sm text-muted-foreground">
                          Aucun plan enregistré.{" "}
                          <Link href="/plans/nouveau" className="underline">
                            En créer un
                          </Link>
                          .
                        </div>
                      )}
                      {plans?.map((p) => (
                        <SelectItem key={p.id} value={String(p.id)}>
                          {p.name} · {p.templateName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                )}
              </div>

              {error && (
                <Alert variant="destructive">
                  <AlertDescription>{error}</AlertDescription>
                </Alert>
              )}
            </div>
          </div>

          <div className="sticky bottom-0 flex items-center justify-end gap-2 border-t border-border bg-background px-6 py-3">
            <Button type="button" variant="outline" onClick={() => router.push("/")} disabled={submitting}>
              Annuler
            </Button>
            <Button
              type="submit"
              disabled={
                submitting ||
                loadingStep2 ||
                !state.title.trim() ||
                (state.origin === "adhoc" ? state.templateId === null : state.selectedPlanId === null)
              }
            >
              {submitting || loadingStep2 ? "Création…" : "Continuer"}
            </Button>
          </div>
        </form>
      ) : (
        <div className="flex flex-1 flex-col">
          <div className="flex-1 overflow-y-auto px-6 py-6">
            <div className="mx-auto max-w-3xl">
              <PlanStepsEditor
                mode={state.mode}
                onModeChange={(mode) => setState((s) => ({ ...s, mode }))}
                steps={state.steps}
                onStepsChange={(steps) => setState((s) => ({ ...s, steps }))}
              />
              {error && (
                <Alert variant="destructive" className="mt-4">
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
              <Button
                type="button"
                variant="outline"
                onClick={() => router.push(`/cdc/${state.createdCdcId}`)}
                disabled={submitting}
              >
                Plus tard
              </Button>
              <Button type="button" onClick={handleGenerate} disabled={submitting}>
                {submitting ? "Lancement…" : "Lancer la génération"}
              </Button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
