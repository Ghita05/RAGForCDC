// Formatage de dates relatif, sans dépendance (Intl.RelativeTimeFormat est
// natif). "il y a 2 jours" pour un instant ; "2 jours" pour une durée.

const RTF = new Intl.RelativeTimeFormat("fr", { numeric: "auto" });

const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ["year", 365 * 24 * 3600],
  ["month", 30 * 24 * 3600],
  ["week", 7 * 24 * 3600],
  ["day", 24 * 3600],
  ["hour", 3600],
  ["minute", 60],
  ["second", 1],
];

const DURATION_WORD: Partial<Record<Intl.RelativeTimeFormatUnit, string>> = {
  year: "an",
  month: "mois",
  week: "semaine",
  day: "jour",
  hour: "heure",
  minute: "minute",
};

function diffSeconds(iso: string): number {
  return (Date.now() - new Date(iso).getTime()) / 1000;
}

/** "il y a 2 jours", "hier", "à l'instant"… */
export function formatRelative(iso: string): string {
  const diff = diffSeconds(iso);
  for (const [unit, secondsInUnit] of UNITS) {
    const value = Math.floor(diff / secondsInUnit);
    if (value >= 1 || unit === "second") {
      return RTF.format(-value, unit);
    }
  }
  return RTF.format(0, "second");
}

/** "2 jours", "3 heures"… sans préfixe, pour une durée plutôt qu'un instant. */
export function formatDuration(iso: string): string {
  const diff = diffSeconds(iso);
  if (diff < 60) return "moins d'une minute";
  for (const [unit, secondsInUnit] of UNITS) {
    if (unit === "second") break;
    const value = Math.floor(diff / secondsInUnit);
    if (value >= 1) {
      const word = DURATION_WORD[unit] ?? unit;
      const suffix = value > 1 && unit !== "month" ? "s" : "";
      return `${value} ${word}${suffix}`;
    }
  }
  return "moins d'une minute";
}
