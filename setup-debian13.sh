#!/bin/bash
# Setup für Debian 13 (Trixie) - OHNE Nginx (externer Reverse Proxy)
# Spring Boot Embedded Tomcat auf Port 8080 | Java 21 (Debian OpenJDK) | MariaDB

set -e

timedatectl set-timezone Europe/Berlin
timedatectl set-ntp true

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║     CILI Production Setup (No Nginx - Ext. Reverse Proxy)      ║"
echo "║     Java 21 (Debian OpenJDK) + MariaDB + Spring Boot Embedded  ║"
echo "║     Debian 13 (Trixie)                                         ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

if [ "$EUID" -ne 0 ]; then
  echo "Fehler: Muss als root ausgeführt werden"
  exit 1
fi

DB_PASSWORD="${1:-CiliByDesign2022!}"
SKIP_WHISPER_MODELS="${2:-no}"   # 'yes' überspringt den ~5 GB Whisper-Modell-Download
SKIP_NLLB_MODEL="${3:-no}"       # 'yes' überspringt den ~2.5 GB NLLB-Modell-Download
HF_TOKEN="${4:-}"      # HuggingFace-Token (optional, erhöht Rate-Limit)
                                 # Token anlegen: https://huggingface.co/settings/tokens

echo "Parameter:"
echo "  - Datenbankpasswort:  (verborgen)"
echo "  - Whisper-Download:   ${SKIP_WHISPER_MODELS} (yes=überspringen)"
echo "  - NLLB-Download:      ${SKIP_NLLB_MODEL} (yes=überspringen)"
echo "  - HuggingFace-Token:  ${HF_TOKEN:+(gesetzt)}" "${HF_TOKEN:-(nicht gesetzt, anonymer Download)}"
echo ""

# ===== 1. SYSTEM UPDATE =====
echo "1. System Update..."
apt-get update -q
apt-get upgrade -y -q
apt-get install -y -q curl wget git htop net-tools jq gnupg ca-certificates apt-transport-https unzip
echo "OK System aktualisiert"

# ===== 2. SSH =====
echo ""
echo "2. SSH installieren und konfigurieren..."
apt-get install -y -q openssh-server

sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config

systemctl enable ssh
systemctl restart ssh
echo "OK SSH installiert und gestartet (Port 22, Root-Login deaktiviert)"

if id -u cili &>/dev/null; then
  echo "INFO Benutzer 'cili' existiert bereits"
else
  useradd -m -s /bin/bash cili
  echo "cili:Cili2030!" | chpasswd
  usermod -aG sudo cili
  echo "OK Benutzer 'cili' angelegt und zur sudo-Gruppe hinzugefügt"
fi

# ===== 3. FIREWALL =====
echo ""
echo "3. Firewall Setup..."
if systemd-detect-virt --container --quiet 2>/dev/null; then
  echo "INFO Container-Umgebung erkannt – UFW übersprungen (Firewall am Host konfigurieren)"
else
  apt-get install -y -q ufw
  ufw --force enable
  ufw default deny incoming
  ufw default allow outgoing
  ufw allow 22/tcp
  ufw allow 8080/tcp
  ufw allow 3306/tcp
  echo "OK Firewall konfiguriert (SSH 22, CILI 8080, MariaDB 3306)"
  echo "HINWEIS: Port 3306 ist offen — in Produktion auf bestimmte IPs einschränken:"
  echo "         ufw delete allow 3306/tcp && ufw allow from <IP> to any port 3306"
fi

# ===== SPEICHER-DIMENSIONIERUNG =====
# Fixe Werte für 8 GB RAM (LXC):
#   MariaDB 512 MB  – Metadaten für ~30 User, working set < 100 MB
#   JVM     2048 MB – Spring Boot; Videos gehen via file-size-threshold auf Temp-Disk
#   Rest    ~5.5 GB – OS, FFmpeg-Transcode, Page Cache für große Video-Uploads
MARIADB_BUFFER_MB=512
JVM_MAX_MB=2048
JVM_MIN_MB=512
TOTAL_MEM_MB=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)
echo ""
echo "Speicher: ${TOTAL_MEM_MB} MB → MariaDB: ${MARIADB_BUFFER_MB} MB | JVM: ${JVM_MAX_MB} MB"

# ===== GPU-ERKENNUNG =====
# LXC-Voraussetzung: NVIDIA-Treiber am Proxmox-Host, GPU-Geräte (/dev/nvidia*) per
# lxc.cgroup2.devices.allow im LXC-Container durchgereicht.
GPU_AVAILABLE=false
if nvidia-smi &>/dev/null 2>&1; then
  GPU_AVAILABLE=true
elif [ -e /dev/nvidia0 ]; then
  GPU_AVAILABLE=true
fi

if [ "$GPU_AVAILABLE" = "true" ]; then
  WHISPER_DEVICE=cuda
  WHISPER_COMPUTE=float16
  NLLB_DEVICE=cuda
  NLLB_COMPUTE=float16
  GPU_NAME=$(nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | head -1 || echo "NVIDIA GPU")
  echo "GPU erkannt: ${GPU_NAME} → Whisper/NLLB laufen auf CUDA (float16)"
else
  WHISPER_DEVICE=cpu
  WHISPER_COMPUTE=int8_float16
  NLLB_DEVICE=cpu
  NLLB_COMPUTE=int8
  echo "HINWEIS: Keine NVIDIA-GPU erkannt → Whisper/NLLB laufen auf CPU"
fi

# ===== 4. JAVA 21 (DEBIAN OPENJDK) =====
echo ""
echo "4. Java 21 installieren (Debian OpenJDK aus Standard-Repo)..."
apt-get install -y -q openjdk-21-jre

JAVA_BIN=$(readlink -f "$(which java)")
JAVA_HOME=$(dirname "$(dirname "${JAVA_BIN}")")
echo "OK Java $(java -version 2>&1 | head -1)"
echo "   JAVA_HOME=${JAVA_HOME}"

# ===== 5. MARIADB =====
echo ""
echo "5. MariaDB installieren (Debian Standard-Repo)..."

apt-get install -y -q mariadb-server

cat > /etc/mysql/mariadb.conf.d/50-cili.cnf <<CNFEOF
[mariadb]
bind-address=0.0.0.0
innodb_buffer_pool_size=${MARIADB_BUFFER_MB}M
innodb_flush_log_at_trx_commit=2
max_connections=25
wait_timeout=28800
max_allowed_packet=1G
character_set_server=utf8mb4
collation_server=utf8mb4_unicode_ci
default_storage_engine=InnoDB
slow_query_log=ON
slow_query_log_file=/var/log/mysql/slow.log
long_query_time=2
CNFEOF

systemctl enable mariadb
systemctl restart mariadb

MARIADB_ROOT_PASSWORD="$(openssl rand -base64 24)"
MARIADB_EXT_PASSWORD="!CiliByDesign2030#"

mysql -u root <<SQLEOF
SET PASSWORD FOR 'root'@'localhost' = PASSWORD('${MARIADB_ROOT_PASSWORD}');
CREATE DATABASE IF NOT EXISTS cili CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'cili'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON cili.* TO 'cili'@'localhost';
CREATE USER IF NOT EXISTS 'cili_ext'@'%' IDENTIFIED BY '${MARIADB_EXT_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON cili.* TO 'cili_ext'@'%';
FLUSH PRIVILEGES;
SQLEOF

MARIADB_VERSION=$(mariadb --version 2>/dev/null | grep -oP '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || echo "unknown")
echo "OK MariaDB ${MARIADB_VERSION} konfiguriert"

# ===== 6. TOOLS =====
echo ""
echo "6. FFmpeg und LibreOffice installieren..."
apt-get update -q
# Ab Debian 13 (Trixie) heißt das Paket libreoffice-nogui (wie Ubuntu 24.04+)
echo "  (LibreOffice ~300 MB, bitte warten...)"
apt-get install -y -q ffmpeg libreoffice-nogui
echo "OK Tools installiert"

# ===== 7. PYTHON + FASTER-WHISPER =====
echo ""
echo "7. Python 3 + faster-whisper installieren..."
apt-get install -y -q python3 python3-venv python3-pip build-essential

mkdir -p /opt/cili
python3 -m venv /opt/cili/whisper-venv

VENV_PIP="/opt/cili/whisper-venv/bin/pip"
$VENV_PIP install -q --upgrade pip setuptools wheel

$VENV_PIP install -q faster-whisper

$VENV_PIP install -q \
    ctranslate2 \
    transformers \
    sentencepiece \
    pymupdf \
    python-docx

# torch: CUDA-Version falls GPU vorhanden, sonst CPU
# CUDA 13.x ist rückwärtskompatibel mit cu128-Wheels
# torch wird nur für die einmalige NLLB-Modellkonvertierung (ct2-transformers-converter) benötigt.
# Danach übernimmt ctranslate2 alle Inferenz — PyTorch ist zur Laufzeit nicht aktiv.
if [ "$GPU_AVAILABLE" = "true" ]; then
  echo "  GPU erkannt — installiere torch mit CUDA-Unterstützung (cu128)..."
  $VENV_PIP install -q torch --index-url https://download.pytorch.org/whl/cu128
  echo "OK torch mit CUDA installiert (nur für NLLB-Konvertierung; ctranslate2 bündelt CUDA-Libs)"
else
  $VENV_PIP install -q torch --index-url https://download.pytorch.org/whl/cpu
  echo "OK torch (CPU) installiert (nur für NLLB-Konvertierung)"
fi

echo "OK faster-whisper, ctranslate2 installiert in /opt/cili/whisper-venv"

# wtpsplit wird von transcribe_worker.py nicht mehr verwendet — kein Install nötig.

# Python-Skripte deployen
SCRIPT_DIR="$(dirname "$(realpath "$0")")"
for SCRIPT in transcribe_worker.py translate_worker.py doc_translate_worker.py; do
  if [ -f "${SCRIPT_DIR}/scripts/${SCRIPT}" ]; then
    cp "${SCRIPT_DIR}/scripts/${SCRIPT}" /opt/cili/
    chmod 755 "/opt/cili/${SCRIPT}"
    echo "OK /opt/cili/${SCRIPT} deployed"
  else
    echo "HINWEIS scripts/${SCRIPT} nicht gefunden — manuell kopieren:"
    echo "        cp scripts/${SCRIPT} /opt/cili/"
  fi
done

# ===== 8. VERZEICHNISSE =====
echo ""
echo "8. CILI-Verzeichnisse erstellen..."
mkdir -p /opt/cili
mkdir -p /opt/cili/data
mkdir -p /opt/cili/log
mkdir -p /opt/cili/backups/{mariadb,data}
mkdir -p /opt/cili/whisper-models
mkdir -p /opt/cili/nllb

chown -R cili:cili /opt/cili
chmod 750 /opt/cili/data
chmod 750 /opt/cili/log
echo "OK Verzeichnisse erstellt"

# ===== 9. KONFIGURATION =====
echo ""
echo "9. Konfigurationsdateien erstellen..."

CILI_JWT_SECRET="$(openssl rand -hex 32)"

cat > /opt/cili/cili.env <<ENVEOF
# Spring Boot liest diese Variablen automatisch per Relaxed Binding
SPRING_PROFILES_ACTIVE=prod
CILI_DB_PASSWORD=${DB_PASSWORD}
CILI_JWT_SECRET=${CILI_JWT_SECRET}
HF_HOME=/opt/cili/whisper-models
# Python aus der venv — nötig, damit faster-whisper UND yt-dlp gefunden werden
CILI_PYTHON_PATH=/opt/cili/whisper-venv/bin/python3
# Nur für manuelle DB-Wartung
DB_ROOT_PASSWORD=${MARIADB_ROOT_PASSWORD}
DB_EXT_PASSWORD=${MARIADB_EXT_PASSWORD}
ENVEOF

chmod 600 /opt/cili/cili.env
chown cili:cili /opt/cili/cili.env

# ===== 9b. SPRING BOOT SYSTEMD-SERVICE =====
echo ""
echo "9b. CILI systemd-Service einrichten..."

cat > /etc/systemd/system/cili.service <<SVCEOF
[Unit]
Description=CILI Application (Spring Boot)
After=network.target mariadb.service

[Service]
Type=simple
User=cili
Group=cili
EnvironmentFile=/opt/cili/cili.env
WorkingDirectory=/opt/cili
ExecStart=${JAVA_HOME}/bin/java \
    -Xmx${JVM_MAX_MB}m \
    -Xms${JVM_MIN_MB}m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+AlwaysPreTouch \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/opt/cili/log/heapdump.hprof \
    -jar /opt/cili/cili-app.war
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
SVCEOF

systemctl daemon-reload
systemctl enable cili
echo "OK cili.service registriert (startet nach WAR-Deploy)"
echo "OK Konfiguration erstellt (Passwörter in /opt/cili/cili.env)"

# ===== 9c. WHISPER-MODELLE =====
[ -n "${HF_TOKEN}" ] && export HF_TOKEN
echo ""
echo "9c. Whisper-Modelle herunterladen (medium ~1.5 GB, large-v3 ~3.1 GB)..."
if [ "${SKIP_WHISPER_MODELS}" = "yes" ]; then
  echo "HINWEIS Übersprungen — Modelle später manuell laden:"
  echo "        HF_HOME=/opt/cili/whisper-models \\"
  echo "          /opt/cili/whisper-venv/bin/python3 -c \\"
  echo "          \"from huggingface_hub import snapshot_download; snapshot_download('Systran/faster-whisper-medium')\""
else
  echo "    Kann je nach Verbindung 10–30 Minuten dauern..."
  HF_HOME=/opt/cili/whisper-models \
    /opt/cili/whisper-venv/bin/python3 - <<'PYEOF'
import sys
try:
    from huggingface_hub import snapshot_download
    print("  Lade Systran/faster-whisper-medium (~1.5 GB)...", flush=True)
    snapshot_download("Systran/faster-whisper-medium")
    print("  OK medium")
    print("  Lade Systran/faster-whisper-large-v3 (~3.1 GB)...", flush=True)
    snapshot_download("Systran/faster-whisper-large-v3")
    print("  OK large-v3")
except Exception as e:
    print(f"FEHLER beim Modell-Download: {e}", file=sys.stderr, flush=True)
    print("Modelle können später heruntergeladen werden (siehe HINWEIS oben).", file=sys.stderr)
    sys.exit(1)
PYEOF
  chown -R cili:cili /opt/cili/whisper-models
  echo "OK Whisper-Modelle in /opt/cili/whisper-models"
fi

# ===== 9d. NLLB-MODELL =====
echo ""
echo "9d. NLLB-600M Übersetzungsmodell herunterladen und konvertieren (~2.5 GB)..."
mkdir -p /opt/cili/nllb

if [ "${SKIP_NLLB_MODEL}" = "yes" ]; then
  echo "HINWEIS Übersprungen — Modell später manuell konvertieren:"
  echo "        /opt/cili/whisper-venv/bin/ct2-transformers-converter \\"
  echo "          --model facebook/nllb-200-distilled-600M \\"
  echo "          --output_dir /opt/cili/nllb/nllb-600M \\"
  echo "          --quantization int8 \\"
  echo "          --copy_files sentencepiece.bpe.model tokenizer_config.json special_tokens_map.json \\"
  echo "          --force"
else
  echo "    Konvertierung via ct2-transformers-converter (lädt ~2.5 GB, kann 10–20 Min. dauern)..."
  /opt/cili/whisper-venv/bin/ct2-transformers-converter \
    --model facebook/nllb-200-distilled-600M \
    --output_dir /opt/cili/nllb/nllb-600M \
    --quantization int8 \
    --copy_files sentencepiece.bpe.model tokenizer_config.json special_tokens_map.json \
    --force && \
    echo "OK NLLB-600M (int8) in /opt/cili/nllb/nllb-600M" || \
    echo "FEHLER NLLB-Konvertierung fehlgeschlagen — Modell manuell laden (siehe HINWEIS oben)"
fi

chown -R cili:cili /opt/cili/nllb

# ===== 9e. VIDEO-IMPORT (yt-dlp + Deno) =====
echo ""
echo "9e. Video-Import einrichten (yt-dlp + Deno JS-Runtime)..."

# yt-dlp + HTTP-/Env-Abhängigkeiten für video_upload.py in dieselbe venv wie Whisper.
# cili.python-path muss auf /opt/cili/whisper-venv/bin/python3 zeigen, damit yt-dlp
# gefunden wird (siehe cili.env unten).
$VENV_PIP install -q yt-dlp requests python-dotenv
echo "OK yt-dlp, requests, python-dotenv in venv installiert"

# Deno als JS-Runtime: yt-dlp braucht sie, um YouTubes n-challenge/po_token zu lösen.
# Ohne Deno liefert YouTube (vor allem mit Cookies) nur Storyboards → Download-Fehler
# "Requested format is not available". Systemweit nach /usr/local/bin, damit der von
# Java (systemd-Service) gespawnte Python-Prozess Deno via PATH (shutil.which) findet.
if [ ! -x /usr/local/bin/deno ]; then
  curl -fsSL https://deno.land/install.sh | DENO_INSTALL=/usr/local sh
fi
if [ -x /usr/local/bin/deno ]; then
  echo "OK Deno $(/usr/local/bin/deno --version 2>/dev/null | head -1)"
else
  echo "WARNUNG Deno-Installation fehlgeschlagen — öffentliche YouTube-Videos gehen"
  echo "        trotzdem (Mobile-Client ohne Cookies); private/Cookie-Videos nicht."
fi

# video_upload.py in das konfigurierte scripts-dir (cili.scripts-dir=/opt/cili/scripts) deployen
mkdir -p /opt/cili/scripts
if [ -f "${SCRIPT_DIR}/scripts/video_upload.py" ]; then
  cp "${SCRIPT_DIR}/scripts/video_upload.py" /opt/cili/scripts/
  chmod 755 /opt/cili/scripts/video_upload.py
  echo "OK /opt/cili/scripts/video_upload.py deployed"
else
  echo "HINWEIS scripts/video_upload.py nicht gefunden — manuell kopieren:"
  echo "        cp scripts/video_upload.py /opt/cili/scripts/"
fi

# YouTube-Cookies (optional, für private/altersbeschränkte Videos):
# Netscape-Cookie-Datei nach /opt/cili/scripts/youtube-cookies.txt legen und in
# cili.video-import.cookies-file (bzw. YTDLP_COOKIES_FILE) eintragen. Ohne Cookies
# funktionieren öffentliche Videos.
echo "HINWEIS Für private YouTube-Videos: youtube-cookies.txt nach /opt/cili/scripts/ legen"

chown -R cili:cili /opt/cili/scripts

echo "HINWEIS: CILI startet erst nach WAR-Deploy — siehe Anleitung unten"

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    SETUP ABGESCHLOSSEN                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
echo "Status:"
echo "  MariaDB: $(systemctl is-active mariadb)"
echo "  CILI:    $(systemctl is-active cili) (wartet auf WAR)"
echo ""
echo "WAR deployen und starten:"
echo "  cp cili-app.war /opt/cili/"
echo "  chown cili:cili /opt/cili/cili-app.war"
echo "  systemctl start cili"
echo ""
echo "Python-Skripte deployen:"
echo "  cp scripts/transcribe_worker.py    /opt/cili/"
echo "  cp scripts/translate_worker.py     /opt/cili/"
echo "  cp scripts/doc_translate_worker.py /opt/cili/"
echo "  cp scripts/video_upload.py         /opt/cili/scripts/   # Video-Import (yt-dlp+Deno)"
echo ""
echo "CILI erreichbar unter:"
echo "  http://$(hostname -I | awk '{print $1}'):8080"
echo ""
echo "Nginx Reverse Proxy:"
echo "  upstream cili { server $(hostname -I | awk '{print $1}'):8080; }"
echo ""
echo "Konnektivitätstest:"
echo "  curl http://$(hostname -I | awk '{print $1}'):8080/api/actuator/health"
echo ""
echo "Logs:"
echo "  journalctl -u cili -f"
echo "  tail -f /opt/cili/log/cili.log"
echo ""
echo "Wichtige Pfade:"
echo "  WAR:           /opt/cili/cili-app.war"
echo "  Data:          /opt/cili/data"
echo "  Logs:          /opt/cili/log/"
echo "  Config:        /opt/cili/cili.env"
echo "  Whisper:       /opt/cili/transcribe_worker.py"
echo "  Translation:   /opt/cili/translate_worker.py"
echo "  Doc-Transl.:   /opt/cili/doc_translate_worker.py"
echo "  Venv:          /opt/cili/whisper-venv/bin/python3"
echo "  Whisper-Mdl.:  /opt/cili/whisper-models"
echo "  NLLB-Modell:   /opt/cili/nllb/nllb-600M"
echo "  Video-Import:  /opt/cili/scripts/video_upload.py (yt-dlp + Deno /usr/local/bin/deno)"
echo ""
echo "Datenbank:"
echo "  User:     cili@localhost"
echo "  Passwort: (in /opt/cili/cili.env)"
echo ""
echo "WICHTIG: Vor Produktionsbetrieb anpassen:"
echo "  - DB-Passwort in /opt/cili/cili.env"
echo "  - cili.cors.allowed-origins in application-prod.properties"
echo "  - Standard-Admin-Passwort nach erstem Login ändern"
echo ""
