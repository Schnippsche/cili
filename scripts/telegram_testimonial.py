"""
Erfahrungsbericht-Pipeline für Telegram-Import — gemeinsam genutzt von
telegram_import_mensch.py und telegram_import_tier.py: KI-Klassifikation,
Whisper-Transkription als Klassifikationshilfe, Testimonial-Posting an CILI.

Webinar-Nachrichten werden hier nur erkannt und übersprungen (kein Download/
Upload mehr — das übernimmt telegram_import_webinare.py als eigener,
wöchentlicher Lauf).
"""

import asyncio
import json
import os
import re
import shutil
import subprocess
import tempfile
import time
from dataclasses import dataclass, field
from datetime import datetime
from io import BytesIO
from pathlib import Path

import ftfy
import requests

import telegram_common as common
from video_upload import upload_file


@dataclass
class TestimonialRunConfig:
    # Telegram
    tg_api_id: int
    tg_api_hash: str
    tg_phone: str
    tg_group: str
    session_file: Path

    # Quelle
    source_name: str          # "Mensch" | "Tier" — State-Datei-Name, Log-Präfix
    is_human: bool
    is_animal: bool

    # Lokale KI (Ollama)
    ai_url: str
    ai_model: str
    ai_timeout: int
    ai_num_ctx: int
    ai_num_predict: int
    ai_retry_backoff_seconds: float
    min_confidence: float

    # CILI
    cili_url: str
    cili_user: str
    cili_pass: str

    # Whisper (Klassifikationshilfe)
    whisper_model: str
    whisper_device: str
    whisper_compute_type: str
    whisper_lang: str

    # Limits
    max_messages_per_run: int
    max_runtime_minutes: float

    # Pfade / Sonstiges
    state_file: Path
    manual_cutoff: datetime | None = None
    max_transcript_chars: int = 6000


# ── KI-Prompt ─────────────────────────────────────────────────────────────────

CLASSIFY_PROMPT = """\
Analysiere die folgende Telegram-Nachricht und entscheide, ob es sich um einen persönlichen \
Erfahrungsbericht handelt (jemand beschreibt eine eigene Erfahrung, ein Ergebnis oder eine \
Meinung zu einem Produkt, Kurs oder einer Dienstleistung).

Antworte NUR mit einem JSON-Objekt — kein Markdown, keine Erklärungen:
{{
  "is_testimonial": true,
  "confidence": 0.0,
  "author_name": "",
  "text": "",
  "tags": ""
}}

Regeln für die Felder:

is_testimonial: true wenn Erfahrungsbericht, sonst false.

confidence: deine Sicherheit (0.0 bis 1.0).

author_name:
  1. Suche zuerst im Text nach Mustern wie "Bericht von [Name]", "Bericht via [Name]", "Erfahrung von [Name]", "von [Name]:", "[Name] berichtet", "[Name] schreibt", "Mein Name ist [Name]", o.ä.
  2. Falls gefunden: verwende diesen Namen.
  3. Falls nicht gefunden: verwende den Telegram-Absendernamen.
  4. Format IMMER: nur Vorname + Leerzeichen + erster Buchstabe des Nachnamens + Punkt (z.B. "Peter G." statt "Peter Geisler", "Maria S." statt "Maria Schmidt").
  5. Nie den vollen Nachnamen ausgeben (Datenschutz) und ansonsten auch keine vollständigen Namen.
  6. Emojis im Absendernamen weglassen.

text:
  1. Der bereinigte Erfahrungstext.
  2. Einleitungszeilen wie "Bericht von [Name]:" oder "Erfahrungsbericht von [Name]:" am Anfang entfernen.
  3. #-Zeichen vor Wörtern entfernen, das Wort selbst behalten (aus "#COPD" wird "COPD").
  4. Grußformeln, reine Weiterleitungshinweise und Emojis weglassen.
  5. Falls der Text länger als 400 Zeichen ist: inhaltlich zusammenfassen. Die wichtigsten Aussagen (Symptome, Produkt, Verbesserungen) beibehalten, Wiederholungen und Fülltext weglassen. Maximal 400 Zeichen.
  6. Falls kürzer als 400 Zeichen: unverändert übernehmen.

tags:
  1. Alle #Hashtags aus dem Original-Text als kommagetrennte Liste, ohne #-Zeichen.
  2. Keine Duplikate (Groß-/Kleinschreibung ignorieren).
  3. Beispiel: aus "#COPD" und "#Husten" wird "COPD,Husten".
  4. Falls keine Hashtags vorhanden: leerer String.

Wenn is_testimonial=false: author_name, text und tags als leere Strings zurückgeben.

Absender: {sender_name}
Nachricht:
{message_text}"""


# ── Hilfsfunktionen ───────────────────────────────────────────────────────────

def shorten_name(name: str) -> str:
    """'Peter Geisler' → 'Peter G.'  |  bereits kurze Namen bleiben unverändert."""
    name = name.strip()
    parts = name.split()
    if len(parts) >= 2 and len(parts[-1]) > 1:
        return f"{' '.join(parts[:-1])} {parts[-1][0]}."
    return name


_EMOJI_RE = re.compile(
    "["
    "\U0001F000-\U0001FFFF"   # diverse Emoji-Blöcke (Emoticons, Symbole, Flags…)
    "\U00002600-\U000027FF"   # Verschiedene Symbole, Dingbats
    "\U00002B00-\U00002BFF"   # Weitere Symbole
    "\U0000FE00-\U0000FE0F"   # Variationsselektor
    "\U0001F3FB-\U0001F3FF"   # Hautton-Modifier
    "]+",
    flags=re.UNICODE,
)


def post_process_result(result: dict) -> dict:
    """Bereinigt KI-Ausgabe unabhängig davon ob die KI die Regeln befolgt hat."""
    if result.get("author_name"):
        result["author_name"] = shorten_name(result["author_name"])

    if result.get("text"):
        text = re.sub(r"#(\w+)", r"\1", result["text"])
        text = _EMOJI_RE.sub("", text)
        text = re.sub(r"\s+", " ", text)  # normalize multiple spaces
        result["text"] = text.strip()

    if result.get("tags"):
        raw_tags = [t.strip().lstrip("#") for t in result["tags"].split(",") if t.strip()]
        seen: set[str] = set()
        deduped = []
        for tag in raw_tags:
            if tag.lower() not in seen:
                seen.add(tag.lower())
                deduped.append(tag)
        result["tags"] = ",".join(deduped)

    return result


def fix_json_control_chars(s: str) -> str:
    """Maskiert unkodierte Steuerzeichen innerhalb von JSON-Strings.

    LLMs schreiben manchmal echte Newlines in JSON-Stringwerte, was json.loads
    mit 'Invalid control character' abbricht. Diese Funktion korrigiert das.
    """
    result = []
    in_string = False
    escaped = False
    for ch in s:
        if escaped:
            result.append(ch)
            escaped = False
        elif ch == "\\" and in_string:
            result.append(ch)
            escaped = True
        elif ch == '"':
            result.append(ch)
            in_string = not in_string
        elif in_string and ord(ch) < 0x20:
            escapes = {"\n": "\\n", "\r": "\\r", "\t": "\\t"}
            result.append(escapes.get(ch, f"\\u{ord(ch):04x}"))
        else:
            result.append(ch)
    return "".join(result)


def gpu_temperature() -> str:
    """Liest die aktuelle GPU-Temperatur über nvidia-smi aus (z.B. '75'). Liefert '?' falls nicht verfügbar."""
    try:
        out = subprocess.run(
            ['nvidia-smi', '--query-gpu=temperature.gpu', '--format=csv,noheader,nounits'],
            capture_output=True, text=True, timeout=5, check=True,
        )
        return out.stdout.strip().splitlines()[0]
    except Exception:
        return '?'


def classify_with_ai(ai_url: str, ai_model: str, ai_timeout: int, ai_num_ctx: int,
                     ai_num_predict: int, ai_retry_backoff_seconds: float,
                     sender_name: str, message_text: str) -> dict:
    # ftfy repariert kaputtes Unicode (Mojibake) vor dem Versand an die KI
    message_text = ftfy.fix_text(message_text)
    prompt = CLASSIFY_PROMPT.format(sender_name=sender_name, message_text=message_text)
    # Ollamas native Route (/api/generate) statt der OpenAI-kompatiblen (/v1/chat/completions):
    # Über Letztere unterdrückte think:false das Qwen3-Reasoning NICHT zuverlässig — Ollama
    # dachte trotzdem weiter (nur ins separate "reasoning"-Feld statt "content"), was wiederholt
    # das gesamte max_tokens-Budget aufbrauchte, bevor content geschrieben wurde. Über
    # /api/generate wird think:false zuverlässig respektiert. Kehrseite: bricht Kompatibilität
    # zu OpenAI-artigen Backends wie LM Studio.
    print(f"  Sende an {ai_url} (Modell: {ai_model}, GPU-Temp={gpu_temperature()}°C)")
    resp = common.http_post_with_retry(
        f"{ai_url}/api/generate",
        base_backoff=ai_retry_backoff_seconds,
        json={
            "model": ai_model,
            "prompt": prompt,
            "stream": False,
            "think": False,
            "options": {
                "temperature": 0.1,
                "num_ctx": ai_num_ctx,
                "num_predict": ai_num_predict,
            },
        },
        timeout=ai_timeout,
    )
    body = resp.json()
    if body.get("done_reason") == "length":
        print(f"  WARNUNG: KI-Antwort durch num_predict={ai_num_predict} abgeschnitten "
              f"(done_reason=length)")
    raw = body["response"].strip()
    if "```" in raw:
        parts = raw.split("```")
        raw = parts[1].removeprefix("json").strip() if len(parts) > 1 else raw
    try:
        return json.loads(raw)
    except json.JSONDecodeError as exc:
        try:
            return json.loads(fix_json_control_chars(raw))
        except json.JSONDecodeError:
            raise json.JSONDecodeError(
                f"{exc.msg} (raw response: {raw[:200]!r}, full body: {json.dumps(body)[:1000]!r})",
                exc.doc, exc.pos
            ) from exc


_whisper_model = None


def _get_whisper_model(model_name: str, device: str, compute_type: str):
    """Lazy-Singleton — Modell wird höchstens einmal pro Skriptlauf geladen."""
    global _whisper_model
    if _whisper_model is None:
        from faster_whisper import WhisperModel
        _whisper_model = WhisperModel(model_name, device=device, compute_type=compute_type)
    return _whisper_model


def transcribe_for_classification(path: Path, model_name: str, device: str,
                                  compute_type: str, lang: str) -> str | None:
    """Grobe Klartext-Transkription eines Video-/Audio-Anhangs als Klassifikations-
    hilfe bei kurzer/fehlender Bildunterschrift — kein Cue-Timing/Sync/Glossar wie
    in transcribe_worker.py, nur Text. Die echten Untertitel entstehen unabhängig
    davon automatisch nach dem Upload über die bestehende Backend-Pipeline.
    None bei jedem Fehler (fehlende Abhängigkeit, kein GPU, Decode-Fehler, …) —
    der Aufrufer behandelt das wie eine Nachricht ohne verwertbaren Text."""
    try:
        model = _get_whisper_model(model_name, device, compute_type)
        language = None if lang == "auto" else lang
        segments, _ = model.transcribe(str(path), language=language, beam_size=1, vad_filter=True)
        text = " ".join(seg.text.strip() for seg in segments).strip()
        return text or None
    except Exception as exc:
        print(f"  Transkription fehlgeschlagen: {exc} — ignoriert")
        return None


# ── CILI ──────────────────────────────────────────────────────────────────────

def get_cili_token(cfg: TestimonialRunConfig) -> str:
    return common.get_cili_token(cfg.cili_url, cfg.cili_user, cfg.cili_pass,
                                  env_token=os.getenv("CILI_TOKEN"))


def post_testimonial(*, cili_url: str, token: str, author: str, text: str, tags: str,
                     created_at: datetime, images: list, is_human: bool, is_animal: bool) -> dict:
    headers = {"Authorization": f"Bearer {token}"}
    data = {
        "authorName": author[:200],
        "text": text[:50000],
        "createdAt": created_at.strftime("%Y-%m-%dT%H:%M:%S"),
        "human": "true" if is_human else "false",
        "animal": "true" if is_animal else "false",
    }
    if tags:
        data["tags"] = tags[:500]

    files = [
        ("images", (f"bild_{i + 1}.jpg", img, "image/jpeg"))
        for i, img in enumerate(images)
    ]
    if not files:
        # requests benötigt mindestens einen file-Eintrag für multipart/form-data
        files = [("_mp", ("", b""))]

    resp = common.http_post_with_retry(
        f"{cili_url}/api/testimonials",
        headers=headers,
        data=data,
        files=files,
        timeout=30,
    )
    return resp.json()


# ── Verarbeitung ──────────────────────────────────────────────────────────────

def _resolve_message_content(message, album_map: dict, processed_group_ids: set):
    """Liefert (text, media_msgs) für eine Nachricht bzw. ihr Album.

    Bei einem bereits verarbeiteten Album-Mitglied wird (None, None) zurückgegeben."""
    if not message.grouped_id:
        return (message.text or "").strip(), [message]
    if message.grouped_id in processed_group_ids:
        return None, None  # zweites/weiteres Album-Mitglied — beim ersten erledigt
    processed_group_ids.add(message.grouped_id)
    group_msgs = album_map[message.grouped_id]
    text = next((m.text for m in group_msgs if m.text), "").strip()
    return text, group_msgs


def _classify(cfg: TestimonialRunConfig, sender_name: str, text: str) -> dict | None:
    """KI-Klassifikation samt Nachbearbeitung; None bei Fehler (übersprungen)."""
    try:
        result = classify_with_ai(cfg.ai_url, cfg.ai_model, cfg.ai_timeout, cfg.ai_num_ctx,
                                   cfg.ai_num_predict, cfg.ai_retry_backoff_seconds,
                                   sender_name, text)
        return post_process_result(result)
    except Exception as exc:
        print(f"  KI-Fehler: {exc} — übersprungen\n")
        return None


async def _download_images(client, media_msgs: list) -> list:
    """Lädt alle Foto-Anhänge der Nachricht(en) als Bytes herunter."""
    images = []
    for m in media_msgs:
        if not m.photo:
            continue
        try:
            buf = BytesIO()
            await client.download_media(m.media, file=buf)
            images.append(buf.getvalue())
            print(f"  Bild heruntergeladen ({len(images[-1])} Bytes)")
        except Exception as exc:
            print(f"  Bild-Download fehlgeschlagen: {exc}")
    return images


def _upload_attachments(cfg: TestimonialRunConfig, testimonial_id: int, av_paths: list) -> None:
    """Lädt Video-/Audio-Anhänge nach erfolgreicher Testimonial-Anlage hoch.
    Ein fehlgeschlagener Einzel-Upload bricht den Lauf nicht ab."""
    for path in av_paths:
        try:
            upload_token = get_cili_token(cfg)  # frisches Token — Upload kann dauern
            upload_file(path, token=upload_token, cili_url=cfg.cili_url, testimonial_id=testimonial_id)
            print(f"  ✓ Anhang hochgeladen: {path.name}")
        except Exception as exc:
            print(f"  Video-/Audio-Upload fehlgeschlagen ({path.name}): {exc} — übersprungen")


def _post_one(cfg: TestimonialRunConfig, token: str, author: str, result: dict, text: str,
              msg_utc: datetime, images: list, av_paths: list) -> str:
    """Postet einen Erfahrungsbericht an CILI. Liefert 'imported' oder 'failed'."""
    try:
        created = post_testimonial(
            cili_url=cfg.cili_url,
            token=token,
            author=author,
            text=(result.get("text") or text).strip(),
            tags=result.get("tags", ""),
            created_at=msg_utc,
            images=images,
            is_human=cfg.is_human,
            is_animal=cfg.is_animal,
        )
        print(f"  ✓ Importiert als Erfahrungsbericht ID {created['id']}")
    except requests.HTTPError as exc:
        print(f"  CILI-Fehler {exc.response.status_code}: {exc.response.text[:200]} — übersprungen\n")
        return "failed"
    except Exception as exc:
        print(f"  CILI-Fehler: {exc} — übersprungen\n")
        return "failed"

    _upload_attachments(cfg, created["id"], av_paths)
    print()
    return "imported"


async def _process_message(cfg: TestimonialRunConfig, client, message, album_map: dict,
                           processed_group_ids: set, token: str) -> str:
    """Verarbeitet eine Nachricht (bzw. ein Album) Ende-zu-Ende.

    Rückgabe-Status: 'imported', 'skipped', 'failed' oder 'album_dup'."""
    text, media_msgs = _resolve_message_content(message, album_map, processed_group_ids)
    if text is None:
        return "album_dup"

    av_msgs = [m for m in media_msgs if common.classify_media(m) in ("video", "audio")]
    tmp_dir: Path | None = None
    av_downloaded: dict = {}  # msg.id -> Path, vermeidet Doppel-Download

    try:
        if len(text) < 50 and av_msgs:
            tmp_dir = Path(tempfile.mkdtemp(prefix="cili_tg_"))
            first_path = await common.download_media_to_dir(client, av_msgs[0], tmp_dir)
            if first_path:
                av_downloaded[av_msgs[0].id] = first_path
                transcript = transcribe_for_classification(
                    first_path, cfg.whisper_model, cfg.whisper_device,
                    cfg.whisper_compute_type, cfg.whisper_lang)
                if transcript:
                    if len(transcript) > cfg.max_transcript_chars:
                        print(f"  Transkript gekürzt ({len(transcript)} → {cfg.max_transcript_chars} "
                              f"Zeichen, KI-Kontextfenster)")
                        transcript = transcript[:cfg.max_transcript_chars]
                    text = f"{text}\n\n{transcript}".strip() if text else transcript

        if len(text) < 50:
            return "skipped"

        msg_utc = common.to_utc_naive(message.date)

        if common.is_webinar(text):
            print(f"[{msg_utc.strftime('%d.%m.%Y %H:%M')}] Webinar-Nachricht erkannt — übersprungen "
                  f"(siehe telegram_import_webinare.py)")
            return "skipped"

        sender_name = await common.resolve_sender_name(message)
        print(f"[{msg_utc.strftime('%d.%m.%Y %H:%M')}] {sender_name}: "
              f"{text[:100].replace(chr(10), ' ')}…")

        result = _classify(cfg, sender_name, text)
        if result is None:
            return "skipped"

        confidence = float(result.get("confidence", 0))
        if not result.get("is_testimonial") or confidence < cfg.min_confidence:
            print(f"  → Kein Erfahrungsbericht (confidence={confidence:.2f})\n")
            return "skipped"

        print(f"  → Erfahrungsbericht erkannt (confidence={confidence:.2f})")

        final_text = (result.get("text") or text).strip()
        if len(final_text.split()) < 10:
            print(f"  → Zu kurz nach KI-Verarbeitung ({final_text!r}) — übersprungen\n")
            return "skipped"

        if av_msgs and tmp_dir is None:
            tmp_dir = Path(tempfile.mkdtemp(prefix="cili_tg_"))
        for m in av_msgs:
            if m.id in av_downloaded:
                continue
            p = await common.download_media_to_dir(client, m, tmp_dir)
            if p:
                av_downloaded[m.id] = p

        images = await _download_images(client, media_msgs)
        author = (result.get("author_name") or sender_name).strip() or sender_name
        return _post_one(cfg, token, author, result, text, msg_utc, images, list(av_downloaded.values()))
    finally:
        if tmp_dir is not None:
            shutil.rmtree(tmp_dir, ignore_errors=True)


async def _run_async(cfg: TestimonialRunConfig) -> None:
    client = common.build_client(cfg.session_file, cfg.tg_api_id, cfg.tg_api_hash)
    async with client:
        await client.start(phone=cfg.tg_phone or (lambda: input("Telefonnummer: ")))

        group = await common.resolve_group(client, cfg.tg_group)
        print(f"Gruppe: {getattr(group, 'title', cfg.tg_group)}")

        token = get_cili_token(cfg)
        print("CILI-Login erfolgreich.")

        cutoff = common.resolve_cutoff(cfg.state_file, cfg.manual_cutoff)
        print()

        new_messages = await common.fetch_new_messages(client, group, cutoff)
        if not new_messages:
            print("Keine neuen Nachrichten seit dem letzten Import.")
            return

        print(f"{len(new_messages)} neue Nachricht(en) gefunden — verarbeite älteste zuerst.\n")

        album_map = common.group_albums(new_messages)
        processed_group_ids: set = set()
        counts = {"imported": 0, "skipped": 0, "failed": 0}
        run_started = time.monotonic()
        processed = 0
        limit_hit = None

        # Umkehren: älteste zuerst → chronologische Reihenfolge in CILI
        for message in reversed(new_messages):
            if processed >= cfg.max_messages_per_run:
                limit_hit = f"{cfg.max_messages_per_run} Nachrichten"
                break
            if (time.monotonic() - run_started) / 60 >= cfg.max_runtime_minutes:
                limit_hit = f"{cfg.max_runtime_minutes:.0f} Minuten Laufzeit"
                break

            status = await _process_message(cfg, client, message, album_map, processed_group_ids, token)
            processed += 1
            if status in counts:
                counts[status] += 1
            # Nach jeder Nachricht speichern (nicht erst am Lauf-Ende): große Anhänge können
            # den Prozess-Timeout überschreiten und den Import hart abbrechen — ohne
            # Zwischenstand würde der nächste Lauf bereits Verarbeitetes erneut anfassen.
            common.save_state_date(cfg.state_file, common.to_utc_naive(message.date))

        if limit_hit:
            print(f"Limit erreicht ({limit_hit}) — {len(new_messages) - processed} "
                  f"verbleibende Nachricht(en) folgen im nächsten Lauf.\n")

        print(f"Fertig: {counts['imported']} importiert, {counts['skipped']} übersprungen, "
              f"{counts['failed']} fehlgeschlagen.")


def run(cfg: TestimonialRunConfig) -> None:
    asyncio.run(_run_async(cfg))
