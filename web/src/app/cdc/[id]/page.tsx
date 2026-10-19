"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";
import { Check, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { CdcStatusBadge } from "@/components/status-badge";
import { SectionList } from "@/components/section-list";
import { SectionPanel } from "@/components/section-panel";
import { ReviewDialog } from "@/components/review-dialog";
import { ExportMenu } from "@/components/export-menu";
import { FormatTemplateSelect } from "@/components/format-template-select";
import { CdcEventsTab } from "@/components/cdc-events-tab";
import { ApiError, approveCdc, generatePlan, getCdc } from "@/lib/api";
import { ACTIVE_STATUSES } from "@/lib/api";
import { cdcPhase, estimateGenerationSeconds, typeLabel, type CdcView } from "@/lib/types";

const POLL_INTERVAL_MS = 5000;

export default function CdcDetailPage() {
  const params = useParams<{ id: string }>();
  const cdcId = Number(params.id);

  const [view, setView] = useState<CdcView | null>(null);
  const [selectedOrdre, setSelectedOrdre] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [regenerating, setRegenerating] = useState(false);
  const [approving, setApproving] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const next = await getCdc(cdcId);
      setView(next);
      setError(null);
      setSelectedOrdre((prev) => prev ?? next.sections[0]?.ordre ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Impossible de charger le CDC.");
    }
  }, [cdcId]);

  useEffect(() => {
    void (async () => {
      await refresh();
    })();
  }, [refresh]);

  const hasActiveSection = view?.sections.some((s) => ACTIVE_STATUSES.has(s.status)) ?? false;

  useEffect(() => {
    if (!hasActiveSection) return;
    const interval = setInterval(refresh, POLL_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [hasActiveSection, refresh]);

  if (error && !view) {
    return (
      <main className="flex-1 px-6 py-10">
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      </main>
    );
  }

  if (!view) {
    return (
      <main className="flex-1 px-6 py-10">
        <Skeleton className="mb-6 h-8 w-1/2" />
        <Skeleton className="h-96 w-full" />
      </main>
    );
  }

  const { cdc, sections } = view;
  const obligatoire = sections.filter((s) => s.obligatoire);
  const approvedCount = sections.filter((s) => s.status === "APPROVED").length;
  const allObligatoireApproved =
    obligatoire.length > 0 && obligatoire.every((s) => s.status === "APPROVED");
  const selectedSection = sections.find((s) => s.ordre === selectedOrdre) ?? sections[0];
  const phase = cdcPhase({
    status: cdc.status,
    approvedSections: approvedCount,
    reviewStatus: view.reviewStatus,
  });

  const activeSections = sections.filter((s) => ACTIVE_STATUSES.has(s.status));
  const currentSection = sections.find((s) => s.status === "GENERATING");
  const etaMinutes = Math.max(1, Math.round(estimateGenerationSeconds("SECTIONS", activeSections.length) / 60));

  return (
    <main className="flex flex-1 flex-col px-6 py-6">
      <Link
        href="/"
        className="mb-4 inline-flex w-fit text-xs text-muted-foreground hover:text-foreground"
      >
        Tous les CDC
      </Link>

      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="mb-1.5 flex items-center gap-2">
            <h1 className="text-lg font-medium">{cdc.title}</h1>
            <CdcStatusBadge phase={phase} />
          </div>
          <p className="text-sm text-muted-foreground">
            {typeLabel(cdc.cdcType)} · {approvedCount} / {sections.length} sections validées
            {cdc.planId && cdc.planName && (
              <>
                {" "}
                · plan{" "}
                <Link href="/plans" className="text-foreground hover:underline">
                  {cdc.planName}
                </Link>
              </>
            )}
          </p>
        </div>

        <div className="flex items-center gap-2">
          {cdc.planId && (
            <Button
              size="sm"
              variant="outline"
              disabled={regenerating || cdc.status === "APPROVED"}
              onClick={async () => {
                setRegenerating(true);
                setError(null);
                try {
                  await generatePlan(cdcId, { planId: cdc.planId });
                  await refresh();
                } catch (err) {
                  setError(err instanceof ApiError ? err.message : "Échec du lancement.");
                } finally {
                  setRegenerating(false);
                }
              }}
            >
              <RefreshCw className="size-3.5" />
              {regenerating ? "Lancement…" : "Régénérer"}
            </Button>
          )}

          <ReviewDialog cdcId={cdcId} />

          <FormatTemplateSelect cdcId={cdcId} formatTemplateId={cdc.formatTemplateId} onChanged={refresh} />

          <ExportMenu cdcId={cdcId} />

          <Button
            size="sm"
            disabled={!allObligatoireApproved || approving || cdc.status === "APPROVED"}
            onClick={async () => {
              setApproving(true);
              setError(null);
              try {
                await approveCdc(cdcId);
                await refresh();
              } catch (err) {
                setError(err instanceof ApiError ? err.message : "Échec de la validation.");
              } finally {
                setApproving(false);
              }
            }}
          >
            <Check className="size-3.5" />
            {cdc.status === "APPROVED"
              ? "CDC validé"
              : approving
                ? "Validation…"
                : "Valider le CDC"}
          </Button>
        </div>
      </div>

      {error && (
        <Alert variant="destructive" className="mb-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {activeSections.length > 0 && (
        <div className="mb-4 flex items-center gap-2 rounded-md border border-border bg-secondary/40 px-3 py-2 text-sm text-muted-foreground">
          <span className="size-1.5 shrink-0 animate-pulse rounded-full bg-primary" />
          {currentSection
            ? `Génération en cours : ${currentSection.titre}`
            : "Génération en file d'attente"}
          {" · "}
          {activeSections.length} section{activeSections.length > 1 ? "s" : ""} restante
          {activeSections.length > 1 ? "s" : ""}
          {" · environ "}
          {etaMinutes} minute{etaMinutes > 1 ? "s" : ""}
        </div>
      )}

      {cdc.body && (
        <div className="mb-4 rounded-md border border-border p-3">
          <p className="mb-1.5 text-xs font-medium text-muted-foreground">Trame</p>
          <p className="whitespace-pre-wrap text-sm">{cdc.body}</p>
        </div>
      )}

      <Tabs defaultValue="sections" className="flex-1">
        <TabsList>
          <TabsTrigger value="sections">Sections</TabsTrigger>
          <TabsTrigger value="historique">Historique</TabsTrigger>
        </TabsList>

        <TabsContent value="sections">
          <div className="grid flex-1 grid-cols-[280px_1fr] gap-6">
            <SectionList
              sections={sections}
              selectedOrdre={selectedSection?.ordre ?? null}
              onSelect={setSelectedOrdre}
            />

            {selectedSection && (
              <SectionPanel
                key={selectedSection.ordre}
                cdcId={cdcId}
                section={selectedSection}
                onChanged={refresh}
              />
            )}
          </div>
        </TabsContent>

        <TabsContent value="historique">
          <CdcEventsTab cdcId={cdcId} />
        </TabsContent>
      </Tabs>
    </main>
  );
}
