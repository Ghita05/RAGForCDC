import { cn } from "@/lib/utils";
import { SectionStatusBadge } from "@/components/status-badge";
import type { Section } from "@/lib/types";

export function SectionList({
  sections,
  selectedOrdre,
  onSelect,
}: {
  sections: Section[];
  selectedOrdre: number | null;
  onSelect: (ordre: number) => void;
}) {
  return (
    <nav className="divide-y divide-border border-r border-border">
      {sections.map((s) => (
        <button
          key={s.ordre}
          type="button"
          onClick={() => onSelect(s.ordre)}
          className={cn(
            "flex w-full flex-col gap-1 px-3 py-2 text-left transition-colors hover:bg-secondary",
            selectedOrdre === s.ordre && "bg-accent",
          )}
        >
          <span className="flex items-center gap-2 text-sm">
            <span className="w-5 shrink-0 text-muted-foreground tabular-nums">{s.ordre}.</span>
            <span className="truncate font-medium">{s.titre}</span>
            {!s.obligatoire && (
              <span className="shrink-0 text-xs text-muted-foreground">(optionnelle)</span>
            )}
          </span>
          <span className="pl-7">
            <SectionStatusBadge status={s.status} />
          </span>
        </button>
      ))}
    </nav>
  );
}
