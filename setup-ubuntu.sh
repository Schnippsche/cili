#!/bin/bash
# Setup für Ubuntu 24.04 LTS - OHNE Nginx (externer Reverse Proxy)
# Tomcat 11 auf Port 8080 | Java 21 (Adoptium Temurin) | MySQL 8.4

set -e

timedatectl set-timezone Europe/Berlin
timedatectl set-ntp true

# Versions — bei Bedarf aktualisieren:
#   https://tomcat.apache.org/download-11.cgi
TOMCAT_VERSION="11.0.20"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║     CILI Production Setup (No Nginx - Ext. Reverse Proxy)      ║"
echo "║     Java 21 (Temurin) + MySQL 8.4 + Tomcat 11                  ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

if [ "$EUID" -ne 0 ]; then
  echo "Fehler: Muss als root ausgeführt werden"
  exit 1
fi

DB_PASSWORD="${1:-CiliByDesign2022!}"
SKIP_WHISPER_MODELS="${2:-no}"   # 'yes' überspringt den ~5 GB Whisper-Modell-Download
SKIP_NLLB_MODEL="${3:-no}"       # 'yes' überspringt den ~2.5 GB NLLB-Modell-Download

echo "Parameter:"
echo "  - Datenbankpasswort:  (verborgen)"
echo "  - Whisper-Download:   ${SKIP_WHISPER_MODELS} (yes=überspringen)"
echo "  - NLLB-Download:      ${SKIP_NLLB_MODEL} (yes=überspringen)"
echo ""

# ===== 1. SYSTEM UPDATE =====
echo "1. System Update..."
# Veraltete MySQL-Einträge aus vorherigen Läufen entfernen, damit apt-get update
# nicht wegen fehlendem GPG-Schlüssel abbricht.
rm -f /etc/apt/sources.list.d/mysql*.list
rm -f /etc/apt/keyrings/mysql.gpg
apt-get update -q
apt-get upgrade -y -q
apt-get install -y -q curl wget git htop net-tools jq gnupg ca-certificates apt-transport-https
echo "OK System aktualisiert"

# ===== 2. SSH =====
echo ""
echo "2. SSH installieren und konfigurieren..."
apt-get install -y -q openssh-server

# Minimale Härtung: Root-Login deaktivieren
sed -i 's/^#*PermitRootLogin.*/PermitRootLogin no/' /etc/ssh/sshd_config
sed -i 's/^#*PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config

systemctl enable ssh
systemctl restart ssh
echo "OK SSH installiert und gestartet (Port 22, Root-Login deaktiviert)"

# SSH-Benutzer 'cili' anlegen
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
# UFW benötigt iptables/nftables — in unprivilegierten Proxmox-LXC-Containern
# nicht verfügbar; Firewall dort am Proxmox-Host konfigurieren.
if systemd-detect-virt --container --quiet 2>/dev/null; then
  echo "INFO Container-Umgebung erkannt – UFW übersprungen (Firewall am Host konfigurieren)"
else
  ufw --force enable
  ufw default deny incoming
  ufw default allow outgoing
  ufw allow 22/tcp
  ufw allow 8080/tcp
  ufw allow 3306/tcp
  echo "OK Firewall konfiguriert (SSH 22, Tomcat 8080, MySQL 3306)"
  echo "HINWEIS: Port 3306 ist offen — in Produktion auf bestimmte IPs einschränken:"
  echo "         ufw delete allow 3306/tcp && ufw allow from <IP> to any port 3306"
fi

# ===== SPEICHER-DIMENSIONIERUNG (für MySQL + Tomcat JVM) =====
TOTAL_MEM_MB=$(awk '/MemTotal/ {printf "%d", $2/1024}' /proc/meminfo)
MYSQL_BUFFER_MB=$((TOTAL_MEM_MB * 20 / 100))
JVM_MAX_MB=$((TOTAL_MEM_MB * 45 / 100))
JVM_MIN_MB=$((TOTAL_MEM_MB * 10 / 100))
echo ""
echo "Speicher: ${TOTAL_MEM_MB} MB → MySQL: ${MYSQL_BUFFER_MB} MB | Tomcat JVM: ${JVM_MAX_MB} MB"

# ===== 3. JAVA 21 (ADOPTIUM TEMURIN) =====
# openjdk-21-jdk-headless ist in Ubuntu 24.04 verfügbar; Temurin für aktuelle Sicherheits-Patches.
echo ""
echo "4. Java 21 installieren (Adoptium Temurin)..."
mkdir -p /etc/apt/keyrings
wget -q -O /etc/apt/keyrings/adoptium.asc \
    https://packages.adoptium.net/artifactory/api/gpg/key/public

# shellcheck source=/etc/os-release
echo "deb [signed-by=/etc/apt/keyrings/adoptium.asc] \
https://packages.adoptium.net/artifactory/deb noble main" \
    > /etc/apt/sources.list.d/adoptium.list

apt-get update -q
apt-get install -y -q temurin-21-jdk

JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(which java)")")")
echo "OK Java $(java -version 2>&1 | head -1)"
echo "   JAVA_HOME=${JAVA_HOME}"

# ===== 4. MYSQL 8 =====
# Ubuntu 24.04 enthält MySQL 8.0 in den Standard-Paketquellen — kein externes
# Repo nötig. mysql-apt-config ist unzuverlässig bei nicht-interaktiven Installs
# und scheitert, wenn der Codename nicht im Oracle-Repo hinterlegt ist.
echo ""
echo "5. MySQL 8 installieren (Ubuntu-Paketquellen)..."

apt-get install -y -q mysql-server

# innodb_log_file_size wurde in MySQL 8.0.30+ entfernt — weglassen!
# Buffer-Pool-Größe wird aus verfügbarem RAM berechnet (25 %).
cat > /etc/mysql/mysql.conf.d/cili.cnf <<CNFEOF
[mysqld]
bind-address=0.0.0.0
innodb_buffer_pool_size=${MYSQL_BUFFER_MB}M
innodb_flush_log_at_trx_commit=2
max_connections=50
wait_timeout=28800
max_allowed_packet=1G
character_set_server=utf8mb4
collation_server=utf8mb4_unicode_ci
default_storage_engine=InnoDB
slow_query_log=ON
slow_query_log_file=/var/log/mysql/slow.log
long_query_time=2
CNFEOF

systemctl enable mysql
systemctl restart mysql

# DB_PASSWORD wird aus Skript-Argument (oder Default) übernommen.
MYSQL_ROOT_PASSWORD="$(openssl rand -base64 24)"
MYSQL_EXT_PASSWORD="$(openssl rand -base64 24)"

mysql -u root <<SQLEOF
ALTER USER 'root'@'localhost' IDENTIFIED WITH caching_sha2_password BY '${MYSQL_ROOT_PASSWORD}';
CREATE DATABASE IF NOT EXISTS cili CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'cili'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON cili.* TO 'cili'@'localhost';
CREATE USER IF NOT EXISTS 'cili_ext'@'%' IDENTIFIED BY '${MYSQL_EXT_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE ON cili.* TO 'cili_ext'@'%';
FLUSH PRIVILEGES;
SQLEOF

MYSQL_VERSION=$(mysql --version 2>/dev/null | grep -oP 'Distrib \K[0-9.]+' || echo "8.x")
echo "OK MySQL ${MYSQL_VERSION} konfiguriert"

# ===== 5. TOMCAT 11 (MANUELL) =====
# tomcat11 ist in Ubuntu 24.04 nicht im APT-Repository — manuelle Installation.
echo ""
echo "6. Tomcat 11 installieren (manuell)..."

id -u tomcat &>/dev/null || useradd -r -s /bin/false -M -d /opt/tomcat11 tomcat

curl -fsSL \
    "https://archive.apache.org/dist/tomcat/tomcat-11/v${TOMCAT_VERSION}/bin/apache-tomcat-${TOMCAT_VERSION}.tar.gz" \
    -o /tmp/apache-tomcat.tar.gz || {
  echo "Fehler: Tomcat ${TOMCAT_VERSION} konnte nicht heruntergeladen werden"
  echo "Verfügbare Versionen: https://archive.apache.org/dist/tomcat/tomcat-11/"
  exit 1
}

mkdir -p /opt/tomcat11
tar xzf /tmp/apache-tomcat.tar.gz -C /opt/tomcat11 --strip-components=1
rm /tmp/apache-tomcat.tar.gz

if [ ! -f /opt/tomcat11/bin/startup.sh ]; then
  echo "Fehler: Tomcat-Entpacken fehlgeschlagen — /opt/tomcat11/bin/startup.sh fehlt"
  exit 1
fi

chown -R tomcat:tomcat /opt/tomcat11
chmod -R u+x /opt/tomcat11/bin

# JAVA_HOME wird zum Installationszeitpunkt fest eingetragen.
# DB_PASSWORD kommt zur Laufzeit aus dem systemd EnvironmentFile (/opt/cili/cili.env)
# und wird in setenv.sh als ${DB_PASSWORD} referenziert (Backslash verhindert
# vorzeitige Expansion durch die Shell beim Schreiben dieser Datei).
# -XX:+ParallelRefProcEnabled wurde in Java 22 entfernt — nicht verwenden!
# JVM-Heap-Größen werden aus verfügbarem RAM berechnet (50 % / 12 %).
cat > /opt/tomcat11/bin/setenv.sh <<SETENVEOF
export JAVA_HOME="${JAVA_HOME}"
export HF_HOME="/opt/cili/whisper-models"
CATALINA_OPTS="-Xmx${JVM_MAX_MB}m -Xms${JVM_MIN_MB}m"
CATALINA_OPTS="\$CATALINA_OPTS -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch"
CATALINA_OPTS="\$CATALINA_OPTS -Dfile.encoding=UTF-8 -Djava.awt.headless=true"
CATALINA_OPTS="\$CATALINA_OPTS -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/log/cili/heapdump.hprof"
CATALINA_OPTS="\$CATALINA_OPTS -Dcili.storage.base-path=/var/cili/data"
CATALINA_OPTS="\$CATALINA_OPTS -Dspring.profiles.active=prod"
CATALINA_OPTS="\$CATALINA_OPTS -Dspring.datasource.password=\${DB_PASSWORD}"
CATALINA_OPTS="\$CATALINA_OPTS -Dcili.whisper.python-path=/opt/cili/whisper-venv/bin/python3"
CATALINA_OPTS="\$CATALINA_OPTS -Dcili.whisper.script-path=/opt/cili/transcribe_worker.py"
CATALINA_OPTS="\$CATALINA_OPTS -Dcili.nllb.script-path=/opt/cili/translate_worker.py"
CATALINA_OPTS="\$CATALINA_OPTS -Dcili.nllb.doc-script-path=/opt/cili/doc_translate_worker.py"
CATALINA_OPTS="\$CATALINA_OPTS -Dcili.nllb.model-path=/opt/cili/nllb/nllb-600M"
export CATALINA_OPTS
SETENVEOF
chown tomcat:tomcat /opt/tomcat11/bin/setenv.sh
chmod +x /opt/tomcat11/bin/setenv.sh

# Systemd-Service für Tomcat 11.
# EnvironmentFile stellt DB_PASSWORD und JAVA_HOME zur Laufzeit bereit
# (Datei wird in Schritt 8 erstellt, vor dem Start in Schritt 11).
cat > /etc/systemd/system/tomcat11.service <<SVCEOF
[Unit]
Description=Apache Tomcat 11
After=network.target mysql.service

[Service]
Type=forking
User=tomcat
Group=tomcat
EnvironmentFile=/opt/cili/cili.env
Environment="JAVA_HOME=${JAVA_HOME}"
Environment="CATALINA_HOME=/opt/tomcat11"
Environment="CATALINA_BASE=/opt/tomcat11"
Environment="CATALINA_PID=/opt/tomcat11/temp/tomcat.pid"
ExecStart=/opt/tomcat11/bin/startup.sh
ExecStop=/opt/tomcat11/bin/shutdown.sh
Restart=on-failure
RestartSec=10
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
SVCEOF

systemctl daemon-reload
systemctl enable tomcat11
echo "OK Tomcat ${TOMCAT_VERSION} installiert und als Dienst registriert"

# ===== 6. TOOLS =====
echo ""
echo "7. FFmpeg und LibreOffice installieren..."
rm -f /etc/apt/sources.list.d/universe.list
rm -rf /var/lib/apt/lists/*
apt-get update -q
# libreoffice-headless wurde in Ubuntu 24.04 zu libreoffice-nogui umbenannt.
# Download ~300 MB — kann mehrere Minuten dauern.
echo "  (LibreOffice ~300 MB, bitte warten...)"
apt-get install -y -q ffmpeg libreoffice-nogui
echo "OK Tools installiert"

# ===== 7b. PYTHON + FASTER-WHISPER =====
echo ""
echo "7b. Python 3 + faster-whisper installieren..."
apt-get install -y -q python3 python3-venv python3-pip build-essential

mkdir -p /opt/cili
python3 -m venv /opt/cili/whisper-venv

VENV_PIP="/opt/cili/whisper-venv/bin/pip"
$VENV_PIP install -q --upgrade pip setuptools wheel

# Whisper
$VENV_PIP install -q faster-whisper

# Translation
$VENV_PIP install -q \
    ctranslate2 \
    transformers \
    sentencepiece \
    pymupdf \
    python-docx

# torch + torchvision (CPU-only, ~250 MB) — wird von wtpsplit SaT benötigt.
# ctranslate2 bündelt eigene CUDA-Libs, daher reicht CPU-torch für SaT.
$VENV_PIP install -q torch torchvision --index-url https://download.pytorch.org/whl/cpu

echo "OK faster-whisper, ctranslate2, wtpsplit installiert in /opt/cili/whisper-venv"

# GPU-Erkennung: NVIDIA-Treiber >= 525 reicht, ctranslate2 bündelt CUDA-Bibliotheken
if command -v nvidia-smi &>/dev/null; then
  GPU_NAME=$(nvidia-smi --query-gpu=name --format=csv,noheader 2>/dev/null | head -1 || echo "unbekannt")
  echo "OK GPU erkannt: ${GPU_NAME}"
  echo "   ctranslate2 bündelt CUDA 12.x — kein separates CUDA-Toolkit nötig"
  echo "   Voraussetzung: NVIDIA-Treiber >= 525 (aktuelle Version prüfen: nvidia-smi)"
else
  echo "HINWEIS Keine NVIDIA-GPU erkannt — Whisper / translation laufen auf CPU (langsam)"
  echo "        Für GPU: NVIDIA-Treiber installieren, dann in application.yml device=cuda setzen"
fi

# Python-Skripte deployen (liegen im Repo neben setup-ubuntu.sh)
SCRIPT_DIR="$(dirname "$(realpath "$0")")"
if [ -f "${SCRIPT_DIR}/scripts/transcribe_worker.py" ]; then
  cp "${SCRIPT_DIR}/scripts/transcribe_worker.py" /opt/cili/
  chmod 755 /opt/cili/transcribe_worker.py
  echo "OK /opt/cili/transcribe_worker.py deployed"
else
  echo "HINWEIS scripts/transcribe_worker.py nicht gefunden — manuell kopieren:"
  echo "        cp scripts/transcribe_worker.py /opt/cili/"
fi

if [ -f "${SCRIPT_DIR}/scripts/translate_worker.py" ]; then
  cp "${SCRIPT_DIR}/scripts/translate_worker.py" /opt/cili/
  chmod 755 /opt/cili/translate_worker.py
  echo "OK /opt/cili/translate_worker.py deployed"
else
  echo "HINWEIS scripts/translate_worker.py nicht gefunden — manuell kopieren:"
  echo "        cp scripts/translate_worker.py /opt/cili/"
fi

if [ -f "${SCRIPT_DIR}/scripts/doc_translate_worker.py" ]; then
  cp "${SCRIPT_DIR}/scripts/doc_translate_worker.py" /opt/cili/
  chmod 755 /opt/cili/doc_translate_worker.py
  echo "OK /opt/cili/doc_translate_worker.py deployed"
else
  echo "HINWEIS scripts/doc_translate_worker.py nicht gefunden — manuell kopieren:"
  echo "        cp scripts/doc_translate_worker.py /opt/cili/"
fi

# wtpsplit SaT-Modell vorab herunterladen (verhindert langen Delay beim ersten Job)
echo "  Lade wtpsplit SaT-Modell (sat-3l-sm, ~20 MB)..."
/opt/cili/whisper-venv/bin/python3 -c "from wtpsplit import SaT; SaT('sat-3l-sm')" && \
  echo "OK wtpsplit sat-3l-sm gecacht" || \
  echo "HINWEIS wtpsplit-Modell konnte nicht vorab geladen werden — wird beim ersten Job geladen"

# ===== 7. DIRECTORIES =====
echo ""
echo "8. CILI-Verzeichnisse erstellen..."
mkdir -p /opt/cili
mkdir -p /var/cili/data
mkdir -p /var/log/cili
mkdir -p /opt/cili/backups/{mysql,data}
mkdir -p /opt/cili/whisper-models
mkdir -p /opt/cili/nllb

# Tomcat läuft als 'tomcat' — dieser User braucht Schreibzugriff auf /var/cili/data
chown -R tomcat:tomcat /opt/cili
chown -R tomcat:tomcat /var/cili/data
chown -R tomcat:tomcat /var/log/cili
chmod 750 /var/cili/data
chmod 750 /var/log/cili
echo "OK Verzeichnisse erstellt"

# ===== 8. CONFIG =====
echo ""
echo "9. Konfigurationsdateien erstellen..."

# cili.env wird als systemd EnvironmentFile eingebunden (tomcat11.service).
# Enthält DB_PASSWORD für Spring (-Dspring.datasource.password) und JAVA_HOME.
cat > /opt/cili/cili.env <<ENVEOF
DB_PASSWORD=${DB_PASSWORD}
DB_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}
DB_EXT_PASSWORD=${MYSQL_EXT_PASSWORD}
JAVA_HOME=${JAVA_HOME}
ENVEOF

chmod 600 /opt/cili/cili.env
chown tomcat:tomcat /opt/cili/cili.env
echo "OK Konfiguration erstellt (Passwörter in /opt/cili/cili.env)"

# ===== 8b. WHISPER-MODELLE =====
echo ""
echo "8b. Whisper-Modelle herunterladen (medium ~1.5 GB, large-v3 ~3.1 GB)..."
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
  chown -R tomcat:tomcat /opt/cili/whisper-models
  echo "OK Whisper-Modelle in /opt/cili/whisper-models"
fi

# ===== 8c. NLLB-MODELL =====
echo ""
echo "8c. NLLB-600M Übersetzungsmodell herunterladen und konvertieren (~2.5 GB)..."
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

chown -R tomcat:tomcat /opt/cili/nllb

# ===== 9. LOGROTATE =====
echo ""
echo "9. Log-Rotation konfigurieren..."
cat > /etc/logrotate.d/cili <<'LOGEOF'
/var/log/cili/*.log {
  daily
  rotate 30
  compress
  delaycompress
  missingok
  notifempty
  create 0640 tomcat tomcat
  sharedscripts
}
LOGEOF
echo "OK Log-Rotation konfiguriert"

# ===== 11. MONITORING =====
echo ""
echo "11. Monitoring-Skripte einrichten..."
cat > /opt/cili/health-check.sh <<'HEALTHEOF'
#!/bin/bash
LOGFILE="/var/log/cili/health-check.log"
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOGFILE"; }

log "Health check running..."

systemctl is-active --quiet tomcat11 || { log "WARN Tomcat DOWN - restart"; systemctl start tomcat11; }
systemctl is-active --quiet mysql    || { log "WARN MySQL DOWN - restart";  systemctl start mysql; }

disk=$(df /var/cili/data | awk 'NR==2 {print $5}' | sed 's/%//')
[ "$disk" -gt 90 ] && log "WARN Disk: ${disk}%" || log "OK Disk: ${disk}%"

log "Health check complete"
HEALTHEOF

chmod +x /opt/cili/health-check.sh
chown tomcat:tomcat /opt/cili/health-check.sh

cat > /etc/cron.d/cili-monitoring <<'CRONEOF'
*/5 * * * * root /opt/cili/health-check.sh >/dev/null 2>&1
CRONEOF

echo "OK Monitoring konfiguriert"

# ===== 12. DIENSTE STARTEN =====
echo ""
echo "12. Dienste starten..."
systemctl start mysql
systemctl start tomcat11
echo "Warte auf Tomcat-Start (max. 60 Sekunden)..."
for i in $(seq 1 30); do
  sleep 2
  if ss -tlnp 2>/dev/null | grep -q ':8080'; then
    echo "OK Tomcat antwortet auf Port 8080"
    break
  fi
  [ "$i" -eq 30 ] && echo "HINWEIS Tomcat noch nicht bereit — ggf. 'journalctl -u tomcat11 -f' prüfen"
done

echo ""
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    SETUP ABGESCHLOSSEN                        ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""
echo "Status:"
echo "  MySQL:  $(systemctl is-active mysql)"
echo "  Tomcat: $(systemctl is-active tomcat11)"
echo ""
echo "CILI erreichbar unter:"
echo "  http://$(hostname -I | awk '{print $1}'):8080"
echo ""
echo "Nginx Reverse Proxy Konfiguration:"
echo "  upstream cili_tomcat {"
echo "    server $(hostname -I | awk '{print $1}'):8080;"
echo "  }"
echo ""
echo "Konnektivitätstest vom Nginx-Server:"
echo "  curl http://$(hostname -I | awk '{print $1}'):8080/api/actuator/health"
echo ""
echo "WAR + Python-Skripte deployen:"
echo "  cp cili-app.war /opt/tomcat11/webapps/ROOT.war"
echo "  cp scripts/transcribe_worker.py    /opt/cili/"
echo "  cp scripts/translate_worker.py     /opt/cili/"
echo "  cp scripts/doc_translate_worker.py /opt/cili/"
echo "  systemctl restart tomcat11"
echo ""
echo "Wichtige Pfade:"
echo "  WAR:           /opt/tomcat11/webapps/ROOT.war"
echo "  Data:          /var/cili/data"
echo "  Logs:          /var/log/cili/ | /opt/tomcat11/logs/"
echo "  Whisper:       /opt/cili/transcribe_worker.py"
echo "  Translation:   /opt/cili/translate_worker.py"
echo "  Doc-Transl.:   /opt/cili/doc_translate_worker.py"
echo "  Venv:          /opt/cili/whisper-venv/bin/python3"
echo "  Whisper-Mdl.:  /opt/cili/whisper-models"
echo "  NLLB-Modell:   /opt/cili/nllb/nllb-600M"
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
