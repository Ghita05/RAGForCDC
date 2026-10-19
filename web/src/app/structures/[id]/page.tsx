"use client";

import { useCallback, useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { TemplateSectionForm } from "@/components/template-section-form";
import {
  ApiError,
  createTemplateSection,
  deleteTemplateSection,
  getTemplate,
  updateTemplate,
  updateTemplateSection,
} from "@/lib/api";
import type { TemplateView } from "@/lib/types";
import { useCdcTypeSuggestions } from "@/lib/use-cdc-types";

export default function EditStructurePage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const templateId = Number(params.id);

  const [view, setView] = useState<TemplateView | null>(null);
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [cdcType, setCdcType] = useState("");
  const [savingMeta, setSavingMeta] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [metaError, setMetaError] = useState<string | null>(null);
  const cdcTypeSuggestions = useCdcTypeSuggestions();

  const refresh = useCallback(() => {
    getTemplate(templateId)
      .then((v) => {
        setView(v);
        setName(v.template.name);
        setDescription(v.template.description ?? "");
        setCdcType(v.template.cdcType ?? "");
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Impossible de charger la structure."));
  }, [templateId]);

  useEffect(() => {
    refresh();
  }, [refresh]);

  async function handleSaveMeta(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setSavingMeta(true);
    setMetaError(null);
    try {
      await updateTemplate(templateId, {
        name: name.trim(),
        description: description.trim() || null,
        cdcType: cdcType.trim() || null,
      });
      refresh();
    } catch (err) {
      setMetaError(err instanceof ApiError ? err.message : "Échec de l'enregistrement.");
    } finally {
      setSavingMeta(false);
    }
  }

  if (error) {
    return <main className="flex-1 px-6 py-10 text-sm text-destructive">{error}</main>;
  }
  if (!view) {
    return (
      <main className="flex-1 px-6 py-10">
        <Skeleton className="h-96 w-full" />
      </main>
    );
  }

  const nextOrdre = Math.max(0, ...view.sections.map((s) => s.ordre)) + 1;

  return (
    <main className="flex flex-1 flex-col">
      <div className="border-b border-border px-6 py-4">
        <Link
          href="/structures"
          className="mb-2 inline-flex w-fit items-center gap-1 text-xs text-muted-foreground hover:text-foreground"
        >
          <ChevronLeft className="size-3.5" />
          Structures
        </Link>
        <h1 className="text-lg font-medium">{view.template.name}</h1>
        <p className="text-sm text-muted-foreground">
          Modifiez les informations générales et la liste des sections. Chaque section
          s&apos;enregistre indépendamment.
        </p>
      </div>

      <div className="px-6 py-6">
        <div className="mx-auto flex max-w-3xl flex-col gap-8">
          <form onSubmit={handleSaveMeta} className="flex flex-col gap-4 rounded-md border border-border p-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="grid gap-2">
                <Label htmlFor="template-name">Nom</Label>
                <Input id="template-name" value={name} onChange={(e) => setName(e.target.value)} required />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="template-cdc-type">Type de CDC (optionnel)</Label>
                <Input
                  id="template-cdc-type"
                  list="cdc-type-suggestions"
                  value={cdcType}
                  onChange={(e) => setCdcType(e.target.value)}
                  placeholder="ex. réseau"
                />
                <datalist id="cdc-type-suggestions">
                  {cdcTypeSuggestions.map((t) => (
                    <option key={t} value={t} />
                  ))}
                </datalist>
              </div>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="template-description">Description (optionnel)</Label>
              <Textarea
                id="template-description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                rows={2}
              />
            </div>
            {metaError && (
              <Alert variant="destructive">
                <AlertDescription>{metaError}</AlertDescription>
              </Alert>
            )}
            <div className="flex items-center gap-2">
              <Button type="submit" size="sm" disabled={savingMeta || !name.trim()}>
                {savingMeta ? "Enregistrement…" : "Enregistrer les informations"}
              </Button>
              <Button type="button" size="sm" variant="outline" onClick={() => router.push("/structures")}>
                Retour à la liste
              </Button>
            </div>
          </form>

          <div>
            <h2 className="mb-3 text-sm font-medium">Sections ({view.sections.length})</h2>
            <div className="flex flex-col gap-3">
              {view.sections.map((section) => (
                <TemplateSectionForm
                  key={section.id}
                  section={section}
                  nextOrdre={nextOrdre}
                  onSubmit={(req) => updateTemplateSection(templateId, section.id, req).then(refresh)}
                  onDelete={() => deleteTemplateSection(templateId, section.id).then(refresh)}
                />
              ))}
            </div>
          </div>

          <div>
            <h2 className="mb-3 text-sm font-medium">Ajouter une section</h2>
            <TemplateSectionForm
              nextOrdre={nextOrdre}
              onSubmit={(req) => createTemplateSection(templateId, req).then(refresh)}
            />
          </div>
        </div>
      </div>
    </main>
  );
}
