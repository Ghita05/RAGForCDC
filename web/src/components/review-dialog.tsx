"use client";

import { useEffect, useRef, useState } from "react";
import { ClipboardCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { cn } from "@/lib/utils";
import { ApiError, getReview, startReview } from "@/lib/api";
import {
  parseFindings,
  type ConstatSeverite,
  type Ecart,
  type ReviewConstat,
  type ReviewFindings,
} from "@/lib/types";

const CATEGORIE_LABEL: Record<string, string> = {
  ecart_norme: "Écart aux normes",
  section_manquante: "Section manquante",
  contradiction: "Contradiction",
  imprecision: "Imprécision",
};

const SEVERITE_STYLE: Record<ConstatSeverite, string> = {
  haute: "border-destructive/40 text-destructive",
  moyenne: "border-primary/40 text-primary",
  basse: "border-border text-muted-foreground",
};

/** Nombre arrondi affiché sans décimale inutile (45 plutôt que 45.0). */
function fmtNum(n: number): string {
  return n === Math.round(n) ? String(Math.round(n)) : n.toFixed(2);
}

/** Le constat "écart de norme" est rédigé par le LLM (titre/explication en
 * prose) mais les chiffres viennent de DeviationChecker, côté Java --
 * jamais l'inverse. Comme le JSON ne relie pas explicitement les deux, on
 * retrouve l'écart correspondant par le nom du champ (« Délai d'exécution »,
 * « Pénalité de retard », « Garantie »), présent dans le titre ou
 * l'explication puisque c'est ce texte qui a été donné au modèle. */
function findEcart(ecarts: Ecart[] | undefined, constat: ReviewConstat): Ecart | undefined {
  if (!ecarts || constat.categorie !== "ecart_norme") return undefined;
  const haystack = (constat.titre + " " + constat.explication).toLowerCase();
  return ecarts.find((e) => haystack.includes(e.champ.toLowerCase()));
}

function EcartEvidence({ ecart }: { ecart: Ecart }) {
  return (
    <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 rounded-sm bg-muted/60 px-2.5 py-1.5 text-xs text-muted-foreground">
      <span>
        Valeur du CDC : <span className="font-medium text-foreground">{fmtNum(ecart.valeur)} {ecart.unite}</span>
      </span>
      <span>
        Médiane historique :{" "}
        <span className="font-medium text-foreground">{fmtNum(ecart.median)} {ecart.unite}</span>
      </span>
      <span>
        Bornes :{" "}
        <span className="font-medium text-foreground">
          {fmtNum(ecart.min)}–{fmtNum(ecart.max)} {ecart.unite}
        </span>
      </span>
      <span>
        Échantillon : <span className="font-medium text-foreground">n = {ecart.n}</span>
      </span>
    </div>
  );
}

function ConstatCard({ constat, ecarts }: { constat: ReviewConstat; ecarts?: Ecart[] }) {
  const ecart = findEcart(ecarts, constat);
  return (
    <div className="rounded-md border p-3">
      <div className="mb-1.5 flex items-center gap-2">
        <span
          className={cn(
            "rounded-sm border px-1.5 py-0.5 text-xs font-medium capitalize",
            SEVERITE_STYLE[constat.severite],
          )}
        >
          {constat.severite}
        </span>
        <span className="text-xs text-muted-foreground">
          {CATEGORIE_LABEL[constat.categorie] ?? constat.categorie}
        </span>
      </div>
      <p className="text-sm font-medium">{constat.titre}</p>
      <p className="mt-1 text-sm text-muted-foreground">{constat.explication}</p>
      {ecart && <EcartEvidence ecart={ecart} />}
      <p className="mt-2 text-sm">
        <span className="font-medium">Suggestion : </span>
        {constat.suggestion}
      </p>
    </div>
  );
}

export function ReviewDialog({ cdcId }: { cdcId: number }) {
  const [open, setOpen] = useState(false);
  const [starting, setStarting] = useState(false);
  const [status, setStatus] = useState<"RUNNING" | "DONE" | "FAILED" | null>(null);
  const [findings, setFindings] = useState<ReviewFindings | null>(null);
  const [error, setError] = useState<string | null>(null);
  const reviewIdRef = useRef<number | null>(null);

  async function handleStart() {
    setStarting(true);
    setError(null);
    setFindings(null);
    setStatus(null);
    try {
      const result = await startReview(cdcId);
      reviewIdRef.current = result.reviewId;
      setStatus("RUNNING");
      setOpen(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Impossible de lancer la relecture.");
      setOpen(true);
    } finally {
      setStarting(false);
    }
  }

  useEffect(() => {
    if (status !== "RUNNING" || reviewIdRef.current === null) return;
    const id = reviewIdRef.current;
    const interval = setInterval(async () => {
      try {
        const review = await getReview(id);
        if (review.status === "DONE") {
          setFindings(parseFindings(review.findings));
          setStatus("DONE");
        } else if (review.status === "FAILED") {
          setError(review.error ?? "La relecture a échoué.");
          setStatus("FAILED");
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Impossible de récupérer la relecture.");
        setStatus("FAILED");
      }
    }, 5000);
    return () => clearInterval(interval);
  }, [status]);

  return (
    <>
      <Button size="sm" variant="outline" onClick={handleStart} disabled={starting}>
        <ClipboardCheck className="size-3.5" />
        {starting ? "Lancement…" : "Lancer une relecture"}
      </Button>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-h-[85vh] max-w-2xl overflow-y-auto">
          <DialogHeader>
            <DialogTitle>Relecture du CDC</DialogTitle>
          </DialogHeader>

          {status === "RUNNING" && (
            <div className="flex items-center gap-2 py-8 text-sm text-muted-foreground">
              <span className="size-1.5 shrink-0 animate-pulse rounded-full bg-primary" />
              Analyse en cours… le modèle compare le CDC aux normes historiques.
            </div>
          )}

          {error && (
            <Alert variant="destructive">
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}

          {status === "DONE" && findings && (
            <div className="space-y-4">
              <p className="text-sm text-muted-foreground">{findings.resume}</p>
              {findings.echantillon && (
                <p className="rounded-sm border border-border px-2.5 py-1.5 text-xs text-muted-foreground">
                  {findings.echantillon.n < 3
                    ? `Échantillon insuffisant pour établir des normes fiables (n = ${findings.echantillon.n}${findings.echantillon.cdcType ? `, type ${findings.echantillon.cdcType}` : ""}).`
                    : `Normes calculées sur ${findings.echantillon.n} document${findings.echantillon.n > 1 ? "s" : ""} de référence${findings.echantillon.cdcType ? ` de type ${findings.echantillon.cdcType}` : ""}.`}
                </p>
              )}
              {findings.constats.length === 0 ? (
                <p className="text-sm">Aucun constat à signaler.</p>
              ) : (
                <div className="space-y-2">
                  {findings.constats.map((c, i) => (
                    <ConstatCard key={i} constat={c} ecarts={findings.ecarts} />
                  ))}
                </div>
              )}
            </div>
          )}

          {status === "DONE" && !findings && !error && (
            <p className="text-sm text-muted-foreground">
              La relecture est terminée mais n&apos;a renvoyé aucun résultat exploitable.
            </p>
          )}
        </DialogContent>
      </Dialog>
    </>
  );
}
