"use client";

import { FileDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

const FORMATS = [
  { value: "pdf", label: "PDF" },
  { value: "docx", label: "Word (.docx)" },
  { value: "md", label: "Markdown (.md)" },
] as const;

/** Un lien natif avec Content-Disposition: attachment (voir ExportService)
 * plutôt qu'un fetch + blob : le navigateur gère le téléchargement et le
 * nom de fichier sans code supplémentaire côté client. */
export function ExportMenu({ cdcId }: { cdcId: number }) {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button size="sm" variant="outline">
          <FileDown className="size-3.5" />
          Exporter
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {FORMATS.map((f) => (
          <DropdownMenuItem key={f.value} asChild>
            <a href={`/api/cdc/${cdcId}/export?format=${f.value}`}>{f.label}</a>
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
