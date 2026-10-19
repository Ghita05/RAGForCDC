#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Convertit un tiers du corpus en PDF et un tiers en DOCX, avec une mise en
page d'entreprise (page de garde, en-tête/pied de page, pagination) plutôt
que de simples paragraphes bruts.

Pourquoi : les CDC réels arrivent en PDF/DOCX avec ce type de mise en page.
L'extraction (PDFBox/POI) doit rester correcte malgré le bruit qu'elle
introduit — texte d'en-tête et de pied de page répété sur chaque page,
page de garde, retours à la ligne au milieu des phrases. Si le corpus est
uniquement textuel ou sans mise en page, on mesure une qualité d'extraction
qui ne se reproduira pas en production.

Le manifeste est réécrit avec l'extension réelle de chaque document, afin de
rester la vérité terrain unique.
"""

import csv
import re
from pathlib import Path

from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt, RGBColor
from reportlab.lib.colors import HexColor
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import cm
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak

CORPUS = Path("corpus")
BRAND = HexColor("#1c3f60")


def escape(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


HEADING_RE = re.compile(r"^\d{1,2}\. \S")


def is_heading(block):
    # Numéro de section à un ou deux chiffres ("1. Objet" ... "14. Annexes"),
    # à distinguer d'un tableau markdown dont une cellule commencerait par
    # un nombre suivi d'un point (peu probable en tout début de bloc, mais
    # on exige un espace après le point pour rester strict).
    return bool(HEADING_RE.match(block)) and len(block.splitlines()[0]) < 60


def split_header(text):
    """Sépare le bloc d'en-tête libre ("CAHIER DES CHARGES\\nRéférence : ...")
    du reste du texte, pour le remplacer par une page de garde dessinée."""
    blocks = text.split("\n\n", 1)
    header_lines = blocks[0].strip().split("\n")
    meta = {}
    for line in header_lines[1:]:
        if " : " in line:
            k, v = line.split(" : ", 1)
            meta[k.strip()] = v.strip()
    return meta, (blocks[1] if len(blocks) > 1 else "")


# ---------------------------------------------------------------------------
# PDF
# ---------------------------------------------------------------------------

def _pdf_header_footer(canvas, doc, meta):
    canvas.saveState()
    w, h = A4
    # En-tête : bandeau de couleur + "logo" texte, pas d'image externe.
    canvas.setFillColor(BRAND)
    canvas.rect(0, h - 1.4 * cm, w, 1.4 * cm, fill=1, stroke=0)
    canvas.setFillColor("white")
    canvas.setFont("Helvetica-Bold", 12)
    canvas.drawString(2.5 * cm, h - 1.0 * cm, "SOCIÉTÉ EXEMPLE")
    canvas.setFont("Helvetica", 8)
    canvas.drawRightString(w - 2.5 * cm, h - 1.0 * cm,
                           "Direction des Systèmes d'Information")
    # Pied de page : référence, confidentialité, pagination.
    canvas.setFillColor(HexColor("#666666"))
    canvas.setFont("Helvetica", 8)
    canvas.drawString(2.5 * cm, 1.3 * cm,
                      f"Cahier des charges {meta.get('Référence', '')} — Confidentiel")
    canvas.drawRightString(w - 2.5 * cm, 1.3 * cm, f"Page {canvas.getPageNumber()}")
    canvas.setStrokeColor(HexColor("#cccccc"))
    canvas.line(2.5 * cm, 1.6 * cm, w - 2.5 * cm, 1.6 * cm)
    canvas.restoreState()


def write_pdf(path, text):
    meta, body_text = split_header(text)
    styles = getSampleStyleSheet()
    body = ParagraphStyle("body", parent=styles["Normal"], fontName="Helvetica",
                          fontSize=10.5, leading=15, spaceAfter=8)
    head = ParagraphStyle("head", parent=body, fontName="Helvetica-Bold",
                          fontSize=12, spaceBefore=14, spaceAfter=6, textColor=BRAND)
    cover_title = ParagraphStyle("cover_title", parent=styles["Normal"],
                                 fontName="Helvetica-Bold", fontSize=22,
                                 textColor=BRAND, spaceAfter=24)
    cover_meta = ParagraphStyle("cover_meta", parent=styles["Normal"],
                                fontName="Helvetica", fontSize=11, leading=18)

    doc = SimpleDocTemplate(str(path), pagesize=A4,
                            leftMargin=2.5 * cm, rightMargin=2.5 * cm,
                            topMargin=2.8 * cm, bottomMargin=2.5 * cm,
                            title=path.stem)

    ouvrage = meta.get("Maître d'ouvrage", "")
    flow = [
        Spacer(1, 4 * cm),
        Paragraph("CAHIER DES CHARGES", cover_title),
        Paragraph(f"Référence : {escape(meta.get('Référence', ''))}", cover_meta),
        Paragraph(f"Objet : {escape(meta.get('Objet', ''))}", cover_meta),
        Paragraph(f"Maître d'ouvrage : {escape(ouvrage)}", cover_meta),
        Paragraph(f"Date : {escape(meta.get('Date', ''))}", cover_meta),
        Paragraph(f"Version : {escape(meta.get('Version', ''))}", cover_meta),
        Paragraph(f"Diffusion : {escape(meta.get('Diffusion', ''))}", cover_meta),
        PageBreak(),
    ]
    for block in body_text.split("\n\n"):
        block = block.strip()
        if not block:
            continue
        style = head if is_heading(block) else body
        flow.append(Paragraph(escape(block).replace("\n", "<br/>"), style))

    def on_page(canvas, doc_):
        _pdf_header_footer(canvas, doc_, meta)

    doc.build(flow, onFirstPage=on_page, onLaterPages=on_page)


# ---------------------------------------------------------------------------
# DOCX
# ---------------------------------------------------------------------------

def write_docx(path, text):
    # Un bloc peut contenir plusieurs lignes logiques (lignes de tableau
    # markdown, exigences EF-xx, sous-puces de planning). python-docx ne
    # traite pas "\n" comme un saut de ligne dans un paragraphe : chaque
    # ligne doit devenir son propre paragraphe pour rester lisible et
    # correctement extractible ligne par ligne.
    meta, body_text = split_header(text)
    d = Document()
    d.styles["Normal"].font.name = "Calibri"
    d.styles["Normal"].font.size = Pt(11)

    section = d.sections[0]
    header_p = section.header.paragraphs[0]
    header_p.text = "SOCIÉTÉ EXEMPLE — Direction des Systèmes d'Information"
    header_p.runs[0].bold = True
    header_p.runs[0].font.color.rgb = RGBColor(0x1C, 0x3F, 0x60)

    footer_p = section.footer.paragraphs[0]
    footer_p.text = f"Cahier des charges {meta.get('Référence', '')} — Confidentiel"
    footer_p.runs[0].font.size = Pt(8)
    footer_p.runs[0].font.color.rgb = RGBColor(0x66, 0x66, 0x66)

    # Page de garde.
    title = d.add_heading("CAHIER DES CHARGES", level=0)
    title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    for label in ["Référence", "Objet", "Date", "Version", "Diffusion"]:
        if meta.get(label):
            p = d.add_paragraph()
            p.add_run(f"{label} : ").bold = True
            p.add_run(meta[label])
    ouvrage = meta.get("Maître d'ouvrage", "")
    if ouvrage:
        p = d.add_paragraph()
        p.add_run("Maître d'ouvrage : ").bold = True
        p.add_run(ouvrage)
    d.add_page_break()

    for block in body_text.split("\n\n"):
        block = block.strip()
        if not block:
            continue
        lines = block.split("\n")
        if is_heading(block):
            d.add_heading(lines[0], level=2)
            lines = lines[1:]
        for line in lines:
            if line.strip():
                d.add_paragraph(line)
    d.save(str(path))


def main():
    manifest = CORPUS / "manifest.csv"
    rows = list(csv.DictReader(manifest.open(encoding="utf-8")))

    counts = {"txt": 0, "pdf": 0, "docx": 0}
    for i, row in enumerate(rows):
        src = CORPUS / row["filename"]
        text = src.read_text(encoding="utf-8")
        fmt = ["txt", "pdf", "docx"][i % 3]

        if fmt != "txt":
            dst = src.with_suffix("." + fmt)
            (write_pdf if fmt == "pdf" else write_docx)(dst, text)
            src.unlink()
            row["filename"] = dst.name
        counts[fmt] += 1

    with manifest.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader()
        w.writerows(rows)

    print(f"txt {counts['txt']} | pdf {counts['pdf']} | docx {counts['docx']}")
    print("manifeste mis à jour")


if __name__ == "__main__":
    main()
