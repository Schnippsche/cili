#!/usr/bin/env python3
"""
Adressaufkleber mit QR-Code für einen einzelnen Nutzer (Zweckform 6174, 70 x 42.3 mm, 3x7 pro A4-Blatt).

Die Nutzerdaten werden vom aufrufenden Programm übergeben (z. B. dem CILI-Backend
nach Auswahl eines Users im Frontend) - das Skript ermittelt selbst keine Daten.

Verwendung:
  python scripts/generate_labels.py --name "Beate May" --member-id 219210 \
      --phone "01515 943 3216" --email beamay64@gmail.com --url BeateMay \
      --output etiketten.pdf

Optionen:
  --name <Name>         Anzeigename auf dem Etikett (Pflicht)
  --email <E-Mail>      E-Mail-Adresse (Pflicht)
  --url <Slug>          Slug für den QR-Link https://cilibydesign.com/<Slug> (Pflicht)
  --member-id <ID>      Berater-ID (optional)
  --phone <Nummer>      Mobilnummer (optional)
  --output <Pfad>       Ziel-PDF (Standard: etiketten.pdf)
  --copies <N>          Anzahl Etiketten (Standard: 21 = ein voller Zweckform-6174-Bogen)
"""

import argparse
import io
import sys
from dataclasses import dataclass
from pathlib import Path

import qrcode
from reportlab.lib.colors import HexColor, black
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

QR_BASE_URL = "https://cilibydesign.com/"

# Carlito ist metrisch identisch zu Calibri und frei lizenziert (SIL OFL) -
# als TTF mitgeliefert, damit die Etiketten unabhängig vom Server-Fontbestand aussehen.
FONT_DIR = Path(__file__).resolve().parent / "fonts"
pdfmetrics.registerFont(TTFont("Carlito", str(FONT_DIR / "Carlito-Regular.ttf")))
pdfmetrics.registerFont(TTFont("Carlito-Bold", str(FONT_DIR / "Carlito-Bold.ttf")))
FONT_REGULAR = "Carlito"
FONT_BOLD = "Carlito-Bold"

# Zweckform 6174: 3 Spalten x 7 Zeilen, randlos auf A4, Etikett 70 x 42.3 mm
COLS, ROWS = 3, 7
LABEL_W, LABEL_H = 70 * mm, 42.3 * mm

ACCENT = HexColor("#4472C4")


@dataclass
class LabelData:
    name: str
    member_id: int | None
    phone: str | None
    email: str
    url: str


def make_qr_image(data: str) -> io.BytesIO:
    qr = qrcode.QRCode(border=1, box_size=8)
    qr.add_data(data)
    qr.make(fit=True)
    img = qr.make_image(fill_color="black", back_color="white")
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    buf.seek(0)
    return buf


# Feinabstimmung auf Zweckform 6174 (70 x 42.3 mm, randlos, 3x7/A4)
PAD_LEFT = 4 * mm
PAD_TOP = 5 * mm
QR_SIZE = 16 * mm
QR_MARGIN = 3 * mm
NAME_FONT_SIZE = 12
LINE_FONT_SIZE = 8
URL_FONT_SIZE = 6
NAME_GAP = 6 * mm      # Abstand Name-Baseline -> erste Detailzeile
LINE_GAP = 3.8 * mm    # Zeilenabstand zwischen Detailzeilen (auch für Leerzeilen)


def draw_label(c: canvas.Canvas, x: float, y: float, label: LabelData) -> None:
    """Zeichnet ein Etikett mit unterer linker Ecke bei (x, y)."""
    qr_url = f"{QR_BASE_URL}{label.url}"
    text_x = x + PAD_LEFT
    top_y = y + LABEL_H - PAD_TOP

    c.setFont(FONT_BOLD, NAME_FONT_SIZE)
    c.setFillColor(ACCENT)
    c.drawString(text_x, top_y, label.name)

    c.setFillColor(black)
    line_y = top_y - NAME_GAP

    c.setFont(FONT_REGULAR, LINE_FONT_SIZE)
    if label.member_id is not None:
        c.drawString(text_x, line_y, f"Berater-ID: {label.member_id}")
        line_y -= LINE_GAP
    if label.phone:
        c.drawString(text_x, line_y, f"Mobil: {label.phone}")
        line_y -= LINE_GAP

    line_y -= LINE_GAP  # Leerzeile nach Mobil
    c.drawString(text_x, line_y, label.email)
    line_y -= LINE_GAP

    line_y -= LINE_GAP  # Leerzeile nach E-Mail
    c.setFont(FONT_REGULAR, URL_FONT_SIZE)
    c.drawString(text_x, line_y, qr_url)

    qr_x = x + LABEL_W - QR_SIZE - QR_MARGIN
    qr_y = y + LABEL_H - QR_SIZE - QR_MARGIN
    c.drawImage(ImageReader(make_qr_image(qr_url)), qr_x, qr_y, width=QR_SIZE, height=QR_SIZE, mask="auto")


def generate_pdf(labels: list[LabelData], output_path: str) -> None:
    c = canvas.Canvas(output_path, pagesize=A4)
    _, page_h = A4
    per_page = COLS * ROWS

    for i, label in enumerate(labels):
        pos = i % per_page
        if pos == 0 and i > 0:
            c.showPage()
        row, col = divmod(pos, COLS)
        x = col * LABEL_W
        y = page_h - (row + 1) * LABEL_H
        draw_label(c, x, y, label)

    c.save()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Erzeugt einen Zweckform-6174-Bogen mit Adressaufklebern (Name/QR-Code) für einen einzelnen Nutzer.")
    parser.add_argument("--name", required=True, help="Anzeigename auf dem Etikett")
    parser.add_argument("--email", required=True, help="E-Mail-Adresse")
    parser.add_argument("--url", required=True, help="Slug für den QR-Link https://cilibydesign.com/<Slug>")
    parser.add_argument("--member-id", type=int, default=None, help="Berater-ID (optional)")
    parser.add_argument("--phone", default=None, help="Mobilnummer (optional)")
    parser.add_argument("--output", default="etiketten.pdf", help="Ziel-PDF (Standard: etiketten.pdf)")
    parser.add_argument("--copies", type=int, default=COLS * ROWS,
                         help=f"Anzahl Etiketten (Standard: {COLS * ROWS} = ein voller Zweckform-6174-Bogen)")
    args = parser.parse_args()

    if args.copies < 1:
        parser.error("--copies muss mindestens 1 sein.")

    label = LabelData(
        name=args.name,
        member_id=args.member_id,
        phone=args.phone,
        email=args.email,
        url=args.url,
    )

    generate_pdf([label] * args.copies, args.output)
    print(f"{args.copies} Etikett(en) geschrieben nach {args.output}")


if __name__ == "__main__":
    main()
