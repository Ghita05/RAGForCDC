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
import { ApiError, uploadFormatTemplate } from "@/lib/api";

export function ImportFormatTemplateDialog({ onImported }: { onImported: () => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!file || !name.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await uploadFormatTemplate(name.trim(), file);
      setOpen(false);
      setName("");
      setFile(null);
      onImported();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Échec du téléversement.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm">
          <Upload className="size-3.5" />
          Téléverser un modèle
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Téléverser un modèle de mise en forme</DialogTitle>
            <DialogDescription>
              Un .docx existant : polices, styles de titres, marges et couleurs sont repris pour
              l&apos;export. Une page de garde arbitraire avec mise en page complexe n&apos;est pas
              reproduite à l&apos;identique -- seuls les styles le sont.
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="format-template-name">Nom</Label>
              <Input id="format-template-name" value={name} onChange={(e) => setName(e.target.value)} required />
            </div>

            <div className="grid gap-2">
              <Label htmlFor="format-template-file">Fichier (.docx)</Label>
              <Input
                id="format-template-file"
                type="file"
                accept=".docx"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                required
              />
            </div>

            {error && <p className="text-sm text-destructive">{error}</p>}
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)} disabled={submitting}>
              Annuler
            </Button>
            <Button type="submit" disabled={submitting || !file || !name.trim()}>
              {submitting ? "Téléversement…" : "Téléverser"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
