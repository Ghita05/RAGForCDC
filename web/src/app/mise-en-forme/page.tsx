"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { Star, Trash2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ImportFormatTemplateDialog } from "@/components/import-format-template-dialog";
import { ApiError, deleteFormatTemplate, listFormatTemplates, setDefaultFormatTemplate } from "@/lib/api";
import { formatRelative } from "@/lib/format";
import type { FormatTemplateView } from "@/lib/types";

/** twips (1/1440 pouce) -> cm, pour un affichage lisible des marges détectées. */
function twipsToCm(twips: number): string {
  return (twips / 1440 * 2.54).toFixed(1);
}

function Swatch({ label, hex }: { label: string; hex: string }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
      <span className="size-3 shrink-0 rounded-sm border border-border" style={{ backgroundColor: `#${hex}` }} />
      {label}
    </span>
  );
}

function FormatTemplateCard({
  view,
  busy,
  onSetDefault,
  onDelete,
}: {
  view: FormatTemplateView;
  busy: boolean;
  onSetDefault: () => void;
  onDelete: () => void;
}) {
  const { summary, style } = view;
  return (
    <div className="rounded-md border border-border p-4">
      <div className="mb-3 flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <p className="font-medium">{summary.name}</p>
            {summary.isDefault && (
              <span className="rounded-sm border border-border px-1.5 py-0.5 text-xs text-muted-foreground">
                par défaut
              </span>
            )}
          </div>
          <p className="text-xs text-muted-foreground">
            {summary.filename} · téléversé {formatRelative(summary.createdAt)}
          </p>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {!summary.isDefault && (
            <Button size="sm" variant="outline" disabled={busy} onClick={onSetDefault}>
              <Star className="size-3.5" />
              Définir par défaut
            </Button>
          )}
          <Button size="sm" variant="destructive" disabled={busy} onClick={onDelete}>
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-muted-foreground">
        <span>
          Police : <span className="text-foreground">{style.fontFamily}</span>, {style.bodyFontSizePt} pt
        </span>
        <Swatch label={`Titre 1 (${style.heading1FontSizePt} pt)`} hex={style.heading1Color} />
        <Swatch label={`Titre 2 (${style.heading2FontSizePt} pt)`} hex={style.heading2Color} />
        <Swatch label="Accent" hex={style.accentColor} />
        <span>
          Marges :{" "}
          <span className="text-foreground">
            {twipsToCm(style.marginTopTwips)} / {twipsToCm(style.marginRightTwips)} /{" "}
            {twipsToCm(style.marginBottomTwips)} / {twipsToCm(style.marginLeftTwips)} cm
          </span>
        </span>
      </div>
    </div>
  );
}

export default function MiseEnFormePage() {
  const [templates, setTemplates] = useState<FormatTemplateView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  function refresh() {
    listFormatTemplates()
      .then(setTemplates)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger les modèles."));
  }

  useEffect(() => {
    refresh();
  }, []);

  async function handleSetDefault(id: number) {
    setBusyId(id);
    setError(null);
    try {
      await setDefaultFormatTemplate(id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de l'opération.");
    } finally {
      setBusyId(null);
    }
  }

  async function handleDelete(view: FormatTemplateView) {
    if (!window.confirm(`Supprimer le modèle de mise en forme « ${view.summary.name} » ?`)) return;
    setBusyId(view.summary.id);
    setError(null);
    try {
      await deleteFormatTemplate(view.summary.id);
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
          <h1 className="text-lg font-medium">Mise en forme</h1>
          <p className="text-sm text-muted-foreground">
            Un modèle de mise en forme définit l&apos;apparence d&apos;un CDC exporté (polices,
            couleurs, marges) -- à distinguer d&apos;une{" "}
            <Link href="/structures" className="underline">
              structure
            </Link>
            , qui définit ses sections. Téléversez un .docx existant, ou laissez l&apos;habillage
            par défaut. Un modèle peut être défini par défaut, ou associé à un CDC précis depuis sa
            page de détail.
          </p>
        </div>
        <ImportFormatTemplateDialog onImported={refresh} />
      </div>

      {error && (
        <Alert variant="destructive" className="mx-6 mt-4">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="flex flex-col gap-3 px-6 py-4">
        {templates === null && !error && (
          <>
            {Array.from({ length: 2 }).map((_, i) => (
              <Skeleton key={i} className="h-24 w-full" />
            ))}
          </>
        )}

        {templates?.length === 0 && (
          <p className="py-10 text-center text-sm text-muted-foreground">
            Aucun modèle de mise en forme téléversé. Les exports utilisent l&apos;habillage par
            défaut.
          </p>
        )}

        {templates?.map((view) => (
          <FormatTemplateCard
            key={view.summary.id}
            view={view}
            busy={busyId === view.summary.id}
            onSetDefault={() => handleSetDefault(view.summary.id)}
            onDelete={() => handleDelete(view)}
          />
        ))}
      </div>
    </main>
  );
}
