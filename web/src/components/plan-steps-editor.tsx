"use client";

import { useState } from "react";
import { GripVertical } from "lucide-react";
import { Checkbox } from "@/components/ui/checkbox";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { cn } from "@/lib/utils";
import { estimateGenerationSeconds, PLAN_MODE_LABEL } from "@/lib/types";
import type { PlanMode, PlanStep, StepInput, TemplateSection } from "@/lib/types";

/** Représentation éditable d'une étape, indépendante de son id en base
 * (un plan ponctuel n'a pas d'id) -- l'ordre est l'index dans le tableau,
 * assigné à la sauvegarde (voir toStepInputs). */
export interface PlanStepDraft {
  templateSectionId: number;
  code: string;
  titre: string;
  guidance: string;
  inclus: boolean;
}

export function stepsFromTemplate(sections: TemplateSection[]): PlanStepDraft[] {
  return sections
    .slice()
    .sort((a, b) => a.ordre - b.ordre)
    .map((s) => ({ templateSectionId: s.id, code: s.code, titre: s.titre, guidance: "", inclus: true }));
}

export function stepsFromPlan(steps: PlanStep[]): PlanStepDraft[] {
  return steps
    .slice()
    .sort((a, b) => a.ordre - b.ordre)
    .map((s) => ({
      templateSectionId: s.templateSectionId,
      code: s.code,
      titre: s.titre,
      guidance: s.guidance ?? "",
      inclus: s.inclus,
    }));
}

/** Même forme que EtapeInput (génération ponctuelle) : réutilisable pour
 * les deux appels. */
export function toStepInputs(steps: PlanStepDraft[]): StepInput[] {
  return steps.map((s, i) => ({
    ordre: i + 1,
    templateSectionId: s.templateSectionId,
    guidance: s.guidance.trim() ? s.guidance.trim() : null,
    inclus: s.inclus,
  }));
}

/** Calcul affiché dans le pied de page sticky des pages qui embarquent cet
 * éditeur (voir /plans/nouveau, /plans/[id], /cdc/nouveau) -- extrait ici
 * pour que le pied de page reste visible même quand la liste des sections
 * défile, plutôt que de vivre à l'intérieur du composant qui défile avec elle. */
export function estimateSteps(mode: PlanMode, steps: PlanStepDraft[]): { calls: number; minutes: number } {
  const includedCount = mode === "TRAME" ? steps.length : steps.filter((s) => s.inclus).length;
  const calls = mode === "TRAME" ? 1 : includedCount;
  const minutes = Math.max(1, Math.round(estimateGenerationSeconds(mode, includedCount) / 60));
  return { calls, minutes };
}

interface PlanStepsEditorProps {
  mode: PlanMode;
  onModeChange: (mode: PlanMode) => void;
  steps: PlanStepDraft[];
  onStepsChange: (steps: PlanStepDraft[]) => void;
}

/** Éditeur d'étapes réutilisé tel quel par /plans et par le dialogue
 * «Nouveau CDC» (étape 2) : ordre glisser-déposer, case «inclus», champ
 * «guidance» par section, sélecteur de mode. En mode TRAME, inclus/guidance
 * par section n'ont pas de sens (un seul appel, toutes les sections servent
 * de sommaire -- voir PlanExecutionService.runTrame) : la case est
 * désactivée et le champ masqué, seul l'ordre reste éditable. */
export function PlanStepsEditor({ mode, onModeChange, steps, onStepsChange }: PlanStepsEditorProps) {
  const [dragIndex, setDragIndex] = useState<number | null>(null);

  function move(from: number, to: number) {
    if (from === to) return;
    const next = steps.slice();
    const [item] = next.splice(from, 1);
    next.splice(to, 0, item);
    onStepsChange(next);
  }

  function toggleInclus(index: number) {
    const next = steps.slice();
    next[index] = { ...next[index], inclus: !next[index].inclus };
    onStepsChange(next);
  }

  function setGuidance(index: number, guidance: string) {
    const next = steps.slice();
    next[index] = { ...next[index], guidance };
    onStepsChange(next);
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-2">
        <label className="text-sm font-medium" htmlFor="plan-mode">
          Mode
        </label>
        <Select value={mode} onValueChange={(v) => onModeChange(v as PlanMode)}>
          <SelectTrigger id="plan-mode" className="w-full">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {(Object.keys(PLAN_MODE_LABEL) as PlanMode[]).map((m) => (
              <SelectItem key={m} value={m}>
                {PLAN_MODE_LABEL[m]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <div className="rounded-md border border-border">
        {steps.map((step, index) => (
          <div
            key={step.templateSectionId}
            draggable
            onDragStart={() => setDragIndex(index)}
            onDragOver={(e) => e.preventDefault()}
            onDrop={() => {
              if (dragIndex === null) return;
              move(dragIndex, index);
              setDragIndex(null);
            }}
            onDragEnd={() => setDragIndex(null)}
            className={cn(
              "flex items-start gap-3 border-b border-border p-3 last:border-b-0",
              dragIndex === index && "opacity-50",
            )}
          >
            <GripVertical className="mt-1.5 size-4 shrink-0 cursor-grab text-muted-foreground" />
            <Checkbox
              className="mt-1.5"
              checked={step.inclus}
              disabled={mode === "TRAME"}
              onCheckedChange={() => toggleInclus(index)}
            />
            <div className="flex-1">
              <div className="flex items-center gap-2">
                <p className="text-sm font-medium">{step.titre}</p>
                <span className="text-xs text-muted-foreground">{step.code}</span>
              </div>
              {mode !== "TRAME" && (
                <Textarea
                  placeholder="Précision supplémentaire pour cette section (facultatif)…"
                  value={step.guidance}
                  onChange={(e) => setGuidance(index, e.target.value)}
                  rows={1}
                  className="mt-1.5 min-h-8 text-sm"
                  disabled={!step.inclus}
                />
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
