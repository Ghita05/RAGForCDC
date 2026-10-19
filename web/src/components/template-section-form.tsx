"use client";

import { useId, useState } from "react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { ApiError } from "@/lib/api";
import type { SectionRequest, TemplateSection } from "@/lib/types";

interface Props {
  /** undefined = formulaire d'ajout d'une nouvelle section. */
  section?: TemplateSection;
  nextOrdre: number;
  onSubmit: (req: SectionRequest) => Promise<unknown>;
  /** Absent sur le formulaire d'ajout : une section qui n'existe pas encore
   * ne peut pas être supprimée. */
  onDelete?: () => Promise<unknown>;
}

/** Remplace l'ancienne modale (5 champs, trop pour une fenêtre étroite) --
 * une carte directement dans la page, une par section plus une pour
 * l'ajout. Chaque section se sauvegarde indépendamment, comme avant. */
export function TemplateSectionForm({ section, nextOrdre, onSubmit, onDelete }: Props) {
  const id = useId();
  const isNew = section === undefined;
  const [ordre, setOrdre] = useState(section?.ordre ?? nextOrdre);
  const [code, setCode] = useState(section?.code ?? "");
  const [titre, setTitre] = useState(section?.titre ?? "");
  const [consigne, setConsigne] = useState(section?.consigne ?? "");
  const [obligatoire, setObligatoire] = useState(section?.obligatoire ?? true);
  const [submitting, setSubmitting] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!code.trim() || !titre.trim() || !consigne.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await onSubmit({ ordre, code: code.trim(), titre: titre.trim(), consigne: consigne.trim(), obligatoire });
      if (isNew) {
        setCode("");
        setTitre("");
        setConsigne("");
        setObligatoire(true);
        setOrdre((o) => o + 1);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de l'enregistrement.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete() {
    if (!onDelete) return;
    if (!window.confirm(`Supprimer la section « ${titre} » ?`)) return;
    setDeleting(true);
    setError(null);
    try {
      await onDelete();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de la suppression.");
      setDeleting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="rounded-md border border-border p-3">
      <div className="grid grid-cols-[80px_1fr_2fr] gap-3">
        <div className="grid gap-1.5">
          <Label htmlFor={`${id}-ordre`} className="text-xs text-muted-foreground">Ordre</Label>
          <Input
            id={`${id}-ordre`}
            type="number"
            min={1}
            value={ordre}
            onChange={(e) => setOrdre(Number(e.target.value))}
            className="h-8"
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`${id}-code`} className="text-xs text-muted-foreground">Code</Label>
          <Input
            id={`${id}-code`}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="ex. livrables"
            disabled={!isNew}
            className="h-8"
            required
          />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor={`${id}-titre`} className="text-xs text-muted-foreground">Titre</Label>
          <Input id={`${id}-titre`} value={titre} onChange={(e) => setTitre(e.target.value)} className="h-8" required />
        </div>
      </div>

      <div className="mt-3 grid gap-1.5">
        <Label htmlFor={`${id}-consigne`} className="text-xs text-muted-foreground">Consigne</Label>
        <Textarea id={`${id}-consigne`} value={consigne} onChange={(e) => setConsigne(e.target.value)} rows={2} required />
      </div>

      <div className="mt-3 flex items-center justify-between">
        <label className="flex items-center gap-2 text-sm">
          <Checkbox checked={obligatoire} onCheckedChange={() => setObligatoire((o) => !o)} />
          Section obligatoire
        </label>
        <div className="flex items-center gap-2">
          {onDelete && (
            <Button type="button" size="sm" variant="destructive" onClick={handleDelete} disabled={deleting}>
              {deleting ? "Suppression…" : "Supprimer"}
            </Button>
          )}
          <Button
            type="submit"
            size="sm"
            disabled={submitting || !code.trim() || !titre.trim() || !consigne.trim()}
          >
            {submitting ? "Enregistrement…" : isNew ? "Ajouter la section" : "Enregistrer"}
          </Button>
        </div>
      </div>
      {error && <p className="mt-2 text-sm text-destructive">{error}</p>}
    </form>
  );
}
