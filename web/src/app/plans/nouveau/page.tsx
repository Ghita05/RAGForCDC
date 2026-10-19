"use client";

import { useEffect, useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { PlanForm } from "@/components/plan-form";
import { ApiError, createPlan, listTemplates } from "@/lib/api";
import type { TemplateSummary } from "@/lib/types";

export default function NewPlanPage() {
  const [templates, setTemplates] = useState<TemplateSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listTemplates()
      .then(setTemplates)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger les structures."));
  }, []);

  if (error) {
    return <main className="flex-1 px-6 py-10 text-sm text-destructive">{error}</main>;
  }
  if (!templates) {
    return (
      <main className="flex-1 px-6 py-10">
        <Skeleton className="h-96 w-full" />
      </main>
    );
  }

  return <PlanForm templates={templates} onSubmit={createPlan} />;
}
