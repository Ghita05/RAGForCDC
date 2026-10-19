"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { Skeleton } from "@/components/ui/skeleton";
import { PlanForm } from "@/components/plan-form";
import { ApiError, getPlan, listTemplates, updatePlan } from "@/lib/api";
import type { PlanView, TemplateSummary } from "@/lib/types";

export default function EditPlanPage() {
  const params = useParams<{ id: string }>();
  const planId = Number(params.id);

  const [templates, setTemplates] = useState<TemplateSummary[] | null>(null);
  const [plan, setPlan] = useState<PlanView | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([listTemplates(), getPlan(planId)])
      .then(([t, p]) => {
        setTemplates(t);
        setPlan(p);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger le plan."));
  }, [planId]);

  if (error) {
    return <main className="flex-1 px-6 py-10 text-sm text-destructive">{error}</main>;
  }
  if (!templates || !plan) {
    return (
      <main className="flex-1 px-6 py-10">
        <Skeleton className="h-96 w-full" />
      </main>
    );
  }

  return <PlanForm templates={templates} plan={plan} onSubmit={(req) => updatePlan(planId, req)} />;
}
