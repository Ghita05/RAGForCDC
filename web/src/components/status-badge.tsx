import { cn } from "@/lib/utils";
import type { CdcPhase, SectionStatus } from "@/lib/types";
import { PHASE_LABEL } from "@/lib/types";

// Pastille discrète + libellé, plutôt qu'un badge plein ou une icône : une
// seule couleur d'accent (bleu-ardoise), gris pour le reste, l'état
// "en cours" se distingue par une pastille qui pulse plutôt qu'un spinner.
const SECTION_META: Record<SectionStatus, { label: string; dot: string }> = {
  EMPTY: { label: "Vide", dot: "bg-muted-foreground/30" },
  QUEUED: { label: "En file", dot: "bg-muted-foreground/50" },
  GENERATING: { label: "Génération…", dot: "bg-primary animate-pulse" },
  DRAFT: { label: "Brouillon", dot: "bg-muted-foreground/50" },
  FAILED: { label: "Échec", dot: "bg-destructive" },
  APPROVED: { label: "Validée", dot: "bg-primary" },
};

export function SectionStatusBadge({
  status,
  className,
}: {
  status: SectionStatus;
  className?: string;
}) {
  const meta = SECTION_META[status];
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-xs text-muted-foreground", className)}>
      <span className={cn("size-1.5 shrink-0 rounded-full", meta.dot)} />
      {meta.label}
    </span>
  );
}

const PHASE_DOT: Record<CdcPhase, string> = {
  GENERE: "bg-muted-foreground/50",
  EN_RELECTURE: "bg-primary animate-pulse",
  PARTIELLEMENT_VALIDE: "bg-primary/50",
  VALIDE: "bg-primary",
};

export function CdcStatusBadge({ phase, className }: { phase: CdcPhase; className?: string }) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 text-sm", className)}>
      <span className={cn("size-1.5 shrink-0 rounded-full", PHASE_DOT[phase])} />
      {PHASE_LABEL[phase]}
    </span>
  );
}
