#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Verarbeitet Erfahrungsberichte-CSV nach den definierten Regeln.
"""

import csv
import re
from datetime import datetime
from uuid import UUID, uuid5, NAMESPACE_DNS


# ─── Datum ────────────────────────────────────────────────────────────────────

def normalize_date(raw: str) -> str:
    if not raw:
        return ""
    s = raw.strip()
    if 'T' in s:
        s = s.split('T')[0]
    for fmt in ("%Y-%m-%d", "%d.%m.%Y", "%d.%m.%y"):
        try:
            return datetime.strptime(s, fmt).strftime("%Y-%m-%d")
        except ValueError:
            pass
    return s


def strip_text_date_prefix(text: str):
    """Entferne Datum am Textanfang. Gibt (Rest-Text, YYYY-MM-DD|None) zurück."""
    m = re.match(r'^(\d{1,2})\.(\d{1,2})\.(\d{2,4})\s*[-–]?\s*', text)
    if not m:
        return text, None
    d, mo, y = int(m.group(1)), int(m.group(2)), int(m.group(3))
    if y < 100:
        y += 2000 if y <= 30 else 1900
    try:
        date_str = datetime(y, mo, d).strftime("%Y-%m-%d")
    except ValueError:
        return text, None
    return text[m.end():].strip(), date_str


# ─── Namen-Wörterbuch ─────────────────────────────────────────────────────────

def _name_to_initials(full_name: str) -> str:
    """'Max Mustermann' → 'M. M.' für 2+-teilige Namen."""
    parts = full_name.strip().split()
    if len(parts) >= 2:
        return ' '.join(p[0] + '.' for p in parts)
    return full_name  # Einzelnamen nicht kürzen


def collect_person_names(rows: list) -> dict:
    """
    Durchsucht alle CSV-Zeilen nach echten Personennamen und gibt ein Dict
    {vollständiger_name: anonymisierte_form} zurück.
    Quellen: Autor-Spalte, [Weitergeleitet von X], (von X) am Textanfang.
    """
    names = {}

    name_pat = re.compile(
        r'\[Weitergeleitet von ([^\]]+)\]'
        r'|(?:^|\d\s*[-–]\s*)(?:[^\(]{0,80})?\(von ([^)]{3,50})\)'
        r'|^Von ([A-ZÄÖÜ][a-zäöüß]+ [A-ZÄÖÜ][a-zäöüß]+)[,:\s]',
        re.MULTILINE,
    )

    for row in rows:
        # Autor-Spalte
        autor = row[0].strip() if row[0] else ""
        if autor and ' ' in autor:
            names[autor] = _name_to_initials(autor)

        # Text-Spalte
        text = row[2].strip() if len(row) > 2 else ""
        for m in name_pat.finditer(text):
            for grp in m.groups():
                if grp:
                    n = grp.strip()
                    # Mehrwörtig?
                    if ' ' in n:
                        names[n] = _name_to_initials(n)

    return names


def build_name_replacer(names: dict):
    """
    Baut einen kompilierten Regex + Ersetzungs-Mapping, der alle bekannten
    Vor-Nachname-Kombinationen im Text erkennt.
    """
    if not names:
        return None, {}

    # Nach Länge sortieren (längste zuerst), damit Teilmengen nicht früher matchen
    sorted_names = sorted(names.keys(), key=len, reverse=True)
    pattern = re.compile(
        r'\b(' + '|'.join(re.escape(n) for n in sorted_names) + r')\b'
    )
    return pattern, names


# ─── Autor ────────────────────────────────────────────────────────────────────

def extract_true_author(text: str, fallback: str) -> str:
    """Ermittle den wahren Verfasser aus Weiterleitungsmarkern im Text."""
    # [Weitergeleitet von X]
    m = re.search(r'\[Weitergeleitet von ([^\]]+)\]', text)
    if m:
        return m.group(1).strip()
    # Datum + (von X) im ersten Abschnitt
    m = re.search(
        r'(?:^\d{1,2}\.\d{1,2}\.\d{2,4}\s*[-–]\s*[^\(]{0,80})?\(von ([^)]{3,50})\)',
        text[:200],
    )
    if m:
        return m.group(1).strip()
    # "Von Vorname Nachname:"
    m = re.match(r'^Von ([A-ZÄÖÜ][a-zäöüß]+ [A-ZÄÖÜ][a-zäöüß]+)[,:\s]', text)
    if m:
        return m.group(1).strip()
    return fallback


def anonymize_author(name: str) -> str:
    """Kürzt auf Vorname + Anfangsbuchstabe Nachname."""
    name = name.strip()
    parts = name.split()
    if len(parts) >= 2:
        return f"{parts[0]} {parts[-1][0]}."
    return parts[0] if parts else name


# ─── Relevanz ─────────────────────────────────────────────────────────────────

_SENTENCE_WORDS = re.compile(
    r'\b('
    r'ich|mir|mich|mein|meine|meiner|meinem|meinen|'
    r'wir|uns|unser|unsere|unseren|unserem|'
    r'er|sie|es|ihn|ihm|ihr|ihnen|'
    r'habe|hatte|haben|hatten|bin|war|ist|sind|waren|wird|wurde|werden|wurden|'
    r'nehme|nahm|nimmt|genommen|'
    r'kann|konnte|könnte|muss|musste|darf|durfte|soll|sollte|'
    r'seit|nach|vor|bei|durch|mit|ohne|für|gegen|über|unter|auf|an|in|zu|von|'
    r'und|aber|oder|dass|weil|wenn|da|als|wie|obwohl|'
    r'der|die|das|ein|eine|einen|einem|einer|'
    r'sehr|mehr|viel|besser|gut|schlimm|stark|deutlich|'
    r'nicht|keine|kein|keinen|keinem|'
    r'schon|noch|auch|immer|wieder|nun|jetzt'
    r')\b',
    re.IGNORECASE,
)


def is_just_keywords(text: str) -> bool:
    """True wenn kein echter Satzinhalt erkennbar ist."""
    t = re.sub(r'^\d{1,2}\.\d{1,2}\.\d{2,4}\s*[-–]?\s*', '', text)
    t = re.sub(r'\[Weitergeleitet von [^\]]+\]\s*', '', t)
    t = re.sub(r'\[Antwort auf [^\]]+\]\s*', '', t)
    t = re.sub(r'^\(von [^)]+\)\s*', '', t)
    t = t.strip()

    if len(t) < 50:
        return True

    words = t.split()
    if len(words) < 6:
        return True

    hits = len(_SENTENCE_WORDS.findall(t))
    return (hits / len(words)) < 0.08


# ─── Textbereinigung ──────────────────────────────────────────────────────────

_EMOJI = re.compile(
    "["
    "\U0001F600-\U0001F64F"
    "\U0001F300-\U0001F5FF"
    "\U0001F680-\U0001F6FF"
    "\U0001F1E0-\U0001F1FF"
    "\U00002702-\U000027B0"
    "\U000024C2-\U0001F251"
    "]+",
    flags=re.UNICODE,
)

_FAREWELL = re.compile(
    r'(Liebe[n]?\s+Grüße[n]?|Liebe[n]?\s+Grüsse[n]?|Herzliche[n]?\s+Grüße[n]?|'
    r'Schöne[n]?\s+Grüße[n]?|Viele[n]?\s+Grüße[n]?|Herzlichst|'
    r'Mit\s+freundlichen\s+Grüßen|Mit\s+lieben\s+Grüßen|'
    r'Herzliche\s+Grüsse|Liebe\s+Grüessen|Alles\s+Liebe|Alles\s+Gute|'
    r'Liebs\s+Grüessli|Liebes\s+Grüessli|'
    r'(?:Gruß|Gruss|Grüße|Grüsse)\s+\w+)[^.!?]*$',
    re.IGNORECASE | re.DOTALL,
)

_LG_END = re.compile(r'\bLG\b[^\n.!?]*$', re.IGNORECASE)

# Anredeformel: am Start ODER nach maximal 5 Keyword-Wörtern am Anfang
_GREETING_START = re.compile(
    r'^(?:[A-ZÄÖÜ][a-zäöüß\-]+ ){0,5}'
    r'(Hallo|Hi|Hey|Liebe[r]?|Guten\s+(?:Morgen|Abend|Tag)|Halli\s+Hallo)\s+'
    r'[A-ZÄÖÜ][a-zäöüß]+(?:\s+[A-ZÄÖÜ][a-zäöüß]+)?\s*[,!.]?\s*',
    re.IGNORECASE,
)

# Telegram-Chatformat: "Name Name, [TT.MM.JJ HH:MM]"
_TELEGRAM_HEADER = re.compile(
    r'[A-ZÄÖÜ][a-zäöüß]+\s+[A-ZÄÖÜ][a-zäöüß]+,\s+\[\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2}\]\s*'
    r'(?:\[Weitergeleitet von [^\]]+\]\s*)?',
)


def _word_matches_tag(word: str, tags: set) -> bool:
    """Prüft ob ein Wort zu den Hashtag-Tags passt (auch Teilwort-Match)."""
    w = re.sub(r'[^\wäöüÄÖÜß]', '', word).lower()
    if not w:
        return False
    for tag in tags:
        # Exakt, oder Tag beginnt mit Wort (z.B. "Knieschmerzen" beginnt mit "Knie")
        if w == tag or tag.startswith(w) or w.startswith(tag):
            return True
    return False


def remove_hashtag_prefix(text: str, hashtags: str) -> str:
    """
    Entfernt führende Keyword-Wörter, die mit den Hashtag-Wörtern übereinstimmen.
    Beispiel: 'Knieschmerzen KNIE Ich hatte' → 'Ich hatte'
    """
    if not hashtags:
        return text
    tags = {t.strip().lower() for t in hashtags.split(',') if t.strip()}
    if not tags:
        return text

    words = text.split()
    i = 0
    while i < min(len(words), 10):
        if _word_matches_tag(words[i], tags) or words[i] in ('-', '–', ','):
            i += 1
        else:
            break

    if i > 0 and i < len(words):
        return ' '.join(words[i:]).strip()
    return text


def clean_text(raw: str, name_pattern, name_map: dict, hashtags: str = '') -> str:
    t = raw

    # URLs und E-Mails
    t = re.sub(r'https?://\S+', '', t)
    t = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b', '', t)
    # Telefonnummern (DE/CH/AT-Formate, mind. 10 Ziffern)
    t = re.sub(r'(?<!\d)(\+?\d[\d\s\-/()]{9,})(?!\d)', '', t)
    # Emojis
    t = _EMOJI.sub(' ', t)

    # Telegram-Nachrichtenkopf
    t = _TELEGRAM_HEADER.sub('', t)

    # Weiterleitungs- und Antwort-Marker
    t = re.sub(r'\[Weitergeleitet von [^\]]+\]\s*', '', t)
    t = re.sub(r'\[Antwort auf [^\]]+\]\s*', '', t)
    # Zeitstempel in eckigen Klammern
    t = re.sub(r'\[\d{2}\.\d{2}\.\d{2,4}\s+\d{2}:\d{2}\]\s*', '', t)

    # Datum-Präfix am Textanfang
    t, _ = strip_text_date_prefix(t)

    # (von X) Attributionsmarker überall im Text entfernen
    t = re.sub(r'\s*\(von [^)]{2,50}\)\s*', ' ', t)
    # (bereits oben nach Hashtag-Entfernung behandelt)

    # Anredeformeln am Anfang (ggf. nach Keyword-Präfix)
    t = _GREETING_START.sub('', t)

    # Grußformeln am Ende
    t = _FAREWELL.sub('', t)
    t = _LG_END.sub('', t)

    # Hashtag-basierte Keyword-Präfixe am Textanfang entfernen
    # (auch "Erfahrungsbericht" als Sonderwort hier behandeln)
    t = remove_hashtag_prefix(t, hashtags)
    # Zweiter Durchlauf: falls nach Hashtag-Entfernung nun "Erfahrungsbericht" vorne steht
    t = re.sub(r'^Erfahrungsbericht(?:\s+vom?\s+[^-–]+[-–]\s*|\s+[A-ZÄÖÜ][a-zäöüß]+\s+|\s+)(?![A-Z]{2,})', '', t, flags=re.IGNORECASE)
    t = remove_hashtag_prefix(t, hashtags)

    # "von Vorname" ohne Klammern als erstes Wort am Textanfang entfernen
    t = re.sub(r'^von [A-ZÄÖÜ][a-zäöüß]+ ', '', t)

    # Standalone '#' entfernen (Überbleibsel aus Parsing)
    t = re.sub(r'(?<!\w)#(?!\w)', '', t)

    # Bekannte Personennamen durch Initialen ersetzen
    if name_pattern:
        t = name_pattern.sub(lambda m: name_map.get(m.group(0), m.group(0)), t)

    # Zusammengeschriebene camelCase-Wörter aufteilen (z.B. KnieschmerzenKNIE)
    t = re.sub(r'([a-zäöüß])([A-ZÄÖÜ])', r'\1 \2', t)

    # Whitespace normalisieren
    t = re.sub(r'[ \t]{2,}', ' ', t)
    t = re.sub(r'\n{3,}', '\n\n', t)
    return t.strip()


# ─── Hashtags ─────────────────────────────────────────────────────────────────

def process_hashtags(text: str, existing: str):
    """Extrahiert #Tags, entfernt # im Text, führt mit existing zusammen."""
    found = re.findall(r'#(\w+)', text)
    cleaned = re.sub(r'#(\w+)', r'\1', text)

    seen = {}
    for tag in existing.split(','):
        tag = tag.strip().lstrip('#')
        if tag and tag.lower() not in seen:
            seen[tag.lower()] = tag
    for tag in found:
        if tag and tag.lower() not in seen:
            seen[tag.lower()] = tag

    return cleaned, ','.join(seen.values())


# ─── Kürzen ───────────────────────────────────────────────────────────────────

def truncate_text(text: str, limit: int = 1500) -> str:
    if len(text) <= limit:
        return text
    cut = text[:limit]
    for sep in ('.', '!', '?'):
        pos = cut.rfind(sep)
        if pos > limit * 0.75:
            return cut[:pos + 1].strip()
    pos = cut.rfind(' ')
    return (cut[:pos] if pos > 0 else cut).strip()


# ─── UUID ──────────────────────────────────────────────────────────────────────

def is_valid_uuid5(s: str) -> bool:
    try:
        return UUID(s).version == 5
    except (ValueError, AttributeError):
        return False


def make_uuid5(author: str, date: str, text: str) -> str:
    return str(uuid5(NAMESPACE_DNS, f"{author}|{date}|{text}"))


# ─── Hauptverarbeitung ────────────────────────────────────────────────────────

def process_csv(input_file: str, output_file: str):
    # Alle Zeilen einlesen
    with open(input_file, encoding='utf-8') as f:
        reader = csv.reader(f)
        next(reader)
        all_rows = [row for row in reader]

    # Pass 1: Personennamen-Wörterbuch aufbauen
    name_map = collect_person_names(all_rows)
    name_pattern, name_map = build_name_replacer(name_map)

    kept = []
    skipped = []

    # Pass 2: Verarbeitung
    for lineno, row in enumerate(all_rows, start=2):
        row += [''] * (6 - len(row))
        orig_autor, datum, text, hashtags, bilder, uuid_val = (c.strip() for c in row[:6])

        # Datum normalisieren
        norm_date = normalize_date(datum)

        # Relevanz prüfen
        if not text or len(text) < 50 or is_just_keywords(text):
            skipped.append((lineno, orig_autor, repr(text[:60])))
            continue

        # Hashtags vor Bereinigung extrahieren
        text_no_hash, merged_hashtags = process_hashtags(text, hashtags)

        # Wahren Autor ermitteln (vor Bereinigung, um Marker noch zu finden)
        true_author_raw = extract_true_author(text_no_hash, orig_autor)
        anon_author = anonymize_author(true_author_raw)

        # Text bereinigen
        text_clean = clean_text(text_no_hash, name_pattern, name_map, merged_hashtags)

        if len(text_clean.strip()) < 30:
            skipped.append((lineno, orig_autor, 'nach Bereinigung zu kurz'))
            continue

        # Text kürzen
        text_final = truncate_text(text_clean)

        # UUID
        if bilder:
            uuid_final = uuid_val if is_valid_uuid5(uuid_val) else make_uuid5(anon_author, norm_date, text_final)
        else:
            uuid_final = ''

        kept.append([anon_author, norm_date, text_final, merged_hashtags, bilder, uuid_final])

    # Ausgabe schreiben
    with open(output_file, 'w', encoding='utf-8', newline='') as f:
        writer = csv.writer(f, quoting=csv.QUOTE_MINIMAL)
        writer.writerow(['Autor', 'Datum', 'Erfahrungsbericht', 'Hashtags', 'Bilder', 'UUID'])
        writer.writerows(kept)

    print(f"OK: {len(kept)} Eintraege behalten, {len(skipped)} uebersprungen\n")
    print("Uebersprungene Eintraege:")
    for ln, autor, preview in skipped:
        print(f"  Zeile {ln:>3} ({autor}): {preview}")

    print("\nGesammelte Personennamen (werden anonymisiert):")
    for n, init in sorted(name_map.items()):
        print(f"  {n!r} -> {init!r}")

    return len(kept), len(skipped)


if __name__ == '__main__':
    process_csv(
        r'D:\Entwicklung\cili\erfahrungsberichte_01.csv',
        r'D:\Entwicklung\cili\erfahrungsberichte_01_processed.csv',
    )
