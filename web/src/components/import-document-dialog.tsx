"use client";

import { useState } from "react";
import { Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ApiError, ingestFile } from "@/lib/api";
import { typeLabel } from "@/lib/types";
import { useCdcTypeSuggestions } from "@/lib/use-cdc-types";

export function ImportDocumentDialog({ onImported }: { onImported: () => void }) {
  const [open, setOpen] = useState(false);
  const [file, setFile] = useState<File | null>(null);
  const [cdcType, setCdcType] = useState<string>("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const typeSuggestions = useCdcTypeSuggestions();

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!file || !cdcType) return;
    setSubmitting(true);
    setError(null);
    try {
      await ingestFile(file, cdcType);
      setOpen(false);
      setFile(null);
      setCdcType("");
      onImported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec de l'import.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Upload className="size-3.5" />
          Importer un document
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Importer un document</DialogTitle>
            <DialogDescription>
              PDF, DOCX ou texte brut. Le document est découpé, vectorisé et ses valeurs
              contractuelles sont extraites automatiquement.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="file">Fichier</Label>
              <Input
                id="file"
                type="file"
                accept=".pdf,.docx,.txt"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                required
              />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="doc-type">Type</Label>
              <Select value={cdcType} onValueChange={setCdcType}>
                <SelectTrigger id="doc-type" className="w-full">
                  <SelectValue placeholder="Sélectionner un type" />
                </SelectTrigger>
                <SelectContent>
                  {typeSuggestions.map((t) => (
                    <SelectItem key={t} value={t}>
                      {typeLabel(t)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={submitting}>
              Annuler
            </Button>
            <Button type="submit" disabled={submitting || !file || !cdcType}>
              {submitting ? "Import…" : "Importer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
