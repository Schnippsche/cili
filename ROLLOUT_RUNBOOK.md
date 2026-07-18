# Chunk 12: Testimonial Category Migration Rollout Runbook

**Zielversion**: Singular Testimonial Categories (`Mensch`, `Tier`)  
**Feature Branch**: `feat-testimonial-category-mensch-tier`  
**Kritikalität**: Medium — alle Migrations-Schritte müssen **vor** dem WAR-Deployment erfolgen  
**Rollout-Datum**: TBD (produktion)

---

## Überblick

Dieses Runbook dokumentiert den Deployment-Prozess für die Testimonial-Category-Migration von Plural- zu Singularformen:
- `Menschen` → `Mensch` (Kategorie für persönliche Testimonials)
- `Tiere` → `Tier` (Kategorie für Tierbezogene Testimonials)

**Reihenfolge ist kritisch:** Die Schritte 1–3 müssen **vor** Schritt 4 (WAR-Deployment) abgeschlossen sein, um Datenverlust oder API-Inkonsistenzen zu vermeiden.

---

## 5-Schritt Rollout-Plan

### Schritt 1: Datenbank-Zustand verifizieren

**Ziel:** Sicherstellen, dass die Testimonials-Tabelle nur noch Singularformen enthält.

**Voraussetzung:** Die Migration `migration_testimonial_category_backfill.sql` (Chunk 1) muss bereits auf der Produktionsdatenbank eingespielt worden sein.

```bash
# SSH zur Produktionsumgebung
ssh -i /path/to/key ubuntu@prod.server.com

# Verifizierung: Alle vorhandenen source-Werte auflisten
mysql -u cili -p cili -e "SELECT DISTINCT source FROM testimonials ORDER BY source;"
```

**Erwartet**: Ausgabe sollte **nur** diese Zeilen enthalten (oder leer sein, wenn noch keine Testimonials vorhanden):
```
+--------+
| source |
+--------+
| Mensch |
| Tier   |
+--------+
```

**Fehlerfall:** Falls noch `NULL`, `Menschen` (Plural), oder `Tiere` (Plural) auftauchen:
1. Rückwärtsmigration überprüfen (war sie erfolgreich?)
2. Falls Migration nicht eingespielt: `migration_testimonial_category_backfill.sql` manuell einspielen:
   ```bash
   mysql -u root -p cili < migration_testimonial_category_backfill.sql
   ```
3. Verifikation wiederholen

**Checkpoint:** Nur wenn diese Ausgabe sauber ist, weitermachen zu Schritt 2.

---

### Schritt 2: Telegram-Import Config — TG_SOURCE auf Singular umstellen

**Ziel:** Aktualisierung der `scripts/telegram_import.env` auf die neuen Singular-Kategorien.

**Aktueller Status** (veraltet):
```env
TG_SOURCE=Menschen
```

**Neuer Status** (nach Rollout):
```env
TG_SOURCE=Mensch
```

**Vorgehen** (auf Produktionsserver oder lokal, dann Commit + Push):

1. **Haupkonfiguration (Menschen-Quelle)** — Datei: `scripts/telegram_import.env`
   ```bash
   # Aktuellen Wert überprüfen
   grep "^TG_SOURCE=" scripts/telegram_import.env
   
   # Auf Singular aktualisieren (sed, nano, vim, oder Editor)
   sed -i 's/^TG_SOURCE=Menschen/TG_SOURCE=Mensch/' scripts/telegram_import.env
   
   # Überprüfung
   grep "^TG_SOURCE=" scripts/telegram_import.env
   # Erwartet: TG_SOURCE=Mensch
   ```

2. **Tiere-Konfiguration** — Datei: `scripts/telegram_import_tiere.env`
   ```bash
   # Diese sollte bereits `TG_SOURCE=Tier` enthalten
   grep "^TG_SOURCE=" scripts/telegram_import_tiere.env
   # Erwartet: TG_SOURCE=Tier (kein Änderung nötig)
   ```

**Hinweis:** Falls `telegram_import_tiere.env` noch `TG_SOURCE=Tiere` (Plural) enthält:
```bash
sed -i 's/^TG_SOURCE=Tiere/TG_SOURCE=Tier/' scripts/telegram_import_tiere.env
```

**Checkpoint:** Beide Config-Dateien müssen die Singular-Formen verwenden.

---

### Schritt 3: State-Dateien umbenennen

**Ziel:** State-Dateien an die neuen Singular-Namen anpassen (Dateiname wird aus `TG_SOURCE` abgeleitet).

**Vorher:**
```
scripts/telegram_import_Menschen.state
scripts/telegram_import_Tiere.state
```

**Nachher:**
```
scripts/telegram_import_Mensch.state
scripts/telegram_import_Tier.state
```

**Vorgehen:**
```bash
# Im Root-Verzeichnis der Produktionsumgebung (oder lokal für lokale State-Dateien)

# Umbenennen
mv scripts/telegram_import_Menschen.state scripts/telegram_import_Mensch.state
mv scripts/telegram_import_Tiere.state scripts/telegram_import_Tier.state

# Überprüfung
ls -la scripts/telegram_import_*.state
```

**Checkpoint:** Alte State-Dateien sollten nicht mehr existieren, neue State-Dateien sind vorhanden (oder werden beim nächsten Import-Lauf erstellt).

---

### Schritt 4: Backend + Frontend Deployment

**Ziel:** Neues WAR mit Code-Änderungen deployen.

**Voraussetzung:** Schritte 1–3 müssen **vollständig abgeschlossen** sein!

**Prozess** (Standard Spring Boot WAR-Deployment):
1. Backend + Frontend bauen:
   ```bash
   cd /path/to/cili
   ./mvnw clean package
   ```

2. Alte WAR stoppen (falls läuft):
   ```bash
   systemctl stop cili   # oder manual: kill $PID
   ```

3. Neue WAR deployen:
   ```bash
   cp target/cili-1.0.0.war /opt/cili/cili.war
   
   # Oder manuell:
   systemctl start cili
   ```

4. Logs überprüfen:
   ```bash
   tail -f /var/log/cili/cili.log
   # Erwartet: keine ERROR-Logs, "Started Application in X seconds"
   ```

5. Health-Check:
   ```bash
   curl http://localhost:8080/api/public/testimonials?source=Mensch&size=1
   # Erwartet: HTTP 200, gültige JSON-Antwort
   ```

**Rollback-Plan:** Falls WAR nicht startet oder Fehler auftritt:
- Alte WAR wiederherstellen: `cp /opt/cili/cili.war.backup /opt/cili/cili.war && systemctl restart cili`
- Logs analysieren

**Checkpoint:** WAR läuft, keine Error-Logs, Health-Check antwortet.

---

### Schritt 5: Verifikation (nach Deploy)

**Ziel:** Alle neuen API-Endpunkte verifizieren, dass sie korrekt funktionieren.

**Testfall 1: Interne API (mit Authentication)**
```bash
# Login und Token holen
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}' | jq -r '.accessToken')

# Mensch-Kategorie abfragen
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/testimonials?source=Mensch&size=1"
# Erwartet: HTTP 200, Array mit Testimonials (oder leer, wenn keine Mensch-Testimonials)

# Tier-Kategorie abfragen
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/testimonials?source=Tier&size=1"
# Erwartet: HTTP 200, Array mit Testimonials (oder leer, wenn keine Tier-Testimonials)
```

**Testfall 2: Öffentliche API (ohne Authentication)**
```bash
# Öffentliche Mensch-Testimonials
curl "http://localhost:8080/api/public/testimonials?source=Mensch&size=1"
# Erwartet: HTTP 200, gültiges JSON

# Öffentliche Tier-Testimonials
curl "http://localhost:8080/api/public/testimonials?source=Tier&size=1"
# Erwartet: HTTP 200, gültiges JSON
```

**Testfall 3: Telegram-Import-Skript (Trockentest)**
```bash
# Aktuelle TG_SOURCE überprüfen
cd scripts
python telegram_import.py --help

# Trockentest mit Tiere-Konfiguration (vorsichtig — könnte Live-Daten importieren!)
python telegram_import.py --env scripts/telegram_import_tiere.env --dry-run
# Erwartet: "Would import X testimonials with source='Tier'" (oder keine neuen Einträge)
```

**Testfall 4: Admin-Panel Überprüfung (manuell)**
1. Anmelden als Admin
2. Testimonials-Seite öffnen
3. Filter nach `source` anwenden
4. Beide Werte sollten sichtbar sein: `Mensch`, `Tier`
5. Alte Werte sollten nicht mehr sichtbar sein: `Menschen`, `Tiere`, `NULL`

**Fehlerfall-Handling:**

| Fehler | Symptom | Lösung |
|--------|---------|--------|
| API gibt 404 | Endpunkt nicht erreichbar | WAR nicht gestartet — Logs prüfen |
| API gibt 400 (Bad Request) | `source`-Parameter ungültig | Datenbank-Zustand prüfen (Step 1 wiederholen) |
| API gibt 401/403 | Authentication fehlgeschlagen | Token ungültig oder User hat keine Berechtigung |
| Import-Skript schlägt fehl | `TG_SOURCE=Menschen` noch in Config | Step 2 noch nicht abgeschlossen — Config aktualisieren |

**Checkpoint:** Alle 4 Testfälle bestanden → Deployment erfolgreich!

---

## Rollback-Strategie

Falls Probleme nach dem Deployment auftreten:

1. **Schnell-Rollback (WAR):**
   ```bash
   systemctl stop cili
   cp /opt/cili/cili.war.backup /opt/cili/cili.war
   systemctl start cili
   ```

2. **Datenbank-Rollback (falls nötig):**
   ```bash
   # Rückwärts-Migration (aus Chunk 1)
   mysql -u root -p cili < migration_testimonial_category_backfill.sql.rollback
   ```

3. **Config-Rollback (TG_SOURCE):**
   ```bash
   # In scripts/telegram_import.env zurückändern
   sed -i 's/^TG_SOURCE=Mensch/TG_SOURCE=Menschen/' scripts/telegram_import.env
   ```

---

## Checkliste für den Rollout

- [ ] **1. DB-Verifizierung:** `SELECT DISTINCT source` zeigt nur `Mensch`, `Tier`
- [ ] **2. Config aktualisiert:** `TG_SOURCE=Mensch` in `telegram_import.env`
- [ ] **2b. Tiere-Config OK:** `TG_SOURCE=Tier` in `telegram_import_tiere.env`
- [ ] **3. State-Dateien umbenannt:** `*_Mensch.state` und `*_Tier.state` existieren
- [ ] **4. WAR gebaut & deployed:** `systemctl status cili` → active (running)
- [ ] **5.1 Interne API (Mensch):** HTTP 200, Testimonials oder leeres Array
- [ ] **5.1 Interne API (Tier):** HTTP 200, Testimonials oder leeres Array
- [ ] **5.2 Public API (Mensch):** HTTP 200, valides JSON
- [ ] **5.2 Public API (Tier):** HTTP 200, valides JSON
- [ ] **5.3 Import-Skript:** `--dry-run` zeigt Singular-Source (`Mensch` oder `Tier`)
- [ ] **5.4 Admin-Panel:** Filter zeigt nur Singular-Werte (`Mensch`, `Tier`)
- [ ] **Post-Rollout:** Logs monitoren für 24h, keine ERROR-Einträge

---

## Timing & Maintenance-Fenster

**Geschätzte Dauer:** 30–45 min (mit Tests und Verifikation)  
**Empfohlenes Fenster:** Nachts oder Weekend, außerhalb von Peak-Hours  
**Ausfallzeit:** ~5–10 min (während WAR-Neustart)

---

## Kontakt & Eskalation

- **Frontend/API Issues:** [DevTeam Slack]
- **Database Issues:** [DBA Team Slack]
- **Rollback:** Sofort einleiten — kein Warten auf Approval nötig

---

## Anhang: Wichtige File-Pfade

| Datei | Pfad | Beschreibung |
|-------|------|-------------|
| Migration (Backfill) | `migration_testimonial_category_backfill.sql` (Chunk 1) | Konvertiert Plural zu Singular in DB |
| Config (Mensch) | `scripts/telegram_import.env` | TG_SOURCE=Mensch |
| Config (Tiere) | `scripts/telegram_import_tiere.env` | TG_SOURCE=Tier |
| State (Mensch) | `scripts/telegram_import_Mensch.state` | Nach Step 3 umbenannt |
| State (Tiere) | `scripts/telegram_import_Tier.state` | Nach Step 3 umbenannt |
| WAR | `target/cili-1.0.0.war` | Deployable nach Build |
| Logs | `/var/log/cili/cili.log` | Überprüfen nach Deploy |

---

**Dokumentation erstellt:** 2026-07-18  
**Gültig ab:** Feature-Branch `feat-testimonial-category-mensch-tier` (nach Merge zu `master`)  
**Nächste Review:** Nach erstem Produktions-Rollout
