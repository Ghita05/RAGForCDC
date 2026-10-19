"use client";

import { useState } from "react";
import { Check, RefreshCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { MarkdownView } from "@/components/markdown-view";
import { SectionStatusBadge } from "@/components/status-badge";
import { ApiError, approveSection, generateSection, updateSection } from "@/lib/api";
import type { Section } from "@/lib/types";

const ACTIVE = new Set(["QUEUED", "GENERATING"]);

export function SectionPanel({
  cdcId,
  section,
  onChanged,
}: {
  cdcId: number;
  section: Section;
  onChanged: () => void;
}) {
  // Pas de useState(section.body) + effet pour resynchroniser : le parent
  // rend ce composant avec key={section.ordre}, qui le remonte (et réinitialise
  // tout cet état local) au changement de section. `draft` est dérivé du
  // contenu courant seulement au moment d'entrer en édition, pour rester à
  // jour même si le contenu a changé (régénération) pendant que la section
  // était affichée en lecture seule.
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [busy, setBusy] = useState<"save" | "regenerate" | "approve" | null>(null);
  const [error, setError] = useState<string | null>(null);

  const isActive = ACTIVE.has(section.status);
  const canEdit = section.status !== "APPROVED" && !isActive;
  const canRegenerate = section.status !== "APPROVED" && !isActive;
  const canApprove = section.status === "DRAFT";

  async function run(action: "save" | "regenerate" | "approve", fn: () => Promise<unknown>) {
    setBusy(action);
    setError(null);
    try {
      await fn();
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "L'action a échoué.");
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="flex h-full flex-col">
      <div className="mb-3 flex items-start justify-between gap-4">
        <div>
          <h2 className="text-sm font-medium">
            {section.ordre}. {section.titre}
          </h2>
          <p className="mt-1 text-xs text-muted-foreground">{section.consigne}</p>
        </div>
        <SectionStatusBadge status={section.status} className="shrink-0" />
      </div>

      {section.error && (
        <Alert variant="destructive" className="mb-3">
          <AlertDescription>{section.error}</AlertDescription>
        </Alert>
      )}
      {error && (
        <Alert variant="destructive" className="mb-3">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      <div className="mb-3 flex items-center gap-2">
        {editing ? (
          <>
            <Button
              size="sm"
              onClick={() =>
                run("save", async () => {
                  await updateSection(cdcId, section.ordre, draft);
                  setEditing(false);
                })
              }
              disabled={busy !== null}
            >
              <Check className="size-3.5" />
              {busy === "save" ? "Enregistrement…" : "Enregistrer"}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => setEditing(false)}
              disabled={busy !== null}
            >
              Annuler
            </Button>
          </>
        ) : (
          <Button
            size="sm"
            variant="outline"
            onClick={() => {
              setDraft(section.body ?? "");
              setEditing(true);
            }}
            disabled={!canEdit || busy !== null}
          >
            Modifier
          </Button>
        )}

        <Button
          size="sm"
          variant="outline"
          onClick={() => run("regenerate", () => generateSection(cdcId, section.ordre))}
          disabled={!canRegenerate || busy !== null || editing}
        >
          <RefreshCw className="size-3.5" />
          {busy === "regenerate" ? "Lancement…" : "Régénérer"}
        </Button>

        <Button
          size="sm"
          onClick={() => run("approve", () => approveSection(cdcId, section.ordre))}
          disabled={!canApprove || busy !== null || editing}
        >
          <Check className="size-3.5" />
          {busy === "approve" ? "Validation…" : "Valider la section"}
        </Button>
      </div>

      <div className="min-h-0 flex-1 overflow-y-auto border-t border-border pt-4">
        {editing ? (
          <Textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            className="min-h-[400px] resize-none border-0 p-0 font-mono text-xs shadow-none focus-visible:ring-0"
            autoFocus
          />
        ) : section.body ? (
          <MarkdownView content={section.body} />
        ) : (
          <p className="text-sm text-muted-foreground">
            {isActive ? "Génération en cours…" : "Cette section n'a pas encore été rédigée."}
          </p>
        )}
      </div>
    </div>
  );
}
