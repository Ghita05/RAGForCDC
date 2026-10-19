"use client";

import { useEffect, useState } from "react";
import { listCdcTypes } from "@/lib/api";
import { CDC_TYPES } from "@/lib/types";

/** Types de CDC réellement présents en base (cdc + document), voir
 * CdcRepository.findDistinctTypes -- alimente un filtre : ne doit montrer
 * que ce qui existe réellement, jamais une catégorie vide. */
export function useCdcTypes(): string[] {
  const [types, setTypes] = useState<string[]>([]);
  useEffect(() => {
    listCdcTypes()
      .then(setTypes)
      .catch(() => {});
  }, []);
  return types;
}

/** useCdcTypes() complété des types courants (CDC_TYPES), même absents de la
 * base -- pour qu'un sélecteur de CRÉATION reste utile avant le tout premier
 * CDC de ce type. L'inverse du filtre de liste (useCdcTypes), qui ne doit
 * montrer que ce qui existe déjà. */
export function useCdcTypeSuggestions(): string[] {
  const dbTypes = useCdcTypes();
  return Array.from(new Set([...CDC_TYPES.map((t) => t.value), ...dbTypes])).sort();
}
