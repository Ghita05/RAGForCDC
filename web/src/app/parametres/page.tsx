"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { Image as ImageIcon, Trash2, Upload } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Skeleton } from "@/components/ui/skeleton";
import {
  ApiError,
  clearOrganisationLogo,
  getOrganisationSettings,
  organisationLogoUrl,
  updateOrganisationSettings,
  uploadOrganisationLogo,
} from "@/lib/api";
import { REFERENCE_TEMPLATE_VARIABLES, renderReferenceTemplate } from "@/lib/types";
import type { OrganisationSettings } from "@/lib/types";

export default function ParametresPage() {
  const [settings, setSettings] = useState<OrganisationSettings | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [logoBusy, setLogoBusy] = useState(false);
  const [logoError, setLogoError] = useState<string | null>(null);
  const [logoVersion, setLogoVersion] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    getOrganisationSettings()
      .then(setSettings)
      .catch((err) => setLoadError(err instanceof ApiError ? err.message : "Impossible de charger les paramètres."));
  }, []);

  function update<K extends keyof OrganisationSettings>(key: K, value: OrganisationSettings[K]) {
    setSettings((prev) => (prev ? { ...prev, [key]: value } : prev));
    setSaved(false);
  }

  async function handleSave(e: React.FormEvent) {
    e.preventDefault();
    if (!settings) return;
    setSaving(true);
    setSaveError(null);
    try {
      const payload: OrganisationSettings = {
        ...settings,
        nomOrganisation: settings.nomOrganisation?.trim() || null,
        direction: settings.direction?.trim() || null,
        mentionConfidentialite: settings.mentionConfidentialite?.trim() || null,
        formatReference: settings.formatReference?.trim() || null,
        piedDePage: settings.piedDePage?.trim() || null,
        contactService: settings.contactService?.trim() || null,
        contactAdresse: settings.contactAdresse?.trim() || null,
        contactCourriel: settings.contactCourriel?.trim() || null,
      };
      const result = await updateOrganisationSettings(payload);
      setSettings(result);
      setSaved(true);
    } catch (err) {
      setSaveError(err instanceof ApiError ? err.message : "Échec de l'enregistrement.");
    } finally {
      setSaving(false);
    }
  }

  async function handleLogoSelected(file: File | null) {
    if (!file) return;
    setLogoBusy(true);
    setLogoError(null);
    try {
      const result = await uploadOrganisationLogo(file);
      setSettings(result);
      setLogoVersion((v) => v + 1);
    } catch (err) {
      setLogoError(err instanceof ApiError ? err.message : "Échec du téléversement du logo.");
    } finally {
      setLogoBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleClearLogo() {
    setLogoBusy(true);
    setLogoError(null);
    try {
      const result = await clearOrganisationLogo();
      setSettings(result);
      setLogoVersion((v) => v + 1);
    } catch (err) {
      setLogoError(err instanceof ApiError ? err.message : "Échec de la suppression du logo.");
    } finally {
      setLogoBusy(false);
    }
  }

  if (loadError) {
    return (
      <main className="flex flex-1 flex-col">
        <Alert variant="destructive" className="m-6">
          <AlertDescription>{loadError}</AlertDescription>
        </Alert>
      </main>
    );
  }

  if (!settings) {
    return (
      <main className="flex flex-1 flex-col gap-3 p-6">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-40 w-full" />
      </main>
    );
  }

  return (
    <main className="flex flex-1 flex-col">
      <div className="border-b border-border px-6 py-4">
        <h1 className="text-lg font-medium">Paramètres</h1>
        <p className="text-sm text-muted-foreground">
          Ces paramètres définissent le CONTENU de marque affiché sur la page de garde et en pied de
          page des CDC exportés -- distinct de la{" "}
          <Link href="/mise-en-forme" className="underline">
            mise en forme
          </Link>{" "}
          (polices, couleurs, marges) et de la{" "}
          <Link href="/structures" className="underline">
            structure
          </Link>{" "}
          (sections) d&apos;un CDC.
        </p>
      </div>

      <form onSubmit={handleSave} className="flex flex-1 flex-col overflow-hidden">
        <div className="flex flex-1 gap-8 overflow-y-auto px-6 py-6">
          <div className="flex max-w-xl flex-1 flex-col gap-6">
            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-medium">Identité</h2>
              <div className="grid gap-2">
                <Label htmlFor="nom-organisation">Nom de l&apos;organisation</Label>
                <Input
                  id="nom-organisation"
                  value={settings.nomOrganisation ?? ""}
                  onChange={(e) => update("nomOrganisation", e.target.value)}
                  placeholder="ex. Groupe Aurore Ingénierie"
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="direction">Direction</Label>
                <Input
                  id="direction"
                  value={settings.direction ?? ""}
                  onChange={(e) => update("direction", e.target.value)}
                  placeholder="ex. Direction des systèmes d'information"
                />
              </div>
            </section>

            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-medium">Logo</h2>
              <div className="flex items-center gap-4">
                <div className="flex size-20 shrink-0 items-center justify-center rounded-md border border-dashed border-border bg-muted/30">
                  {settings.hasLogo ? (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={organisationLogoUrl(logoVersion)}
                      alt="Logo de l'organisation"
                      className="max-h-full max-w-full object-contain p-1"
                    />
                  ) : (
                    <ImageIcon className="size-6 text-muted-foreground" />
                  )}
                </div>
                <div className="flex flex-col gap-2">
                  <div className="flex items-center gap-2">
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      disabled={logoBusy}
                      onClick={() => fileInputRef.current?.click()}
                    >
                      <Upload className="size-3.5" />
                      {settings.hasLogo ? "Remplacer" : "Téléverser"}
                    </Button>
                    {settings.hasLogo && (
                      <Button type="button" size="sm" variant="destructive" disabled={logoBusy} onClick={handleClearLogo}>
                        <Trash2 className="size-3.5" />
                      </Button>
                    )}
                  </div>
                  <p className="text-xs text-muted-foreground">PNG ou JPG, 2 Mo maximum.</p>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/png,image/jpeg"
                    className="hidden"
                    onChange={(e) => handleLogoSelected(e.target.files?.[0] ?? null)}
                  />
                </div>
              </div>
              {logoError && <p className="text-sm text-destructive">{logoError}</p>}
            </section>

            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-medium">Confidentialité et pied de page</h2>
              <div className="grid gap-2">
                <Label htmlFor="mention-confidentialite">Mention de confidentialité</Label>
                <Input
                  id="mention-confidentialite"
                  value={settings.mentionConfidentialite ?? ""}
                  onChange={(e) => update("mentionConfidentialite", e.target.value)}
                  placeholder="ex. Diffusion restreinte — usage interne et prestataires consultés"
                />
                <p className="text-xs text-muted-foreground">Affichée en bas de la page de garde.</p>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="pied-de-page">Texte de pied de page</Label>
                <Input
                  id="pied-de-page"
                  value={settings.piedDePage ?? ""}
                  onChange={(e) => update("piedDePage", e.target.value)}
                  placeholder="ex. Groupe Aurore Ingénierie — document interne"
                />
                <p className="text-xs text-muted-foreground">
                  Affiché sur chaque page, combiné au numéro de page.
                </p>
              </div>
            </section>

            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-medium">Référence du document</h2>
              <div className="grid gap-2">
                <Label htmlFor="format-reference">Gabarit de référence</Label>
                <Input
                  id="format-reference"
                  value={settings.formatReference ?? ""}
                  onChange={(e) => update("formatReference", e.target.value)}
                  placeholder="CDC-{annee}-{numero:000}_{type}"
                />
              </div>
              <ul className="flex flex-col gap-1 rounded-md border border-border bg-muted/20 p-3 text-xs text-muted-foreground">
                {REFERENCE_TEMPLATE_VARIABLES.map((v) => (
                  <li key={v.token}>
                    <code className="rounded-sm bg-muted px-1 py-0.5 text-foreground">{v.token}</code> —{" "}
                    {v.description}
                  </li>
                ))}
              </ul>
            </section>

            <section className="flex flex-col gap-3">
              <h2 className="text-sm font-medium">Contact (optionnel)</h2>
              <div className="grid gap-2">
                <Label htmlFor="contact-service">Service</Label>
                <Input
                  id="contact-service"
                  value={settings.contactService ?? ""}
                  onChange={(e) => update("contactService", e.target.value)}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="contact-adresse">Adresse</Label>
                <Textarea
                  id="contact-adresse"
                  value={settings.contactAdresse ?? ""}
                  onChange={(e) => update("contactAdresse", e.target.value)}
                  rows={2}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="contact-courriel">Courriel</Label>
                <Input
                  id="contact-courriel"
                  type="email"
                  value={settings.contactCourriel ?? ""}
                  onChange={(e) => update("contactCourriel", e.target.value)}
                />
              </div>
            </section>

            {saveError && (
              <Alert variant="destructive">
                <AlertDescription>{saveError}</AlertDescription>
              </Alert>
            )}
          </div>

          <div className="w-[340px] shrink-0">
            <div className="sticky top-6">
              <h2 className="mb-3 text-sm font-medium text-muted-foreground">Aperçu de la page de garde</h2>
              <CoverPreview settings={settings} logoVersion={logoVersion} />
            </div>
          </div>
        </div>

        <div className="sticky bottom-0 flex items-center justify-between border-t border-border bg-background px-6 py-3">
          <span className="text-sm text-muted-foreground">{saved ? "Paramètres enregistrés." : ""}</span>
          <Button type="submit" disabled={saving}>
            {saving ? "Enregistrement…" : "Enregistrer"}
          </Button>
        </div>
      </form>
    </main>
  );
}

function CoverPreview({ settings, logoVersion }: { settings: OrganisationSettings; logoVersion: number }) {
  const now = new Date();
  const reference = renderReferenceTemplate(settings.formatReference, now.getFullYear(), 42, "reseau");
  return (
    <div className="flex aspect-[210/297] flex-col items-center rounded-md border border-border bg-card p-6 text-center shadow-sm">
      <div className="flex min-h-16 items-center justify-center">
        {settings.hasLogo ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img
            src={organisationLogoUrl(logoVersion)}
            alt="Logo de l'organisation"
            className="max-h-14 max-w-[140px] object-contain"
          />
        ) : (
          <div className="rounded-sm border border-dashed border-border px-4 py-2 text-[9px] italic text-muted-foreground">
            Emplacement du logo
          </div>
        )}
      </div>
      {settings.nomOrganisation && <p className="mt-2 text-xs font-semibold">{settings.nomOrganisation}</p>}
      {settings.direction && <p className="text-[10px] text-muted-foreground">{settings.direction}</p>}

      <p className="mt-6 text-base font-bold text-primary">Titre du cahier des charges</p>

      <div className="mt-3 flex flex-col gap-0.5 text-[10px] text-muted-foreground">
        <span>Référence : {reference}</span>
        <span>Type : réseau</span>
        <span>Date : {now.toLocaleDateString("fr-FR")}</span>
        <span>Statut : Brouillon</span>
      </div>

      {(settings.contactService || settings.contactAdresse || settings.contactCourriel) && (
        <div className="mt-4 flex flex-col gap-0.5 text-[9px] text-muted-foreground">
          {settings.contactService && <span>{settings.contactService}</span>}
          {settings.contactAdresse && <span>{settings.contactAdresse}</span>}
          {settings.contactCourriel && <span>{settings.contactCourriel}</span>}
        </div>
      )}

      <div className="mt-auto text-[9px] italic text-muted-foreground">
        {settings.mentionConfidentialite}
      </div>
    </div>
  );
}
