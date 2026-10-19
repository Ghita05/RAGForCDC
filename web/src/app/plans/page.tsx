"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Copy, Pencil, Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ApiError, duplicatePlan, listPlans } from "@/lib/api";
import { formatRelative } from "@/lib/format";
import { PLAN_MODE_LABEL } from "@/lib/types";
import type { PlanSummary } from "@/lib/types";

export default function PlansPage() {
  const [plans, setPlans] = useState<PlanSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [duplicatingId, setDuplicatingId] = useState<number | null>(null);

  function refresh() {
    listPlans()
      .then(setPlans)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger les plans."));
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleDuplicate(id: number) {
    setDuplicatingId(id);
    setError(null);
    try {
      await duplicatePlan(id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de la duplication.");
    } finally {
      setDuplicatingId(null);
    }
  }

  return (
    <main className="flex flex-1 flex-col">
      <div className="flex items-center justify-between px-6 py-4">
        <div>
          <h1 className="text-lg font-medium">Plans de génération</h1>
          <p className="text-sm text-muted-foreground">
            Un plan décrit comment générer un CDC à partir d&apos;une structure : quelles sections,
            dans quel ordre, avec quelles précisions. Créez-en un pour le réutiliser d&apos;un CDC
            à l&apos;autre, ou définissez-en un ponctuel directement depuis « Nouveau CDC ».
          </p>
        </div>
        <Button size="sm" asChild>
          <Link href="/plans/nouveau">
            <Plus className="size-3.5" />
            Nouveau plan
          </Link>
        </Button>
      </div>

      {error && (
        <Alert variant="destructive" className="mx-6 mt-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="overflow-x-auto">
        <table className="w-full min-w-[900px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th className="h-11 px-6 font-normal">Nom</th>
              <th className="h-11 px-4 font-normal">Structure</th>
              <th className="h-11 px-4 font-normal">Type</th>
              <th className="h-11 px-4 font-normal">Mode</th>
              <th className="h-11 px-4 font-normal">Sections</th>
              <th className="h-11 px-4 font-normal">Modifié</th>
              <th className="h-11 px-4 font-normal" />
            </tr>
          </thead>
          <tbody>
            {plans === null && !error && (
              <>
                {Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i} className="h-11 border-b border-border">
                    <td className="px-6" colSpan={7}>
                      <Skeleton className="h-4 w-full" />
                    </td>
                  </tr>
                ))}
              </>
            )}

            {plans?.length === 0 && (
              <tr>
                <td colSpan={7} className="py-10 text-center text-muted-foreground">
                  Aucun plan enregistré. Les CDC peuvent toujours être générés avec un plan
                  ponctuel, non sauvegardé.
                </td>
              </tr>
            )}

            {plans?.map((plan) => (
              <tr key={plan.id} className="h-11 border-b border-border hover:bg-secondary/60">
                <td className="px-6 py-0 font-medium">{plan.name}</td>
                <td className="px-4 py-0 text-muted-foreground">{plan.templateName}</td>
                <td className="px-4 py-0 text-muted-foreground lowercase">{plan.cdcType ?? "—"}</td>
                <td className="px-4 py-0 text-muted-foreground">{PLAN_MODE_LABEL[plan.mode]}</td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {plan.includedSteps} / {plan.totalSteps}
                </td>
                <td className="px-4 py-0 text-muted-foreground">{formatRelative(plan.updatedAt)}</td>
                <td className="px-4 py-0">
                  <div className="flex items-center justify-end gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={duplicatingId === plan.id}
                      onClick={() => handleDuplicate(plan.id)}
                    >
                      <Copy className="size-3.5" />
                      {duplicatingId === plan.id ? "Duplication…" : "Dupliquer"}
                    </Button>
                    <Button size="sm" variant="outline" asChild>
                      <Link href={`/plans/${plan.id}`}>
                        <Pencil className="size-3.5" />
                        Éditer
                      </Link>
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  );
}
