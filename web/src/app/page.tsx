"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { Plus } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { CdcStatusBadge } from "@/components/status-badge";
import { formatDuration, formatRelative } from "@/lib/format";
import { ApiError, listCdcs } from "@/lib/api";
import type { CdcSummary, CdcPhase } from "@/lib/types";
import { PHASE_LABEL, cdcPhase, typeLabel } from "@/lib/types";
import { useCdcTypes } from "@/lib/use-cdc-types";

function ProgressCell({ approved, total }: { approved: number; total: number }) {
  const pct = total > 0 ? Math.round((approved / total) * 100) : 0;
  return (
    <div className="flex items-center gap-2.5">
      <div className="h-1.5 w-20 shrink-0 rounded-full bg-muted">
        <div className="h-1.5 rounded-full bg-primary" style={{ width: `${pct}%` }} />
      </div>
      <span className="tabular-nums text-muted-foreground">
        {approved} / {total}
      </span>
    </div>
  );
}

export default function CdcListPage() {
  const [cdcs, setCdcs] = useState<CdcSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [phaseFilter, setPhaseFilter] = useState<"all" | CdcPhase>("all");
  const [typeFilter, setTypeFilter] = useState<string>("all");
  const cdcTypes = useCdcTypes();

  useEffect(() => {
    listCdcs()
      .then(setCdcs)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Impossible de charger les CDC."),
      );
  }, []);

  const filtered = useMemo(() => {
    if (!cdcs) return null;
    return cdcs.filter((cdc) => {
      if (search.trim() && !cdc.title.toLowerCase().includes(search.trim().toLowerCase())) {
        return false;
      }
      if (phaseFilter !== "all" && cdcPhase(cdc) !== phaseFilter) return false;
      if (typeFilter !== "all" && cdc.cdcType !== typeFilter) return false;
      return true;
    });
  }, [cdcs, search, phaseFilter, typeFilter]);

  return (
    <main className="flex flex-1 flex-col">
      <div className="flex items-center justify-between px-6 py-4">
        <div>
          <h1 className="text-lg font-medium">Cahiers des charges</h1>
          <p className="text-sm text-muted-foreground">
            Liste des CDC en cours ou validés. Un CDC se génère à partir d&apos;une{" "}
            <Link href="/structures" className="underline">
              structure
            </Link>{" "}
            et, en option, d&apos;un{" "}
            <Link href="/plans" className="underline">
              plan
            </Link>{" "}
            réutilisable.
          </p>
        </div>
        <Button size="sm" asChild>
          <Link href="/cdc/nouveau">
            <Plus className="size-3.5" />
            Nouveau CDC
          </Link>
        </Button>
      </div>

      <div className="flex items-center gap-2 border-b border-border px-6 py-2">
        <Input
          placeholder="Rechercher un CDC…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="h-8 max-w-64 text-sm"
        />
        <Select value={phaseFilter} onValueChange={(v) => setPhaseFilter(v as typeof phaseFilter)}>
          <SelectTrigger size="sm" className="w-44 text-sm">
            <SelectValue placeholder="Statut" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les statuts</SelectItem>
            {(Object.keys(PHASE_LABEL) as CdcPhase[]).map((phase) => (
              <SelectItem key={phase} value={phase}>
                {PHASE_LABEL[phase]}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Select value={typeFilter} onValueChange={setTypeFilter}>
          <SelectTrigger size="sm" className="w-40 text-sm">
            <SelectValue placeholder="Type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Tous les types</SelectItem>
            {cdcTypes.map((t) => (
              <SelectItem key={t} value={t}>
                {typeLabel(t)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
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
              <th className="h-11 px-6 font-normal">Titre</th>
              <th className="h-11 px-4 font-normal">Type</th>
              <th className="h-11 px-4 font-normal">Statut</th>
              <th className="h-11 px-4 font-normal">Avancement</th>
              <th className="h-11 px-4 font-normal">Créé</th>
              <th className="h-11 px-4 font-normal">Modifié</th>
              <th className="h-11 px-4 font-normal">Depuis</th>
            </tr>
          </thead>
          <tbody>
            {cdcs === null && !error && (
              <>
                {Array.from({ length: 4 }).map((_, i) => (
                  <tr key={i} className="h-11 border-b border-border">
                    <td className="px-6" colSpan={7}>
                      <Skeleton className="h-4 w-full" />
                    </td>
                  </tr>
                ))}
              </>
            )}

            {filtered?.length === 0 && cdcs?.length !== 0 && (
              <tr>
                <td colSpan={7} className="py-10 text-center text-muted-foreground">
                  Aucun CDC ne correspond à ces critères.
                </td>
              </tr>
            )}

            {cdcs?.length === 0 && (
              <tr>
                <td colSpan={7} className="py-10 text-center text-muted-foreground">
                  Aucun CDC pour l&apos;instant. Commencez par « Nouveau CDC », en haut à droite.
                </td>
              </tr>
            )}

            {filtered?.map((cdc) => {
              const phase = cdcPhase(cdc);
              const sinceIso = cdc.approvedAt ?? cdc.createdAt;
              return (
                <tr key={cdc.id} className="h-11 border-b border-border hover:bg-secondary/60">
                  <td className="px-6 py-0">
                    <Link href={`/cdc/${cdc.id}`} className="block font-medium">
                      {cdc.title}
                    </Link>
                  </td>
                  <td className="px-4 py-0 text-muted-foreground">{typeLabel(cdc.cdcType)}</td>
                  <td className="px-4 py-0">
                    <CdcStatusBadge phase={phase} className="text-sm" />
                  </td>
                  <td className="px-4 py-0">
                    <ProgressCell approved={cdc.approvedSections} total={cdc.totalSections} />
                  </td>
                  <td className="px-4 py-0 text-muted-foreground">{formatRelative(cdc.createdAt)}</td>
                  <td className="px-4 py-0 text-muted-foreground">{formatRelative(cdc.lastModifiedAt)}</td>
                  <td className="px-4 py-0 text-muted-foreground">{formatDuration(sinceIso)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </main>
  );
}
