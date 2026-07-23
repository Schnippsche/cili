# CILI – Benutzerhandbuch

CILI ist ein Medienverwaltungssystem exklusiv für Cili-Member für Dateien aller Art: Videos, Bilder, Dokumente, Audiodateien und mehr. Dateien werden in Ordnern organisiert, lassen sich thematisch in Sammlungen zusammenfassen und können mit externen Personen über Freigabe-Links geteilt werden — ohne dass diese einen Account benötigen.

---

## Inhaltsverzeichnis

1. [Anmeldung](#1-anmeldung)
2. [Oberfläche & Navigation](#2-oberfläche--navigation)
3. [Ordner & Dateien](#3-ordner--dateien)
4. [Dateien betrachten & abspielen](#4-dateien-betrachten--abspielen)
5. [Untertitel](#5-untertitel)
6. [Suche](#6-suche)
7. [Sammlungen](#7-sammlungen)
8. [Erfahrungsberichte](#8-erfahrungsberichte)
9. [Freigabe-Links](#9-freigabe-links)
10. [Favoriten](#10-favoriten)
11. [Papierkorb](#11-papierkorb)

---

## 1. Anmeldung

Öffne CILI im Browser. Du wirst zur Anmeldeseite weitergeleitet, sofern du noch nicht eingeloggt bist.

Gib deinen **Benutzernamen** und dein **Passwort** ein und klicke auf **Anmelden**. Nach erfolgreicher Anmeldung landest du auf der Startseite (Dashboard).

> Zugangsdaten erhältst du von deinem Administrator.

---

## 2. Oberfläche & Navigation

### Seitenleiste

Links befindet sich die Navigationsleiste mit den Hauptbereichen:

| Bereich | Beschreibung |
|---|---|
| **Dashboard** | Startseite mit allen Root-Ordnern |
| **Sammlungen** | Thematische Datei-Gruppierungen |
| **Erfahrungsberichte** | Verwaltung von Erfahrungsberichten |
| **Suche** | Volltext-Suche über alle Inhalte |

Die Seitenleiste lässt sich über das Menü-Symbol in der oberen Leiste ein- und ausblenden. Auf kleinen Bildschirmen klappt sie automatisch ein.

### Obere Leiste (Top-Bar)

Rechts oben findest du dein Benutzerprofil mit der Möglichkeit, das **Passwort zu ändern** oder dich **abzumelden**.

### Breadcrumb-Navigation

Beim Navigieren durch Ordner zeigt die Breadcrumb-Leiste deinen aktuellen Pfad. Klicke auf einen Ordnernamen in der Breadcrumb-Leiste, um direkt dorthin zurückzuspringen.

---

## 3. Ordner & Dateien

### Ordner-Übersicht

Das Dashboard zeigt alle Ordner der obersten Ebene. Klicke auf einen Ordner, um ihn zu öffnen und seinen Inhalt (Unterordner und Dateien) zu sehen.

### Ansichtsmodus wechseln

Du kannst zwischen zwei Ansichten wählen:

- **Kachelansicht** – Vorschaubilder und Dateinamen nebeneinander
- **Listenansicht** – kompakte Darstellung mit mehr Details

Die gewählte Ansicht wird gespeichert und bleibt beim nächsten Besuch erhalten.

### Dateien hochladen

Ziehe Dateien per Drag & Drop in den Ordner-Bereich, oder klicke auf die Upload-Schaltfläche. Mehrere Dateien können gleichzeitig hochgeladen werden.

> Maximale Dateigröße: 50 MB pro Datei (bzw. gemäß Konfiguration deines Administrators).

Nach dem Upload werden Thumbnails (Vorschaubilder) automatisch im Hintergrund generiert.

#### Verarbeitung von Audio- und Videodateien

Jede hochgeladene Audio- oder Videodatei durchläuft nach dem Upload automatisch zwei Schritte im Hintergrund:

1. **Konvertierung** – die Datei wird in ein weboptimiertes Format umgewandelt
2. **Transkription** – der gesprochene Inhalt wird automatisch erkannt und als Untertitel gespeichert

Dieser Prozess kann je nach Länge und Komplexität der Datei **einige Minuten** in Anspruch nehmen. Während dieser Zeit ist die Datei bereits sichtbar und abspielbar, die Untertitel erscheinen jedoch erst nach Abschluss der Transkription.

### Direkt-Upload (Video per URL importieren)

Mit dem **Direkt-Upload** kannst du Videos direkt von externen Plattformen in einen Ordner importieren — ohne die Datei zuerst herunterladen und dann wieder hochladen zu müssen.

**So funktioniert es:**

1. Öffne einen Ordner und klicke auf die Schaltfläche **Direkt-Upload**
2. Füge die URL des Videos ein (z. B. von Loom, YouTube, Vimeo oder einer anderen unterstützten Plattform)
3. Falls das Video passwortgeschützt ist, trage das Passwort im entsprechenden Feld ein
4. Klicke auf **Download starten**

Der Server lädt das Video herunter und legt es automatisch im aktuellen Ordner ab. Anschließend wird es wie jede andere Videodatei konvertiert und transkribiert (siehe oben).

> Der Direkt-Upload steht nur zur Verfügung wenn du Upload-Berechtigung für den jeweiligen Ordner hast.

### Datei-Aktionen

Klicke auf das **Drei-Punkte-Menü** einer Datei oder eines Ordners, um folgende Aktionen aufzurufen (abhängig von deinen Berechtigungen):

- **Öffnen / Abspielen** – Datei betrachten
- **Herunterladen** – Datei auf das eigene Gerät speichern
- **Metadaten bearbeiten** – Titel und Beschreibung anpassen
- **In Sammlung aufnehmen** – Datei einer Sammlung hinzufügen
- **Verschieben** – Datei in einen anderen Ordner verschieben
- **Freigabe-Link** – öffentlichen Link erstellen, kopieren oder widerrufen (Details siehe [Kapitel 9](#9-freigabe-links))
- **Untertitel** – Untertiteldateien (.srt) manuell hochladen oder vorhandene Untertitel verwalten; bei Audio/Video werden Untertitel nach der Transkription automatisch hinzugefügt
- **KI-Zusammenfassung** – automatisch generierte Zusammenfassung des Inhalts auf Basis der Untertitel anzeigen; steht nur für Audio- und Videodateien mit vorhandenen Untertiteln zur Verfügung
- **Löschen** – Datei entfernen (mit Bestätigung)

### Ordner verwalten

Über das Menü eines Ordners kannst du ihn **umbenennen**, **Unterordner erstellen** oder ihn **löschen** (dabei werden alle enthaltenen Dateien in den Papierkorb verschoben).

---

## 4. Dateien betrachten & abspielen

Ein Klick auf eine Datei öffnet sie direkt im Browser — ohne Download. Je nach Dateityp stehen unterschiedliche Funktionen zur Verfügung:

### Video & Audio

- Eingebetteter Player mit Play/Pause, Lautstärke und Vollbild
- **Mehrsprachige Untertitel**: Wenn Untertitel vorhanden sind, können sie im Player ein- und ausgeschaltet sowie die Sprache gewählt werden
- **Zeitstempel-Sprung**: Bei Suchergebnissen mit Untertitel-Treffern springt ein Klick auf den Treffer direkt zur passenden Stelle im Video

### Bilder

- Vollbild-Ansicht (Lightbox) mit Navigation zum vorherigen und nächsten Bild im selben Ordner

### PDF & Dokumente

- Eingebettete Vorschau direkt im Browser (kein externer Viewer nötig)
- Unterstützte Formate: PDF, Word (.docx), Excel, OpenDocument

### Textdateien

- Eingebetteter Text-Editor zur direkten Ansicht und Bearbeitung

### Nicht unterstützte Formate

Für alle anderen Dateitypen steht ein Download-Button bereit.

### Videoausschnitt (Clip) erstellen

Aus einem Video lässt sich ein kurzer Ausschnitt als eigenständige neue Datei extrahieren — etwa um nur eine relevante Szene weiterzugeben, ohne das gesamte Video teilen zu müssen.

1. Öffne das Video im Player und klicke auf **Ausschnitt erstellen**
2. Trage Start- und Endzeitpunkt in die beiden Eingabefelder ein (Format `m:ss`, bei längeren Aufnahmen `h:mm:ss`)
3. Klicke auf **Vorschau**, um den markierten Bereich am Stück abzuspielen (das Video pausiert automatisch am Endpunkt)
4. Klicke auf **Clip erstellen** — der Dateiname wird automatisch aus dem Titel des Mediums und der gewählten Zeitspanne gebildet

Der neue Ausschnitt wird im Hintergrund erzeugt und erscheint nach kurzer Zeit als eigenständige Datei im selben Ordner — unabhängig vom Originalvideo, mit eigenem Vorschaubild und eigenen Metadaten.

> Der Button **Ausschnitt erstellen** steht nur zur Verfügung, wenn du Upload-Berechtigung für den jeweiligen Ordner hast.

---

## 5. Untertitel

Untertitel ermöglichen es, den gesprochenen Inhalt von Audio- und Videodateien als Text mitzulesen, in anderen Sprachen anzuzeigen und im Volltext zu durchsuchen. Das Untertitel-Panel öffnet sich über den Menüeintrag **Untertitel** im Drei-Punkte-Menü einer Audio- oder Videodatei.

---

### 5.1 Untertitel-Formate: SRT, VTT und Text

Beim Herunterladen stehen drei Formate zur Wahl:

| Format | Beschreibung | Verwendung |
|---|---|---|
| **SRT** (SubRip) | Weit verbreitetes Textformat mit Zeitstempeln | Videoplayer, Schnittprogramme, Übersetzungstools |
| **VTT** (WebVTT) | Modernes Webformat, ähnlich wie SRT | Browser-Player, Streaming-Dienste |
| **Text** | Reiner Fließtext ohne Zeitstempel | Nachlesen, Suche, Weiterverarbeitung |

SRT und VTT sind strukturell sehr ähnlich — beide enthalten nummerierten Textblöcke mit Start- und Endzeit. Für die meisten Zwecke (z. B. Einspielen in einen Videoplayer oder Weitergabe) ist **SRT** die universellste Wahl.

---

### 5.2 Automatische Transkription

Nach dem Hochladen einer Audio- oder Videodatei erstellt CILI automatisch einen Untertitel-Track durch Spracherkennung. Dieser Prozess läuft im Hintergrund und kann je nach Länge der Datei einige Minuten dauern. Der Untertitel erscheint im Panel sobald die Transkription abgeschlossen ist.

**Neu transkribieren:** Wenn die automatische Erkennung fehlerhaft war oder die Datei ausgetauscht wurde, kann die Transkription neu gestartet werden. Klicke dazu im Untertitel-Panel auf **Neu transkribieren**.

> Beim Neu-Transkribieren wird der bisherige automatisch erstellte Untertitel überschrieben. Manuell hochgeladene Untertitel (z. B. in anderen Sprachen) bleiben dabei erhalten.

---

### 5.3 Untertitel manuell hochladen

Eigene Untertiteldateien lassen sich im Panel ergänzen — etwa eine professionell erstellte Übersetzung oder eine korrigierte Version.

1. Wähle im Dropdown die **Sprache** des Untertitels (oder „Andere…" für einen freien Sprachcode wie `nl` oder `ru`)
2. Klicke auf **Datei wählen…** und wähle eine `.srt`- oder `.vtt`-Datei
3. Klicke auf **Hochladen**

Pro Sprache kann jeweils ein Untertitel-Track vorhanden sein. Wenn für die gewählte Sprache bereits ein Track existiert, erscheint eine Fehlermeldung — der bestehende Track muss zuerst gelöscht werden.

---

### 5.4 Untertitel übersetzen

Vorhandene Untertitel lassen sich automatisch in eine andere Sprache übersetzen.

1. Wähle unter **Von** die Ausgangssprache (nur bereits vorhandene Tracks stehen zur Wahl)
2. Wähle unter **Nach** die Zielsprache
3. Klicke auf **Übersetzen**

Die Übersetzung wird im Hintergrund erstellt. Der Fortschritt wird im Panel angezeigt. Sobald die Übersetzung abgeschlossen ist, erscheint der neue Track automatisch in der Liste.

> Wenn für die Zielsprache bereits ein Untertitel existiert, erscheint ein Bestätigungsdialog — der bestehende Track kann auf Wunsch überschrieben werden.

---

### 5.5 Untertitel herunterladen

Neben jedem Untertitel-Track befindet sich ein **Download**-Button mit einem Auswahlmenü für das gewünschte Format (SRT, VTT oder Text). Die Datei wird direkt im Browser heruntergeladen.

---

### 5.6 Untertitel löschen

Klicke neben dem gewünschten Track auf **Löschen** und bestätige die Nachfrage. Der Track wird sofort entfernt und steht im Player nicht mehr zur Verfügung.

---

## 6. Suche

CILI bietet zwei unterschiedliche Suchmöglichkeiten:

| | Globale Suche | Suche in Erfahrungsberichten |
|---|---|---|
| **Wo** | Eigene Seite (Lupe in Seitenleiste) | Direkt auf der Erfahrungsberichte-Seite |
| **Durchsucht** | Dateien, Untertitel **und** Erfahrungsberichte | Nur Erfahrungsberichte |
| **Ergebnisse** | Dateien + Erfahrungsberichte getrennt, jeweils seitenweise | Nur Erfahrungsberichte, seitenweise |
| **Einsatz** | Wenn unklar ist, wo etwas ist | Gezieltes Stöbern in Erfahrungsberichten |

---

### 6.1 Globale Suche

Die globale Suche erreichst du über das **Lupe-Symbol** in der Seitenleiste.

Gib einen oder mehrere Suchbegriffe ein und drücke Enter. Bei mehreren Begriffen müssen alle im Ergebnis vorkommen.

**Was wird durchsucht:**

- **Dateinamen** und Metadaten (Titel, Beschreibung, Tags)
- **Untertitel** von Videos und Audios
- **Erfahrungsberichte** (Autor, Text, Tags)

**Ergebnisse:**

Die Treffer sind in zwei Kategorien aufgeteilt:

- **Dateien** – mit Vorschaubild, Dateiname und Ordnerpfad
- **Erfahrungsberichte** – mit Textausschnitt und Autor; ein Symbol (👤 Mensch / 🐾 Tier) zeigt sofort, um welche Art von Bericht es sich handelt

Bei Untertitel-Treffern wird der gefundene Textausschnitt angezeigt. Ein Klick öffnet das Video und springt direkt zur entsprechenden Stelle im Video.

Neben jedem Treffer befindet sich ein **Lesezeichen-Symbol**, mit dem sich der Treffer direkt einer Sammlung hinzufügen lässt. Über das Symbol **Alle Treffer zur Sammlung hinzufügen** oberhalb der Trefferliste lässt sich das komplette Suchergebnis (Dateien und Erfahrungsberichte zusammen) auf einmal einer Sammlung hinzufügen (Details siehe [Suchtreffer zu einer Sammlung hinzufügen](#suchtreffer-zu-einer-sammlung-hinzufügen)).

Liefert eine Suche mehr Treffer, als auf einer Seite Platz haben, erscheint darunter eine **Seitennavigation** — für Dateien und für Erfahrungsberichte jeweils getrennt, da beide Kategorien unabhängig voneinander blättern.

---

### 6.2 Suche innerhalb der Erfahrungsberichte

Auf der Seite **Erfahrungsberichte** befindet sich ein eigenes Suchfeld direkt über der Liste. Diese Suche durchsucht **ausschließlich Erfahrungsberichte** — keine Dateien, keine Untertitel.

Die Ergebnisse werden seitenweise angezeigt und aktualisieren sich während der Eingabe automatisch.

Sobald ein Suchbegriff eingegeben ist und Treffer vorliegen, erscheint oberhalb der Liste die Schaltfläche **Alle in Sammlung**, mit der sich sämtliche aktuell gefundenen Erfahrungsberichte auf einmal einer Sammlung hinzufügen lassen (siehe [Suchtreffer zu einer Sammlung hinzufügen](#suchtreffer-zu-einer-sammlung-hinzufügen)).

> **Wann welche Suche nutzen?**
> Wenn du weißt, dass du einen Erfahrungsbericht suchst, nutze das Suchfeld auf der Erfahrungsberichte-Seite. Wenn du nicht sicher bist, wo sich ein Inhalt befindet — oder gleichzeitig in Dateien und Erfahrungsberichten suchen möchtest — nutze die globale Suche.

---

### Suchtipps (für beide Suchen)

- Unvollständige Begriffe funktionieren: `Bespre` findet auch `Besprechung`
- Groß-/Kleinschreibung wird ignoriert

---

## 7. Sammlungen

Sammlungen ermöglichen es, Dateien aus verschiedenen Ordnern thematisch zu gruppieren — ohne sie zu verschieben. Zusätzlich können Erfahrungsberichte hinzugefügt werden. Über einen Freigabe-Link lässt sich eine Sammlung mit Externen teilen, ohne dass diese einen Login benötigen. Nur du siehst deine eigenen Sammlungen.

### Neue Sammlung anlegen

1. Navigiere zu **Sammlungen** in der Seitenleiste
2. Klicke auf **Neue Sammlung**
3. Gib einen Namen ein — Namen müssen eindeutig sein, ein bereits verwendeter Name wird beim Anlegen abgewiesen
4. Bestätige mit **Anlegen**

> Jede Sammlung muss einen eindeutigen Namen haben. Bereits verwendete Namen werden direkt im Eingabefeld hervorgehoben.

Wenn du berechtigt bist, Vorlagen zu erstellen (siehe unten), erscheint im Dialog zusätzlich die Option **Als Vorlage markieren**.

### Sammlung aus Vorlage erstellen

Administratoren sowie Benutzer mit dem Recht **Vorlagen erstellen** können Vorlagen bereitstellen — Sammlungen mit vordefinierter Struktur und Inhalten, die als Ausgangspunkt für eigene Sammlungen dienen.

> Das Recht **Vorlagen erstellen** wird von einem Administrator über die Gruppenrechte vergeben (Adminbereich → Gruppen → Rechte → Abschnitt „Sammlungen"). Standardmäßig dürfen nur Administratoren Vorlagen erstellen.

Wenn Vorlagen vorhanden sind, erscheinen sie im Dialog **Neue Sammlung** unter dem Trennstrich. So verwendest du eine Vorlage:

1. Klicke im Dialog auf eine Vorlage in der Liste — der Name der Vorlage wird automatisch ins Namensfeld übernommen
2. Passe den Namen bei Bedarf an
3. Bestätige mit **Anlegen**

Die neue Sammlung enthält alle Einträge (Dateien und Erfahrungsberichte) der gewählten Vorlage. Vorlage und neue Sammlung sind anschließend voneinander unabhängig.

### Sammlung kopieren

Eine bestehende Sammlung lässt sich mit einem Klick duplizieren — inklusive aller enthaltenen Einträge.

1. Klicke in der Sammlungsübersicht auf das **Kopieren-Symbol** der gewünschten Sammlung
2. Der Dialog schlägt automatisch den Namen `„Original (Kopie)"` vor — passe ihn bei Bedarf an
3. Bestätige mit **Kopieren**

Die Kopie ist sofort verfügbar und vollständig unabhängig vom Original. Auch hier muss der Name eindeutig sein.

### Sammlung umbenennen

Klicke auf das **Bearbeiten-Symbol** (Stift) einer Sammlung, trage den neuen Namen ein und bestätige mit **Speichern**. Der Name muss eindeutig sein.

### Dateien zu einer Sammlung hinzufügen

1. Öffne einen Ordner und klicke das Menü einer Datei
2. Wähle **In Sammlung aufnehmen**
3. Wähle die gewünschte Sammlung aus der Liste

Alternativ direkt in der Sammlung über **Ressource hinzufügen**.

### Erfahrungsberichte zu einer Sammlung hinzufügen

Öffne einen Erfahrungsbericht und klicke auf **Zu Sammlung hinzufügen**. Wähle anschließend die gewünschte Sammlung aus der Liste.

### Suchtreffer zu einer Sammlung hinzufügen

Sowohl in der [globalen Suche](#61-globale-suche) als auch in der [Suche innerhalb der Erfahrungsberichte](#62-suche-innerhalb-der-erfahrungsberichte) lassen sich Treffer direkt einer Sammlung hinzufügen, ohne die Datei oder den Erfahrungsbericht einzeln zu öffnen.

**Einzelnen Treffer hinzufügen:**

Klicke bei einem Suchtreffer auf das **Lesezeichen-Symbol** und wähle im Dialog die gewünschte Sammlung aus — oder lege direkt eine neue Sammlung an.

**Alle Treffer auf einmal hinzufügen:**

- In der globalen Suche: Symbol **Alle Treffer zur Sammlung hinzufügen** oberhalb der Trefferliste
- In der Suche innerhalb der Erfahrungsberichte: Schaltfläche **Alle in Sammlung** oberhalb der Liste

Beide fügen **alle aktuell angezeigten Treffer** der laufenden Suche gemeinsam einer Sammlung hinzu. In der globalen Suche zählen dazu sowohl Datei- als auch Erfahrungsbericht-Treffer.

> Wie beim einzelnen Hinzufügen kann im Dialog entweder eine bestehende Sammlung gewählt oder direkt eine neue Sammlung angelegt werden.

### Bericht aus einer Sammlung erstellen

Besteht eine Sammlung **ausschließlich aus Erfahrungsberichten** (keine sonstigen Dateien), erscheint oberhalb der Liste die Schaltfläche **Bericht generieren**. Sie öffnet eine druckfertige Vorschau mit allen Erfahrungsberichten der Sammlung inklusive vorhandener Bilder.

1. Öffne die Sammlung
2. Klicke auf **Bericht generieren**
3. In der Vorschau kannst du den Bericht über **Drucken** direkt ausdrucken oder als PDF speichern

Der Bericht trägt den Namen der Sammlung als Überschrift.

### Sammlung freigeben

Über das **Teilen-Symbol** in der Sammlungsübersicht öffnest du den Freigabe-Dialog (das Symbol erscheint nur, wenn die Sammlung mindestens einen Eintrag enthält):

1. Klicke auf **Link erstellen**
2. Kopiere den generierten Link über das Kopier-Symbol
3. Sende den Link an die gewünschten Personen

Externe können die Sammlung (Dateien und Erfahrungsberichte) ohne Login aufrufen. Der Link ist standardmäßig **90 Tage** gültig.

Den Link kannst du jederzeit **widerrufen** (sofort ungültig) oder **erneuern** (neues Ablaufdatum).

---

## 8. Erfahrungsberichte

Erfahrungsberichte sind strukturierte Einträge mit Autorenname, Text und optionalen Tags sowie Bildern. Jeder Bericht ist außerdem eindeutig als **Mensch**- oder **Tier**-Erfahrungsbericht gekennzeichnet und wird entsprechend mit einem Symbol (👤 bzw. 🐾) versehen — sowohl in der Übersicht als auch in den Suchergebnissen.

### Übersicht & Suche

Unter **Erfahrungsberichte** in der Seitenleiste siehst du alle vorhandenen Einträge. Über die Auswahl **Beide / Mensch / Tier** oberhalb der Liste lässt sich die Ansicht auf eine der beiden Kategorien einschränken. Über das **Suchfeld** darunter kannst du direkt nach Autor, Text oder Tags filtern — die Liste aktualisiert sich dabei automatisch während der Eingabe. Suchbegriff und Mensch/Tier-Auswahl wirken dabei **zusammen**: Ist z. B. „Mensch" ausgewählt, durchsucht die Eingabe im Suchfeld auch nur die Mensch-Erfahrungsberichte. Diese Suche durchsucht **nur Erfahrungsberichte**. Für eine Suche über Dateien und Erfahrungsberichte gleichzeitig nutze die [Globale Suche](#61-globale-suche).

Die Trefferliste ist bei vielen Einträgen seitenweise blätterbar; die Seitennavigation erscheint unterhalb der Liste.

### Erfahrungsbericht anlegen

Klicke auf **Neu** und fülle die Felder aus:

- **Autorenname** – Name der Person
- **Mensch / Tier** – Pflichtauswahl direkt unter dem Namensfeld, legt fest, ob es sich um einen Erfahrungsbericht zu einem Menschen oder einem Tier handelt
- **Text** – Der eigentliche Bericht
- **Tags** – Kommagetrennte Schlagwörter zur Kategorisierung
- **Bilder** – Fotos können direkt angehängt werden

### Erfahrungsberichte in Sammlungen

Erfahrungsberichte lassen sich zu Sammlungen hinzufügen und erscheinen dann auch auf der öffentlichen Sammlungsseite, wenn ein Freigabe-Link geteilt wird.

---

## 9. Freigabe-Links

Mit Freigabe-Links können einzelne Dateien oder ganze Sammlungen mit Personen geteilt werden, die keinen CILI-Account haben.

### Freigabe-Link für eine Datei erstellen

1. Öffne einen Ordner und klicke das Menü der gewünschten Datei
2. Wähle **Freigabe** (oder öffne das Freigabe-Panel)
3. Klicke auf **Link generieren**
4. Kopiere den Link über das Kopier-Symbol

### Freigabe-Link für eine Sammlung erstellen

Siehe [Sammlung freigeben](#sammlung-freigeben) im Abschnitt Sammlungen.

### Link-Verwaltung

| Aktion | Beschreibung |
|---|---|
| **Kopieren** | Link in die Zwischenablage kopieren |
| **Widerrufen** | Link sofort und dauerhaft ungültig machen |
| **Erneuern** | Neuen Token mit frischem Ablaufdatum erstellen |

### Was externe Personen sehen

Wer einen gültigen Freigabe-Link öffnet, sieht:

- **Einzelne Datei**: Direktansicht (Video, Bild, PDF, Audio) mit Download-Option
- **Sammlung**: Alle enthaltenen Dateien als Kacheln sowie Erfahrungsberichte; ein Klick öffnet die Datei in der Vorschau

Bei abgelaufenem oder widerrufenem Link erscheint eine entsprechende Fehlermeldung.

---

## 10. Favoriten

Dateien können als Favorit markiert werden, um schnellen Zugriff zu ermöglichen. Favorisierte Dateien erscheinen im oberen Bereich des Dashboards.

Klicke auf das **Stern-Symbol** einer Datei, um sie zu favorisieren oder die Markierung zu entfernen.

---

## 11. Papierkorb

> Der Papierkorb ist nur für Administratoren zugänglich.

Gelöschte Ordner (und ihr Inhalt) werden zunächst in den Papierkorb verschoben und können von dort wiederhergestellt oder endgültig gelöscht werden.

- **Wiederherstellen**: Ordner wird mit allen Inhalten an seinen ursprünglichen Ort zurückgesetzt
- **Endgültig löschen**: Ordner und alle enthaltenen Dateien werden unwiderruflich entfernt (Bestätigung erforderlich)

---

## Unterstützte Dateiformate

| Kategorie | Formate | Betrachter |
|---|---|---|
| Video | MP4, WebM, MKV u.a. | Eingebetteter Player |
| Audio | MP3, WAV, OGG u.a. | Eingebetteter Player |
| Bilder | JPEG, PNG, WebP, GIF u.a. | Lightbox-Galerie |
| Dokumente | PDF, DOCX, XLSX, ODP u.a. | Eingebetteter Viewer |
| Text / Code | TXT, JSON, Markdown u.a. | Text-Editor |
| Untertitel | SRT u.a. | Wird Video/Audio zugeordnet |
| Sonstige | Alle weiteren | Download |

---

## Häufige Fragen

**Warum sehe ich bestimmte Ordner nicht?**
Der Zugriff auf Ordner wird vom Administrator über Berechtigungen gesteuert. Wende dich an deinen Administrator, wenn du Zugriff auf einen bestimmten Ordner benötigst.

**Wie lange sind Freigabe-Links gültig?**
Standardmäßig 90 Tage. Danach werden sie automatisch ungültig. Du kannst einen Link jederzeit erneuern oder widerrufen.

**Kann ich Dateien in mehreren Sammlungen haben?**
Ja. Eine Datei kann in beliebig vielen Sammlungen enthalten sein, ohne dass sie doppelt gespeichert wird.

**Was sind Vorlagen und wer erstellt sie?**
Vorlagen sind vordefinierte Sammlungen, die als Ausgangspunkt für eigene Sammlungen dienen. Standardmäßig können nur Administratoren Vorlagen erstellen; ein Administrator kann dieses Recht aber auch gezielt einzelnen Gruppen zuweisen (siehe [Sammlung aus Vorlage erstellen](#sammlung-aus-vorlage-erstellen)). Du kannst vorhandene Vorlagen beim Anlegen einer neuen Sammlung als Ausgangspunkt wählen — die neue Sammlung enthält dann dieselben Inhalte wie die Vorlage, ist aber vollständig unabhängig von ihr.

**Kann ich eine Sammlung duplizieren?**
Ja. Klicke in der Sammlungsübersicht auf das Kopieren-Symbol. Du wirst nach einem Namen für die Kopie gefragt. Alle Einträge der Originalsammlung werden übernommen.

**Was passiert, wenn ich einen Ordner lösche?**
Der Ordner wird mit seinem gesamten Inhalt in den Papierkorb verschoben. Ein Administrator kann ihn von dort wiederherstellen oder endgültig löschen.
