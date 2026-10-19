"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Copy, Pencil, Plus, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ApiError, deleteTemplate, duplicateTemplate, listTemplates } from "@/lib/api";
import type { TemplateSummary } from "@/lib/types";

export default function StructuresPage() {
  const [templates, setTemplates] = useState<TemplateSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  function refresh() {
    listTemplates()
      .then(setTemplates)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger les structures."));
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleDuplicate(id: number) {
    setBusyId(id);
    setError(null);
    try {
      await duplicateTemplate(id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de la duplication.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(t: TemplateSummary) {
    if (!window.confirm(`Supprimer la structure « ${t.name} » ?`)) return;
    setBusyId(t.id);
    setError(null);
    try {
      await deleteTemplate(t.id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de la suppression.");
    } finally {
      setBusyId(null);
    }
  }

  return (
    <main className="flex flex-1 flex-col">
      <div className="flex items-center justify-between px-6 py-4">
        <div>
          <h1 className="text-lg font-medium">Structures</h1>
          <p className="text-sm text-muted-foreground">
            Une structure définit l&apos;organisation d&apos;un CDC : ses sections, leurs titres et
            leurs consignes de base. Créez-en une pour apporter votre propre organisation, ou
            dupliquez une structure existante pour l&apos;adapter. (Distinct d&apos;un{" "}
            <Link href="/mise-en-forme" className="underline">
              modèle de mise en forme
            </Link>
            , qui définit l&apos;apparence du document exporté.)
          </p>
        </div>
        <Button size="sm" asChild>
          <Link href="/structures/nouveau">
            <Plus className="size-3.5" />
            Nouvelle structure
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
              <th className="h-11 px-4 font-normal">Type</th>
              <th className="h-11 px-4 font-normal">Sections</th>
              <th className="h-11 px-4 font-normal" />
              <th className="h-11 px-4 font-normal" />
            </tr>
          </thead>
          <tbody>
            {templates === null && !error && (
              <>
                {Array.from({ length: 3 }).map((_, i) => (
                  <tr key={i} className="h-11 border-b border-border">
                    <td className="px-6" colSpan={5}>
                      <Skeleton className="h-4 w-full" />
                    </td>
                  </tr>
                ))}
              </>
            )}

            {templates?.map((t) => (
              <tr key={t.id} className="h-11 border-b border-border hover:bg-secondary/60">
                <td className="px-6 py-0 font-medium">
                  {t.name}
                  {t.isDefault && (
                    <span className="ml-2 rounded-sm border border-border px-1.5 py-0.5 text-xs font-normal text-muted-foreground">
                      par défaut
                    </span>
                  )}
                </td>
                <td className="px-4 py-0 text-muted-foreground lowercase">{t.cdcType ?? "—"}</td>
                <td className="px-4 py-0 tabular-nums text-muted-foreground">{t.sectionCount}</td>
                <td className="px-4 py-0">
                  <div className="flex items-center justify-end gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      disabled={busyId === t.id}
                      onClick={() => handleDuplicate(t.id)}
                    >
                      <Copy className="size-3.5" />
                      Dupliquer
                    </Button>
                    <Button size="sm" variant="outline" asChild>
                      <Link href={`/structures/${t.id}`}>
                        <Pencil className="size-3.5" />
                        Éditer
                      </Link>
                    </Button>
                  </div>
                </td>
                <td className="px-4 py-0 text-right">
                  <Button
                    size="sm"
                    variant="destructive"
                    disabled={busyId === t.id || t.isDefault}
                    title={t.isDefault ? "La structure par défaut ne peut pas être supprimée" : undefined}
                    onClick={() => handleDelete(t)}
                  >
                    <Trash2 className="size-3.5" />
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </main>
  );
}
