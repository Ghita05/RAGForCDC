"use client";

import { useEffect, useState } from "react";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ImportDocumentDialog } from "@/components/import-document-dialog";
import { formatRelative } from "@/lib/format";
import { ApiError, listDocuments } from "@/lib/api";
import type { DocumentSummary } from "@/lib/types";
import { typeLabel } from "@/lib/types";

function num(value: number | null, unit: string, decimals = 0): string {
  return value === null ? "—" : `${value.toFixed(decimals)} ${unit}`;
}

export default function CorpusPage() {
  const [docs, setDocs] = useState<DocumentSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  function refresh() {
    listDocuments()
      .then(setDocs)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : "Impossible de charger le corpus."),
      );
  }

  useEffect(refresh, []);

  return (
    <main className="flex flex-1 flex-col">
      <div className="flex items-center justify-between px-6 py-4">
        <div>
          <h1 className="text-lg font-medium">Corpus</h1>
        </div>
        <ImportDocumentDialog onImported={refresh} />
      </div>

      {error && (
        <Alert variant="destructive" className="mx-6 mt-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="overflow-x-auto border-t border-border">
        <table className="w-full min-w-[1000px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-border text-left text-xs text-muted-foreground">
              <th className="h-11 px-6 font-normal">Fichier</th>
              <th className="h-11 px-4 font-normal">Type</th>
              <th className="h-11 px-4 font-normal">Délai</th>
              <th className="h-11 px-4 font-normal">Pénalité</th>
              <th className="h-11 px-4 font-normal">Plafond</th>
              <th className="h-11 px-4 font-normal">Garantie</th>
              <th className="h-11 px-4 font-normal">SLA</th>
              <th className="h-11 px-4 font-normal">GTR / GTI</th>
              <th className="h-11 px-4 font-normal">Ingéré</th>
            </tr>
          </thead>
          <tbody>
            {docs === null && !error && (
              <>
                {Array.from({ length: 6 }).map((_, i) => (
                  <tr key={i} className="h-11 border-b border-border">
                    <td className="px-6" colSpan={9}>
                      <Skeleton className="h-4 w-full" />
                    </td>
                  </tr>
                ))}
              </>
            )}

            {docs?.length === 0 && (
              <tr>
                <td colSpan={9} className="py-10 text-center text-muted-foreground">
                  Aucun document dans le corpus.
                </td>
              </tr>
            )}

            {docs?.map((doc) => (
              <tr key={doc.id} className="h-11 border-b border-border hover:bg-secondary/60">
                <td className="px-6 py-0 font-medium">{doc.filename}</td>
                <td className="px-4 py-0 text-muted-foreground">{typeLabel(doc.cdcType)}</td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {num(doc.delaiJours, "j")}
                </td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {num(doc.penalitePct, "%", 2)}
                </td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {num(doc.plafondPct, "%", 2)}
                </td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {num(doc.garantieMois, "mois")}
                </td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {num(doc.slaPct, "%", 2)}
                </td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">
                  {doc.gtrHeures === null && doc.gtiHeures === null
                    ? "—"
                    : `${doc.gtrHeures ?? "—"}h / ${doc.gtiHeures ?? "—"}h`}
                </td>
                <td className="px-4 py-0 text-muted-foreground">{formatRelative(doc.createdAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  );
}
