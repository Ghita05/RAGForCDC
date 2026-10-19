"use client";

import { useEffect, useState } from "react";
import { ApiError, getCdcEvents } from "@/lib/api";
import { formatRelative } from "@/lib/format";
import type { CdcEvent } from "@/lib/types";

const EVENT_LABEL: Record<string, string> = {
  PLAN_EXECUTED: "Exécution d'un plan",
};

interface PlanSnapshotStep {
  ordre: number;
  code: string;
  titre: string;
  guidance: string | null;
  inclus: boolean;
}

interface PlanSnapshot {
  planId: number | null;
  mode: string;
  steps: PlanSnapshotStep[];
}

function parseSnapshot(payload: string): PlanSnapshot | null {
  try {
    return JSON.parse(payload) as PlanSnapshot;
  } catch {
    return null;
  }
}

/** Un plan reste modifiable après exécution : l'instantané complet (sections,
 * ordre, guidance, mode), pris au moment de l'exécution, garde l'historique
 * exact même si le plan change ensuite -- voir PlanExecutionService. */
function EventCard({ event }: { event: CdcEvent }) {
  const snapshot = event.type === "PLAN_EXECUTED" ? parseSnapshot(event.payload) : null;
  return (
    <div className="rounded-md border border-border p-3">
      <div className="mb-2 flex items-center justify-between">
        <span className="text-sm font-medium">{EVENT_LABEL[event.type] ?? event.type}</span>
        <span className="text-xs text-muted-foreground">{formatRelative(event.createdAt)}</span>
      </div>
      {snapshot ? (
        <div className="space-y-1.5">
          <p className="text-xs text-muted-foreground">Mode : {snapshot.mode}</p>
          <ul className="space-y-1">
            {snapshot.steps.map((s) => (
              <li key={s.ordre} className="flex items-baseline gap-2 text-sm">
                <span className="w-5 shrink-0 text-muted-foreground tabular-nums">{s.ordre}.</span>
                <span className={s.inclus ? "" : "text-muted-foreground line-through"}>{s.titre}</span>
                {s.guidance && (
                  <span className="truncate text-xs text-muted-foreground">— {s.guidance}</span>
                )}
              </li>
            ))}
          </ul>
        </div>
      ) : (
        <pre className="overflow-x-auto text-xs text-muted-foreground">{event.payload}</pre>
      )}
    </div>
  );
}

export function CdcEventsTab({ cdcId }: { cdcId: number }) {
  const [events, setEvents] = useState<CdcEvent[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getCdcEvents(cdcId)
      .then(setEvents)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger l'historique."));
  }, [cdcId]);

  if (error) return <p className="text-sm text-destructive">{error}</p>;
  if (events === null) return <p className="text-sm text-muted-foreground">Chargement…</p>;
  if (events.length === 0) {
    return <p className="text-sm text-muted-foreground">Aucun événement pour ce CDC.</p>;
  }

  return (
    <div className="space-y-2">
      {events.map((e) => (
        <EventCard key={e.id} event={e} />
      ))}
    </div>
  );
}
