"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { ThemeToggle } from "@/components/theme-toggle";

const NAV_ITEMS = [
  { label: "Cahiers des charges", href: "/" },
  { label: "Plans", href: "/plans" },
  { label: "Corpus", href: "/corpus" },
  { label: "Structures", href: "/structures" },
  { label: "Mise en forme", href: "/mise-en-forme" },
  { label: "Paramètres", href: "/parametres" },
] as const;

// Nom générique du produit : l'outil n'est pas lié à une organisation en
// particulier. Le nom de l'organisation cliente, lui, est configurable.
const PRODUCT_NAME = "CDC Studio";
const ORG_NAME = process.env.NEXT_PUBLIC_ORG_NAME;

export function AppSidebar() {
  const pathname = usePathname();

  return (
    <aside className="sticky top-0 flex h-svh w-[220px] shrink-0 flex-col border-r border-border">
      <div className="px-4 py-4">
        <span className="text-sm font-medium">{PRODUCT_NAME}</span>
        {ORG_NAME && <span className="block text-xs text-muted-foreground">{ORG_NAME}</span>}
      </div>

      <nav className="flex flex-col gap-0.5 px-2">
        {NAV_ITEMS.map((item) => {
          const active = pathname === item.href || pathname.startsWith(item.href + "/");
          return (
            <Link
              key={item.label}
              href={item.href}
              className={cn(
                "rounded-sm px-2.5 py-1.5 text-sm transition-colors",
                active
                  ? "bg-accent text-accent-foreground"
                  : "text-muted-foreground hover:bg-secondary hover:text-foreground",
              )}
            >
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="mt-auto p-3">
        <ThemeToggle />
      </div>
    </aside>
  );
}
