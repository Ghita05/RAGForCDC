"use client";

import { useEffect, useState } from "react";
import { Paintbrush } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiError, listFormatTemplates, setCdcFormatTemplate } from "@/lib/api";
import type { FormatTemplateView } from "@/lib/types";

const DEFAULT_SENTINEL = "default";

/** Sélecteur du modèle de mise en forme utilisé à l'export de CE CDC --
 * "Par défaut" retire l'association (formatTemplateId -> null), voir
 * ExportService.resolveStyle. Valeur toujours définie ("default" plutôt que
 * undefined) : un Select qui oscille entre contrôlé et non contrôlé peut
 * déclencher un onValueChange("") parasite (voir CLAUDE.md). */
export function FormatTemplateSelect({
  cdcId,
  formatTemplateId,
  onChanged,
}: {
  cdcId: number;
  formatTemplateId: number | null;
  onChanged: () => void;
}) {
  const [templates, setTemplates] = useState<FormatTemplateView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    listFormatTemplates()
      .then(setTemplates)
      .catch(() => setError("Impossible de charger les modèles de mise en forme."));
  }, []);

  if (templates !== null && templates.length === 0) return null;

  return (
    <div className="flex items-center gap-1.5">
      <Paintbrush className="size-3.5 shrink-0 text-muted-foreground" />
      <Select
        value={formatTemplateId !== null ? String(formatTemplateId) : DEFAULT_SENTINEL}
        disabled={saving || templates === null}
        onValueChange={async (v) => {
          if (!v) return;
          setSaving(true);
          setError(null);
          try {
            await setCdcFormatTemplate(cdcId, v === DEFAULT_SENTINEL ? null : Number(v));
            onChanged();
          } catch (err) {
            setError(err instanceof ApiError ? err.message : "Échec de l'enregistrement.");
          } finally {
            setSaving(false);
          }
        }}
      >
        <SelectTrigger size="sm" className="w-52 text-sm">
          <SelectValue />
        </SelectTrigger>
        <SelectContent>
          <SelectItem value={DEFAULT_SENTINEL}>Mise en forme par défaut</SelectItem>
          {templates?.map((t) => (
            <SelectItem key={t.summary.id} value={String(t.summary.id)}>
              {t.summary.name}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
      {error && <span className="text-xs text-destructive">{error}</span>}
    </div>
  );
}
