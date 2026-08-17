#!/usr/bin/env python3
"""
vitalstoff_summary.py
======================
Erzeugt aus dem HTML-Export eines Vitalstoff-Analyseberichts (z.B. "stefan.htm",
inkl. zugehörigem Bilder-Ordner "stefan-Dateien/") eine kompakte Zusammenfassung
der auffälligen Messwerte.

Vollständig deterministisch, KEIN LLM nötig:
  - Werte, Normbereiche und Parameter-Namen kommen direkt aus den <TABLE>-Zellen
    des HTML (kein PDF-Layout-Rätselraten mehr).
  - Die tatsächliche Geräte-Bewertung (-/+/++/+++ und Richtung) wird aus dem
    kleinen Balken-Icon jeder Zeile (z.B. "YC05.png") pixelgenau decodiert:
    der Balken hat 7 Farbsegmente (rot|gelb|blau|grün|blau|gelb|rot = stark/
    mäßig/leicht abweichend niedrig, normal, leicht/mäßig/stark abweichend
    hoch), ein Pfeil-Marker zeigt auf ein Segment. Das ist die exakte
    Original-Bewertung des Geräts, keine eigene Schätzung.
  - Die Erklärsätze zu jedem Parameter kommen aus dem im Bericht selbst
    enthaltenen "Beschreibung der Parameter"-Text (erster Satz), nicht von
    einer KI generiert.

Kein LLM/KI mehr im Spiel - die Ausgabe wird über eine editierbare HTML-
Vorlage ("template.html") erzeugt: Kopfdaten und drei wiederholbare
Bausteine (Abweichungsstufe / Körpersystem / einzelner Messwert) sind als
{{PLATZHALTER}} markiert, die das Skript befüllt. Layout, CSS, Struktur -
alles frei im Template anpassbar, ohne den Code zu ändern.

Nutzung:
  python vitalstoff_summary.py stefan.htm
  python vitalstoff_summary.py stefan.htm --template mein_layout.html --html-out zusammenfassung.html
"""

import argparse
import os
import re
import sys
from urllib.parse import unquote

from bs4 import BeautifulSoup
from PIL import Image
import yaml

for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

SECTION_RE = re.compile(r"\(([^)]+)\)\s*Analyse-Bericht")
NAME_RE = re.compile(r"Name:\s*(.+?)\s*Geschlecht")
DATE_RE = re.compile(r"Test Zeit:\s*([\d.: ]+)")
NUM_RE = re.compile(r"-?\d+(?:[.,]\d+)?")

# Segment-Index (0-6) -> (Symbol, Richtung, Label)
SEGMENTS = {
    0: ("+++", "unter", "stark abweichend"),
    1: ("++", "unter", "mäßig abweichend"),
    2: ("+", "unter", "leicht abweichend"),
    3: ("-", None, "normal"),
    4: ("+", "über", "leicht abweichend"),
    5: ("++", "über", "mäßig abweichend"),
    6: ("+++", "über", "stark abweichend"),
}
SEVERITY_RANK = {"+++": 3, "++": 2, "+": 1, "-": 0}
SEVERITY_WORD = {"+++": "stark", "++": "mäßig", "+": "leicht"}
SEVERITY_TITLE = {"+++": "Stark abweichend", "++": "Mäßig abweichend", "+": "Leicht abweichend"}
SEVERITY_COLOR = {"+++": "#d9534f", "++": "#f0ad4e", "+": "#5bc0de"}


def load_custom_texts(path: str) -> dict:
    if not path or not os.path.exists(path):
        return {}
    with open(path, encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def resolve_text(f: dict, custom_texts: dict):
    """Fallback-Kette für den Erklärtext einer Zeile:
    1. eigener, abweichungsspezifischer Text aus der YAML (Parameter -> Richtung -> Schwere)
    2. eigener 'default'-Text aus der YAML für den Parameter
    3. der aus dem Originalbericht extrahierte Beschreibungstext
    4. nichts (Feld bleibt leer)."""
    entry = custom_texts.get(f["parameter"])
    if entry:
        direction = entry.get(f["direction"]) if f["direction"] else None
        if direction:
            severity_text = direction.get(SEVERITY_WORD.get(f["symbol"], ""))
            if severity_text:
                return severity_text
        if entry.get("default"):
            return entry["default"]
    return f["description"]


def resolve_full_text(f: dict, custom_texts: dict):
    """Wie resolve_text(), aber ohne Schweregrad/Richtungs-Verzweigung: liefert den
    vollständigen (ungekürzten) Original-Beschreibungstext für den Vollbericht, mit
    optionalem Nutzer-Override 'default_full' aus der YAML. Die Original-Parameter-
    beschreibung ist generisch und variiert - anders als die kurzen, selbst verfassten
    über/unter-Texte - nicht nach Ausprägung des Patientenwerts."""
    entry = custom_texts.get(f["parameter"])
    if entry and entry.get("default_full"):
        return entry["default_full"]
    return f["description_full"]


def write_texts_template(findings: list, path: str):
    """Erzeugt/aktualisiert eine YAML-Vorlage mit einem Eintrag pro Parameter,
    vorbefüllt mit dem Originaltext als 'default'. leicht/mäßig/stark je
    Richtung bleiben leer, damit sie individuell nachgetragen werden können."""
    existing = load_custom_texts(path)
    seen = {}
    for f in findings:
        if f["parameter"] in seen:
            continue
        seen[f["parameter"]] = {
            "default": f["description"] or "",
            "default_full": f["description_full"] or "",
            "über": {"leicht": "", "mäßig": "", "stark": ""},
            "unter": {"leicht": "", "mäßig": "", "stark": ""},
        }
    # bereits vorhandene (ggf. vom Nutzer editierte) Einträge nicht überschreiben
    merged = {**seen, **existing}
    with open(path, "w", encoding="utf-8") as f:
        yaml.dump(merged, f, allow_unicode=True, sort_keys=True, width=100)
    return len(seen) - len(existing.keys() & seen.keys())

_icon_cache = {}


# Feste Zuordnung Dateiname -> Segment-Index. Verifiziert: die Icon-Dateien
# sind über alle geprüften Berichte hinweg byte-identisch (nur die jeweils
# tatsächlich verwendete Teilmenge YC01-YC09 wird pro Bericht mitgeliefert) -
# der Dateiname allein reicht daher, kein Bildladen/Pixel-Decoding nötig.
ICON_NAME_TO_SEGMENT = {
    "YC01": 0, "YC02": 1, "YC03": 2, "YC04": 3,
    "YC05": 4, "YC06": 5, "YC07": 6, "YC08": 3, "YC09": 1,
    "YC10": 5,
}


def decode_icon(path: str):
    """Liefert (symbol, richtung, label) primär per Dateiname-Lookup (kein
    Datei-I/O). Nur für unbekannte Dateinamen (z.B. neue Icon-Varianten eines
    künftigen Berichts) fällt es auf die geometrische Pfeil-Positionserkennung
    im Balken-Icon (190x26 px, 7 gleich breite Farbsegmente) zurück."""
    if path in _icon_cache:
        return _icon_cache[path]

    stem = os.path.splitext(os.path.basename(path))[0]
    seg = ICON_NAME_TO_SEGMENT.get(stem)
    if seg is not None:
        result = SEGMENTS[seg]
        _icon_cache[path] = result
        return result

    try:
        im = Image.open(path).convert("RGB")
    except (FileNotFoundError, OSError):
        return None
    w, h = im.size
    px = im.load()
    xs = [x for x in range(w) for y in range(1, min(9, h)) if px[x, y] == (0, 0, 0)]
    if not xs:
        return None
    cx = sum(xs) / len(xs)
    seg = min(6, int(cx / (w / 7)))
    result = SEGMENTS[seg]
    _icon_cache[path] = result
    return result


def to_float(token: str):
    token = token.strip().replace(".", "").replace(",", ".")
    try:
        return float(token)
    except ValueError:
        return None


def parse_range(text: str):
    nums = NUM_RE.findall(text)
    if len(nums) < 2:
        return None
    low, high = to_float(nums[0]), to_float(nums[1])
    if low is None or high is None:
        return None
    return (low, high) if low <= high else (high, low)


def parse_value(text: str):
    nums = NUM_RE.findall(text)
    return to_float(nums[0]) if nums else None


def norm_key(s: str) -> str:
    """Normalisiert Parameternamen für den Erklärtext-Abgleich (Klammerzusätze
    wie '(HLP)' oder '(Fe)' können auf beiden Seiten leicht abweichen)."""
    s = re.sub(r"\([^)]*\)", "", s)
    s = re.sub(r"\s+", " ", s).strip().rstrip(":").strip().lower()
    return s


def first_sentence(text: str, max_len: int = 220) -> str:
    text = re.sub(r"\s+", " ", text).strip()
    m = re.match(r"(.{15,}?[.!?])(\s|$)", text)
    sentence = m.group(1) if m else text
    if len(sentence) > max_len:
        sentence = sentence[:max_len].rsplit(" ", 1)[0] + "…"
    return sentence


PRODUCT_LIST_RE = re.compile(r"^[A-ZÄÖÜ0-9][A-ZÄÖÜ0-9\-\s,]*$")


def strip_product_list(text: str) -> str:
    """Die Wellness-Tipp-Texte enden immer mit einem angehängten, durch
    Komma getrennten Satz reiner Produktcodes (z.B. 'V-ITADOL, VITALAGE
    COLLAGEN, V-CURCUMAX'). Diese Produktempfehlungen sollen nicht
    übernommen werden - der Rest des Texts (patientenspezifisch) bleibt 1:1
    erhalten. Erkennung: der letzte durch einen Punkt abgetrennte Abschnitt
    enthält keinen einzigen Kleinbuchstaben (echte Sätze haben immer welche)."""
    text = re.sub(r"\s+", " ", text).strip()
    parts = re.split(r"(?<=\.)\s+", text)
    if len(parts) > 1:
        last = parts[-1].strip().rstrip(".").strip()
        if last and PRODUCT_LIST_RE.match(last):
            return " ".join(parts[:-1]).strip()
    return text


def extract_summary_blocks(soup: BeautifulSoup):
    """Parst den Original-Abschnitt 'Zusammenfassender Bericht': mehrere
    Unterkapitel (Font-Überschrift), je eine Tabelle System/Eigenschaft/
    Normbereich/Wert mit rowspan-gruppiertem System und einer 'Wellness-Tipp'-
    Zeile pro Gruppe. Ergebnis: Liste von {"title", "groups": [{"system",
    "items": [...], "tip"}]} - Werte/Struktur eigene Auswertung, Tipp-Text
    1:1 aus dem Original (nur Produktliste am Ende entfernt)."""
    blocks = []
    in_summary = False
    current_title = None
    current_groups = None
    group = None

    def close_block():
        nonlocal current_title, current_groups, group
        if group is not None:
            current_groups.append(group)
            group = None
        if current_title is not None:
            blocks.append({"title": current_title, "groups": current_groups})

    for tag in soup.find_all(["font", "table"]):
        if tag.name == "font":
            size = tag.get("size")
            text = re.sub(r"\s+", " ", tag.get_text(" ", strip=True))
            if size == "6" and "Zusammenfassender" in text and "Bericht" in text:
                in_summary = True
                continue
            if in_summary and size == "3":
                close_block()
                current_title, current_groups, group = text, [], None
            continue

        if not in_summary or current_groups is None:
            continue

        tbody = tag.find("tbody")
        trs = (tbody.find_all("tr", recursive=False) if tbody else tag.find_all("tr", recursive=False))
        if not trs:
            continue
        head_text = " ".join(td.get_text(strip=True) for td in tag.find_all("td", limit=4))
        if "System" not in head_text or "getestete Eigenschaft" not in head_text:
            continue

        for tr in trs[1:]:
            tds = tr.find_all("td", recursive=False)
            if not tds:
                continue
            first_text = tds[0].get_text(strip=True)
            if first_text == "Wellness-Tipp":
                if group is not None and len(tds) > 1:
                    group["tip"] = strip_product_list(tds[1].get_text(" ", strip=True))
                    current_groups.append(group)
                    group = None
                continue
            if len(tds) == 4:  # neue System-Gruppe (rowspan-Start)
                if group is not None:
                    current_groups.append(group)
                group = {"system": re.sub(r"\s+", " ", first_text), "items": [], "tip": None}
                name_cell, range_cell, value_cell = tds[1], tds[2], tds[3]
            elif len(tds) == 3 and group is not None:  # Folgezeile derselben Gruppe
                name_cell, range_cell, value_cell = tds[0], tds[1], tds[2]
            else:
                continue
            pname = re.sub(r"\s+", " ", name_cell.get_text(strip=True))
            rng = parse_range(range_cell.get_text(strip=True))
            val = parse_value(value_cell.get_text(strip=True))
            if pname and rng is not None and val is not None:
                group["items"].append({"parameter": pname, "low": rng[0], "high": rng[1], "value": val})

    close_block()
    return blocks


def extract_findings(html_path: str):
    base_dir = os.path.dirname(os.path.abspath(html_path))
    with open(html_path, encoding="iso-8859-1", errors="replace") as f:
        html = f.read()
    # 'html.parser' statt 'lxml': lxml sniffte hier wegen eines kaputten
    # <meta charset>-Tags im Kopf ein falsches Encoding und erzeugte doppelt
    # kodierte Umlaute (z.B. "Fettsäure" -> "FettsÃ¤ure").
    soup = BeautifulSoup(html, "html.parser")
    summary_blocks = extract_summary_blocks(soup)

    header_text = re.sub(r"\s+", " ", soup.get_text(" "))
    patient_name, test_date = "Unbekannt", "Unbekannt"
    m = NAME_RE.search(header_text)
    if m:
        patient_name = m.group(1)
    m = DATE_RE.search(header_text)
    if m:
        test_date = m.group(1).strip()

    current_section = "Unbekannt"
    rows = []
    descriptions = {}

    for tag in soup.find_all(["font", "table"]):
        if tag.name == "font" and tag.get("size") == "6":
            txt = tag.get_text(" ", strip=True)
            m = SECTION_RE.search(txt)
            if m:
                current_section = re.sub(r"\s+", " ", m.group(1)).strip()
            continue

        tbody = tag.find("tbody")
        trs = (tbody.find_all("tr", recursive=False) if tbody else tag.find_all("tr", recursive=False))
        if not trs:
            continue
        header_text = " ".join(td.get_text(strip=True) for td in tag.find_all("td", limit=4))

        if "getestete Eigenschaft" in header_text and "Normalbereich" in header_text:
            for tr in trs[1:]:
                tds = tr.find_all("td", recursive=False)
                if len(tds) < 4:
                    continue
                name = re.sub(r"\s+", " ", tds[0].get_text(strip=True))
                rng = parse_range(tds[1].get_text(strip=True))
                val = parse_value(tds[2].get_text(strip=True))
                img = tds[3].find("img")
                icon_path = os.path.join(base_dir, unquote(img["src"])) if img and img.get("src") else None
                if not name or rng is None or val is None:
                    continue
                rows.append({
                    "section": current_section, "parameter": name,
                    "low": rng[0], "high": rng[1], "value": val, "icon": icon_path,
                })
        elif tag.get("class") and "xtable" in tag.get("class"):
            head = trs[0].get_text(strip=True)
            body = " ".join(t.get_text(" ", strip=True) for t in trs[1:])
            key = norm_key(head)
            if key and body:
                descriptions.setdefault(key, body)

    findings = []
    for r in rows:
        decoded = decode_icon(r["icon"]) if r["icon"] else None
        if decoded is None:
            # Fallback, falls Icon fehlt/nicht lesbar: rein wertbasiert (grob),
            # als "leicht" statt eines nicht existierenden Symbols einsortiert,
            # damit die Gruppierung nicht auf ein unbekanntes Symbol trifft.
            symbol, direction, label = ("-", None, "normal") if r["low"] <= r["value"] <= r["high"] else \
                ("+", "über" if r["value"] > r["high"] else "unter", "abweichend (Icon nicht lesbar, grobe Schätzung)")
        else:
            symbol, direction, label = decoded

        span = r["high"] - r["low"]
        if r["value"] > r["high"]:
            pct = (r["value"] - r["high"]) / span * 100 if span else float("inf")
        elif r["value"] < r["low"]:
            pct = (r["low"] - r["value"]) / span * 100 if span else float("inf")
        else:
            pct = 0.0

        desc = descriptions.get(norm_key(r["parameter"]))
        desc_full = re.sub(r"\s+", " ", desc).strip() if desc else None
        findings.append({
            **r, "symbol": symbol, "direction": direction, "label": label,
            "deviation_pct": pct, "description": first_sentence(desc) if desc else None,
            "description_full": desc_full,
        })

    abnormal = [f for f in findings if f["symbol"] not in ("-",)]
    abnormal.sort(key=lambda f: (SEVERITY_RANK.get(f["symbol"], 0), f["deviation_pct"]), reverse=True)

    return patient_name, test_date, findings, abnormal, summary_blocks


def group_by_severity_and_section(abnormal: list) -> dict:
    """{'+++': {section: [findings]}, '++': {...}, '+': {...}} - Sections in
    der Reihenfolge ihres ersten Auftretens, Findings bereits nach
    Abweichungsstärke vorsortiert (siehe extract_findings)."""
    result = {"+++": {}, "++": {}, "+": {}}
    for f in abnormal:
        result[f["symbol"]].setdefault(f["section"], []).append(f)
    return result


def group_by_section_ordered(findings: list) -> dict:
    """{section: [findings]} - Sections in der Reihenfolge ihres ersten Auftretens im
    Original-Bericht, Items je Section in Original-Zeilenreihenfolge (kein
    Schweregrad-Sort, im Gegensatz zu group_by_severity_and_section)."""
    result = {}
    for f in findings:
        result.setdefault(f["section"], []).append(f)
    return result


def build_text_report(patient_name, test_date, abnormal, custom_texts) -> str:
    lines = [
        f"Patient: {patient_name}  |  Testdatum: {test_date}",
        f"Auffällige Werte insgesamt: {len(abnormal)}",
        "",
    ]
    grouped = group_by_severity_and_section(abnormal)
    for symbol in ("+++", "++", "+"):
        by_section = grouped[symbol]
        total = sum(len(v) for v in by_section.values())
        if not total:
            continue
        lines.append(f"## {SEVERITY_TITLE[symbol]} ({total})")
        for section, items in by_section.items():
            lines.append(f"### {section}")
            for f in items:
                text = resolve_text(f, custom_texts)
                base = f"- {f['parameter']}: {f['value']:g} ({f['direction']} Norm, Bereich {f['low']:g}-{f['high']:g})"
                if text:
                    base += f" — {text}"
                lines.append(base)
        lines.append("")
    return "\n".join(lines)


def esc(s) -> str:
    return str(s if s is not None else "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def render(chunk: str, mapping: dict) -> str:
    out = chunk
    for key, value in mapping.items():
        out = out.replace("{{" + key + "}}", "" if value is None else str(value))
    return out


def extract_block(template: str, name: str):
    """Schneidet einen '<!-- TPL:NAME --> ... <!-- /TPL:NAME -->'-Baustein aus
    dem Template heraus und gibt (Baustein-Inhalt, Template-ohne-Baustein) zurück."""
    pattern = re.compile(rf"<!--\s*TPL:{name}\s*-->(.*?)<!--\s*/TPL:{name}\s*-->", re.DOTALL)
    m = pattern.search(template)
    if not m:
        raise ValueError(
            f"Template-Baustein '<!-- TPL:{name} -->...<!-- /TPL:{name} -->' fehlt in der "
            f"Template-Datei. Bitte nicht entfernen, nur den Inhalt/Style anpassen."
        )
    return m.group(1), pattern.sub("", template, count=1)


SEVERITY_CLASS = {"+++": "stark", "++": "maessig", "+": "leicht", "-": "normal"}

DEFAULT_TEMPLATE = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<title>Zusammenfassung {{PATIENT_NAME}}</title>
<style>
  body { font-family: Calibri, Arial, sans-serif; max-width: 1000px; margin: 2em auto; padding: 0 1.5em; color: #222; box-sizing: border-box; }
  h1 { margin-bottom: 0.2em; }
  .meta { color: #666; margin-bottom: 1.5em; }
  .severity-block { margin-top: 2em; }
  .severity-block h2 { border-bottom: 2px solid currentColor; padding-bottom: 4px; }
  .severity-stark h2 { color: #d9534f; }
  .severity-maessig h2 { color: #f0ad4e; }
  .severity-leicht h2 { color: #5bc0de; }
  .section-block { margin: 1em 0; }
  .section-block h3 { margin: 0.5em 0 0.3em; color: #555; }
  .section-block .count { font-weight: normal; color: #888; }
  table { border-collapse: collapse; width: 100%; margin-bottom: 0.5em; }
  th, td { border: 1px solid #ccc; padding: 6px 8px; text-align: left; vertical-align: top; }
  th { background: #f0f0f0; }
  .summary-block { margin-top: 2.5em; }
  .summary-group { margin: 1em 0; }
  .summary-group h4 { margin: 0.5em 0 0.3em; color: #555; }
  .summary-tip { background: #f7f7f0; border-left: 3px solid #cbbf7a; padding: 0.6em 0.8em; margin-top: 0.3em; }
  @media print {
    body { padding: 0; margin: 0; max-width: none; }
  }
</style>
</head>
<body>

<h1>Zusammenfassung: {{PATIENT_NAME}}</h1>
<p class="meta">Testdatum: {{TEST_DATE}} | Auffällige Werte insgesamt: {{TOTAL}}
  (stark: {{COUNT_STARK}}, m&auml;&szlig;ig: {{COUNT_MAESSIG}}, leicht: {{COUNT_LEICHT}})</p>

{{BODY}}

{{SUM_BLOCKS}}

<!--
  Ab hier folgen die Bausteine, aus denen die Platzhalter oben zusammengesetzt
  werden. Das Skript schneidet diese Bl&ouml;cke heraus, wiederholt sie mit
  echten Daten und setzt das Ergebnis an der jeweiligen Stelle oben ein.
  Struktur, CSS-Klassen und Text hier frei anpassen - nur die doppelt
  geschweiften Platzhalter und die TPL-Marker-Kommentare m&uuml;ssen
  erhalten bleiben (nicht umbenennen/entfernen).

  BODY (Hauptbefunde, verschachtelt: Abweichungsstufe > K&ouml;rpersystem > Messwert):
-->

<!-- TPL:SEVERITY -->
<div class="severity-block severity-{{SEVERITY_CLASS}}">
  <h2>{{SEVERITY_TITLE}} ({{SEVERITY_COUNT}})</h2>
  {{SECTIONS}}
</div>
<!-- /TPL:SEVERITY -->

<!-- TPL:SECTION -->
<div class="section-block">
  <h3>{{SECTION_NAME}} <span class="count">({{SECTION_COUNT}})</span></h3>
  <table>
    <tr><th>Parameter</th><th>Wert</th><th>Normbereich</th><th>Erkl&auml;rung</th></tr>
    {{ITEMS}}
  </table>
</div>
<!-- /TPL:SECTION -->

<!-- TPL:ITEM -->
<tr class="sev-{{SEVERITY_CLASS}}">
  <td>{{PARAMETER}}</td>
  <td>{{VALUE}} ({{DIRECTION}})</td>
  <td>{{RANGE}}</td>
  <td>{{TEXT}}</td>
</tr>
<!-- /TPL:ITEM -->

<!--
  SUM_BLOCKS (Original-Abschnitt "Zusammenfassender Bericht", verschachtelt:
  Unterkapitel > K&ouml;rpersystem-Gruppe > Messwert. Der Wellness-Tipp-Text
  ist 1:1 aus dem Original &uuml;bernommen, patientenspezifisch, nur die am
  Ende angeh&auml;ngte Produktliste wurde entfernt):
-->

<!-- TPL:SUMBLOCK -->
<div class="summary-block">
  <h2>{{SUM_TITLE}}</h2>
  {{SUM_GROUPS}}
</div>
<!-- /TPL:SUMBLOCK -->

<!-- TPL:SUMGROUP -->
<div class="summary-group">
  <h4>{{SUM_SYSTEM}}</h4>
  <table>
    <tr><th>Eigenschaft</th><th>Wert</th><th>Normbereich</th></tr>
    {{SUM_ITEMS}}
  </table>
  <p class="summary-tip">{{SUM_TIP}}</p>
</div>
<!-- /TPL:SUMGROUP -->

<!-- TPL:SUMITEM -->
<tr>
  <td>{{SUM_PARAMETER}}</td>
  <td>{{SUM_VALUE}}</td>
  <td>{{SUM_RANGE}}</td>
</tr>
<!-- /TPL:SUMITEM -->

</body>
</html>
"""


def ensure_template(path: str):
    if not os.path.exists(path):
        with open(path, "w", encoding="utf-8") as f:
            f.write(DEFAULT_TEMPLATE)
        return True
    return False


def build_summary_blocks_html(template_str: str, summary_blocks: list):
    sumitem_tpl, template_str = extract_block(template_str, "SUMITEM")
    sumgroup_tpl, template_str = extract_block(template_str, "SUMGROUP")
    sumblock_tpl, template_str = extract_block(template_str, "SUMBLOCK")

    block_parts = []
    for block in summary_blocks:
        group_parts = []
        for group in block["groups"]:
            item_parts = [render(sumitem_tpl, {
                "SUM_PARAMETER": esc(item["parameter"]),
                "SUM_VALUE": f"{item['value']:g}",
                "SUM_RANGE": f"{item['low']:g} - {item['high']:g}",
            }) for item in group["items"]]
            group_parts.append(render(sumgroup_tpl, {
                "SUM_SYSTEM": esc(group["system"]),
                "SUM_ITEMS": "".join(item_parts),
                "SUM_TIP": esc(group["tip"]),
            }))
        block_parts.append(render(sumblock_tpl, {
            "SUM_TITLE": esc(block["title"]),
            "SUM_GROUPS": "".join(group_parts),
        }))

    return "".join(block_parts), template_str


def build_html_report(template_str: str, patient_name, test_date, abnormal, custom_texts,
                       summary_blocks=None, include_body=True) -> str:
    """include_body=False erzeugt die Fazit-Variante: optisch dasselbe Template,
    aber ohne die detaillierte Auflistung aller einzelnen Messwerte (BODY) -
    nur Kopfdaten und der Zusammenfassungsabschnitt (SUM_BLOCKS)."""
    item_tpl, template_str = extract_block(template_str, "ITEM")
    section_tpl, template_str = extract_block(template_str, "SECTION")
    severity_tpl, template_str = extract_block(template_str, "SEVERITY")
    sum_blocks_html, template_str = build_summary_blocks_html(template_str, summary_blocks or [])

    grouped = group_by_severity_and_section(abnormal)
    body_parts = []
    for symbol in ("+++", "++", "+") if include_body else ():
        by_section = grouped[symbol]
        total = sum(len(v) for v in by_section.values())
        if not total:
            continue
        sev_class = SEVERITY_CLASS[symbol]

        section_parts = []
        for section, items in by_section.items():
            item_parts = []
            for f in items:
                text = resolve_text(f, custom_texts)
                item_parts.append(render(item_tpl, {
                    "SECTION": esc(section),
                    "PARAMETER": esc(f["parameter"]),
                    "VALUE": f"{f['value']:g}",
                    "RANGE": f"{f['low']:g} - {f['high']:g}",
                    "DIRECTION": esc(f["direction"]),
                    "SEVERITY_SYMBOL": symbol,
                    "SEVERITY_CLASS": sev_class,
                    "TEXT": esc(text),
                }))
            section_parts.append(render(section_tpl, {
                "SECTION_NAME": esc(section),
                "SECTION_COUNT": len(items),
                "SEVERITY_CLASS": sev_class,
                "ITEMS": "".join(item_parts),
            }))

        body_parts.append(render(severity_tpl, {
            "SEVERITY_TITLE": SEVERITY_TITLE[symbol],
            "SEVERITY_SYMBOL": symbol,
            "SEVERITY_COUNT": total,
            "SEVERITY_CLASS": sev_class,
            "SECTIONS": "".join(section_parts),
        }))

    return render(template_str, {
        "PATIENT_NAME": esc(patient_name),
        "TEST_DATE": esc(test_date),
        "TOTAL": len(abnormal),
        "COUNT_STARK": sum(1 for f in abnormal if f["symbol"] == "+++"),
        "COUNT_MAESSIG": sum(1 for f in abnormal if f["symbol"] == "++"),
        "COUNT_LEICHT": sum(1 for f in abnormal if f["symbol"] == "+"),
        "BODY": "".join(body_parts),
        "SUM_BLOCKS": sum_blocks_html,
    })


def safe_filename_part(name: str) -> str:
    """Macht einen Patientennamen dateisystemtauglich (Windows-verbotene
    Zeichen entfernen), für die Verwendung im Ausgabedateinamen."""
    name = re.sub(r'[<>:"/\\|?*\x00-\x1f]', "", name).strip()
    return name or "Unbekannt"


def main():
    ap = argparse.ArgumentParser(description="Kompakte, deterministische Zusammenfassung eines HTML-Vitalstoffberichts.")
    ap.add_argument("html", help="Pfad zur .htm/.html-Datei (Bilder-Ordner muss danebenliegen)")
    ap.add_argument("--out", default=None, help="Zieldatei für den Text-Report (optional, wird nur bei Angabe geschrieben)")
    ap.add_argument("--html-out", default=None,
                     help="Zieldatei für den HTML-Report (Default: zusammenfassung_<Name>.html)")
    ap.add_argument("--fazit-out", default=None,
                     help="Zieldatei für den Fazit-Report (Default: zusammenfassung_<Name>_fazit.html). "
                          "Optisch identisches Template, aber ohne die Einzelwert-Auflistung - nur der "
                          "Zusammenfassungsabschnitt (Original 'Zusammenfassender Bericht' inkl. Wellness-Tipps).")
    ap.add_argument("--template", default="template.html",
                     help="HTML-Vorlage mit {{PLATZHALTER}}n für Layout/Design (Default: template.html). "
                          "Wird beim ersten Lauf automatisch mit einem editierbaren Grundgerüst angelegt, "
                          "falls nicht vorhanden.")
    ap.add_argument("--texts", default="parameter_texts.yml",
                     help="YAML-Datei mit vordefinierten, abweichungsspezifischen Erklärtexten "
                          "(Default: parameter_texts.yml). Wird beim ersten Lauf automatisch aus "
                          "den Originaltexten des Berichts vorbefüllt, falls nicht vorhanden.")
    args = ap.parse_args()

    patient_name, test_date, findings, abnormal, summary_blocks = extract_findings(args.html)
    name_part = safe_filename_part(patient_name)
    html_out = args.html_out or f"zusammenfassung_{name_part}.html"
    fazit_out = args.fazit_out or f"zusammenfassung_{name_part}_fazit.html"

    print(f"Patient: {patient_name}  |  Testdatum: {test_date}")
    print(f"Gelesene Parameter gesamt: {len(findings)}")
    print(f"Davon außerhalb des Normbereichs: {len(abnormal)}")
    print(f"  stark: {sum(1 for f in abnormal if f['symbol']=='+++')}  "
          f"mäßig: {sum(1 for f in abnormal if f['symbol']=='++')}  "
          f"leicht: {sum(1 for f in abnormal if f['symbol']=='+')}\n")

    if not os.path.exists(args.texts):
        write_texts_template(findings, args.texts)
        print(f"Hinweis: '{args.texts}' existierte noch nicht und wurde mit den Originaltexten "
              f"als Vorlage angelegt. Trage dort bei Bedarf eigene, abweichungsspezifische "
              f"Texte unter über/leicht|mäßig|stark bzw. unter/leicht|mäßig|stark ein.\n")
    custom_texts = load_custom_texts(args.texts)

    if ensure_template(args.template):
        print(f"Hinweis: '{args.template}' existierte noch nicht und wurde mit einem Grundgerüst "
              "angelegt. Layout/CSS dort frei anpassen (nur die {{PLATZHALTER}} und "
              "<!-- TPL:... --> Marker müssen erhalten bleiben).\n")

    text_report = build_text_report(patient_name, test_date, abnormal, custom_texts)
    print(text_report)

    if args.out:
        with open(args.out, "w", encoding="utf-8") as f:
            f.write(text_report)
        print(f"\nText-Report gespeichert: {args.out}")

    with open(args.template, encoding="utf-8") as f:
        template_str = f.read()

    html_report = build_html_report(template_str, patient_name, test_date, abnormal, custom_texts, summary_blocks)
    with open(html_out, "w", encoding="utf-8") as f:
        f.write(html_report)
    print(f"HTML-Report gespeichert: {html_out}")

    fazit_report = build_html_report(template_str, patient_name, test_date, abnormal, custom_texts,
                                      summary_blocks, include_body=False)
    with open(fazit_out, "w", encoding="utf-8") as f:
        f.write(fazit_report)
    print(f"Fazit-Report gespeichert: {fazit_out}")


if __name__ == "__main__":
    main()
