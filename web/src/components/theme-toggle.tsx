"use client";

import { useEffect, useState } from "react";
import { useTheme } from "next-themes";
import { Moon, Sun } from "lucide-react";
import { cn } from "@/lib/utils";

export function ThemeToggle() {
  const { theme, setTheme } = useTheme();
  // Évite un mismatch d'hydratation : le thème résolu côté client (localStorage)
  // n'est connu qu'après montage.
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    void (async () => setMounted(true))();
  }, []);

  const options = [
    { value: "light", label: "Clair", icon: Sun },
    { value: "dark", label: "Sombre", icon: Moon },
  ] as const;

  return (
    <div className="flex rounded-sm border border-border p-0.5">
      {options.map((opt) => {
        const Icon = opt.icon;
        const active = mounted && theme === opt.value;
        return (
          <button
            key={opt.value}
            type="button"
            onClick={() => setTheme(opt.value)}
            aria-pressed={active}
            className={cn(
              "flex flex-1 items-center justify-center gap-1.5 rounded-[3px] px-2 py-1 text-xs transition-colors",
              active
                ? "bg-secondary text-foreground"
                : "text-muted-foreground hover:text-foreground",
            )}
          >
            <Icon className="size-3.5" />
            {opt.label}
          </button>
        );
      })}
    </div>
  );
}
