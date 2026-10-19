"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ChevronLeft } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { ApiError, createTemplate } from "@/lib/api";
import { useCdcTypeSuggestions } from "@/lib/use-cdc-types";

export default function NewStructurePage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [cdcType, setCdcType] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const cdcTypeSuggestions = useCdcTypeSuggestions();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      const view = await createTemplate({
        name: name.trim(),
        description: description.trim() || null,
        cdcType: cdcType.trim() || null,
      });
      router.push(`/structures/${view.template.id}`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de la création de la structure.");
      setSubmitting(false);
    }
  }

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
        <h1 className="text-lg font-medium">Nouvelle structure</h1>
        <p className="text-sm text-muted-foreground">
          Nommez la structure, puis ajoutez ses sections une fois créée.
        </p>
      </div>

      <form onSubmit={handleSubmit} className="px-6 py-6">
        <div className="mx-auto flex max-w-2xl flex-col gap-4">
          <div className="grid gap-2">
            <Label htmlFor="template-name">Nom</Label>
            <Input id="template-name" value={name} onChange={(e) => setName(e.target.value)} required />
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

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          <div className="flex items-center gap-2">
            <Button type="submit" disabled={submitting || !name.trim()}>
              {submitting ? "Création…" : "Créer et définir les sections"}
            </Button>
            <Button type="button" variant="outline" onClick={() => router.push("/structures")} disabled={submitting}>
              Annuler
            </Button>
          </div>
        </div>
      </form>
    </main>
  );
}
