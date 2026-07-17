# Proxmox — Hardware-Upgrade & Neuaufbau

## Ziel-Architektur

```
NVMe (466GB)      → Proxmox OS + LXC-Root-Disks (local-lvm)   ← bleibt unverändert
1x 6TB IronWolf   → /mnt/data (ext4, alle Nutzdaten)           ← neu
7.3TB WD Red      → /mnt/data3 (nur Backup)                    ← bleibt

Datenstruktur auf /mnt/data:
  /mnt/data/homes/andrea  → SMB \\fileserver\andrea  (CT 112)
  /mnt/data/homes/stefan  → SMB \\fileserver\stefan  (CT 112)
  /mnt/data/cili          → Bind-Mount in CT 110
  /mnt/data/frigate       → Bind-Mount in CT 111

Container (aus Backup wiederhergestellt):
  CT 109  AdGuard             → DNS
  CT 106  NginxProxyManager   → Reverse Proxy + Cloudflare Tunnel
  CT 107  Omada               → Netzwerk-Controller
  CT 110  CILI                → Medienverwaltung (GPU: Whisper/NLLB)
  CT 105  SonarQube           → Code-Qualität
  CT 111  Frigate             → Surveillance (GPU)              ← neu
  CT 112  Fileserver          → Samba (Andrea, Stefan)          ← neu

Hardware-Änderungen:
  2x WD Red 1.8TB  → werden AUSGEBAUT
  1x IronWolf 6TB  → wird EINGEBAUT
  RTX 3050 6GB     → wird EINGEBAUT
  Neues Netzteil   → wird EINGEBAUT
```

---

## Phase 0: Vorbereitung (System läuft noch)

### fstab bereinigen — VOR dem Ausbauen der Platten
Die alten Platten stehen ohne `nofail` in der fstab → System würde nach dem Ausbauen nicht mehr booten.
```bash
sed -i '/data1\|data2/d' /etc/fstab
cat /etc/fstab | grep data   # prüfen ob Einträge weg sind
```

### Backups prüfen — alle 6 Container müssen vorhanden sein
```bash
ls /mnt/data3/dump/*.tar.zst | grep -oP 'lxc-\d+' | sort -u
# Erwartet: lxc-102 lxc-105 lxc-106 lxc-107 lxc-109 lxc-110
```

### Proxmox herunterfahren
```bash
shutdown -h now
```

---

## Phase 1: Hardware-Einbau

- [ ] Foto der aktuellen Verkabelung machen
- [ ] Altes Netzteil ausbauen, neues einbauen (mind. 550W für RTX 3050)
- [ ] 2x WD Red 1.8TB (sda/sdb) ausbauen
- [ ] 1x Seagate IronWolf 6TB einbauen
- [ ] RTX 3050 in PCIe x16-Slot, Stromkabel anschließen
- [ ] System starten

### Hardware prüfen
```bash
# Alle Platten sichtbar?
lsblk -dno NAME,SIZE,MODEL | grep -v loop

# Erwarteter Output:
# sda    5.5T  ST6000VN001-...   ← IronWolf 6TB
# sdb    7.3T  WDC WD80EFPX-...  ← Backup
# nvme0n1 465.8G Samsung SSD ... ← System (unverändert)

# GPU erkannt?
lspci | grep -i nvidia
```

---

## Phase 2: Proxmox bereinigen

### Alte Storage-Einträge entfernen
```bash
pvesm remove data1
pvesm remove data2

# fstab-Einträge für alte Platten löschen
sed -i '/data1\|data2/d' /etc/fstab
```

### Backups nach Reboot prüfen
```bash
ls /mnt/data3/dump/*.tar.zst   # data3 ist bereits gemountet und in Proxmox registriert
```

### 6TB IronWolf partitionieren und einbinden
```bash
DATA_DEV=$(lsblk -dno NAME,MODEL | awk '/ST6000/ {print "/dev/"$1}')
echo "6TB: $DATA_DEV"

# Partitionieren und formatieren
parted ${DATA_DEV} --script mklabel gpt mkpart primary ext4 0% 100%
mkfs.ext4 -L data ${DATA_DEV}1

# In fstab eintragen
UUID_DATA=$(blkid -s UUID -o value ${DATA_DEV}1)
mkdir -p /mnt/data
echo "UUID=${UUID_DATA}  /mnt/data  ext4  defaults,nofail  0  2" >> /etc/fstab
mount -a

# Verzeichnisstruktur anlegen
mkdir -p /mnt/data/homes/andrea /mnt/data/homes/stefan
mkdir -p /mnt/data/cili /mnt/data/frigate

# Berechtigungen für Bind-Mounts in unprivilegierten Containern
# (root im Container = UID 100000 auf dem Host)
chown -R 100000:100000 /mnt/data/cili /mnt/data/frigate
chmod 700 /mnt/data/homes/andrea /mnt/data/homes/stefan
```

### data als Storage registrieren
```bash
pvesm add dir data --path /mnt/data --content rootdir,images,snippets
```

---

## Phase 3: Container wiederherstellen

> **Reihenfolge einhalten** — AdGuard (DNS) und Nginx müssen zuerst laufen.

Im Proxmox Web-UI für jeden Container:
`Datacenter → backup-data3 → [neuestes Backup] → Restore`
- Storage: `local-lvm`
- VMID beibehalten

### Reihenfolge und Startkontrolle

```bash
# 1. AdGuard
# → Restore CT 109, dann:
pct start 109
pct exec 109 -- nslookup google.com   # DNS funktioniert?

# 2. NginxProxyManager + Cloudflare Tunnel
# → Restore CT 106, dann:
pct start 106
pct exec 106 -- systemctl status cloudflared   # Tunnel aktiv?

# 3. Omada
# → Restore CT 107, dann:
pct start 107

# 4. CILI — Bind-Mount vor dem Start setzen
pct set 110 --mp0 /mnt/data/cili,mp=/var/cili/data
# → Restore CT 110, dann:
pct start 110

# 5. SonarQube
# → Restore CT 105, dann:
pct start 105
```

---

## Phase 4: NVIDIA-Treiber prüfen

> Treiber bereits installiert — nur verifizieren dass die RTX 3050 erkannt wird.

```bash
lspci | grep -i nvidia      # GPU sichtbar?
lsmod | grep nvidia         # Modul geladen?
nvidia-smi                  # GPU-Status
```

### NVIDIA Device-Nummern prüfen
```bash
ls -la /dev/nvidia*
# Ausgabe zeigt Major-Nummern, z.B.:
# crw-rw-rw- ... 195, 0 /dev/nvidia0
# crw-rw-rw- ... 195, 255 /dev/nvidiactl
# crw-rw-rw- ... 507, 0 /dev/nvidia-uvm       ← diese Nummer kann sich ändern!
# crw-rw-rw- ... 507, 1 /dev/nvidia-uvm-tools
```

Falls die nvidia-uvm Major-Nummer nicht `507` ist, `/etc/pve/lxc/110.conf` anpassen:
```bash
# Aktuelle Nummer auslesen
UVM_MAJOR=$(ls -la /dev/nvidia-uvm | awk '{print $5}' | tr -d ',')
echo "nvidia-uvm Major: $UVM_MAJOR"

# In 110.conf ersetzen falls abweichend
sed -i "s/lxc.cgroup2.devices.allow: c 5[0-9][0-9]:\*/lxc.cgroup2.devices.allow: c ${UVM_MAJOR}:*/" \
  /etc/pve/lxc/110.conf

# Prüfen
grep cgroup2 /etc/pve/lxc/110.conf
```

### GPU-Zugang für CILI (CT 110) prüfen
> GPU-Passthrough bereits in 110.conf konfiguriert — wird mit dem Backup wiederhergestellt.
```bash
pct exec 110 -- nvidia-smi   # GPU im Container sichtbar?
```

---

## Phase 5: Fileserver LXC (Samba)

```bash
# Debian-Template herunterladen (falls nicht vorhanden)
pveam download data3 debian-12-standard_12.7-1_amd64.tar.zst

# LXC erstellen
pct create 112 data3:vztmpl/debian-12-standard_12.7-1_amd64.tar.zst \
  --hostname fileserver \
  --memory 512 \
  --cores 1 \
  --rootfs local-lvm:8 \
  --net0 name=eth0,bridge=vmbr0,ip=dhcp \
  --unprivileged 0

# Homes-Verzeichnis einbinden
pct set 112 --mp0 /mnt/data/homes,mp=/mnt/homes

pct start 112

# Samba installieren und User anlegen
pct exec 112 -- bash -c "
  apt update && apt install -y samba

  # Linux-User ohne Login anlegen
  useradd -M -s /sbin/nologin andrea
  useradd -M -s /sbin/nologin stefan

  # Homeverzeichnisse mit korrekten Rechten
  mkdir -p /mnt/homes/andrea /mnt/homes/stefan
  chown andrea:andrea /mnt/homes/andrea
  chown stefan:stefan /mnt/homes/stefan
  chmod 700 /mnt/homes/andrea /mnt/homes/stefan
"

# Samba-Passwörter setzen (interaktiv, 2x eingeben)
pct exec 112 -- smbpasswd -a andrea
pct exec 112 -- smbpasswd -a stefan
```

### Samba-Konfiguration
```bash
pct exec 112 -- bash -c "cat >> /etc/samba/smb.conf << 'EOF'

[andrea]
  path = /mnt/homes/andrea
  valid users = andrea
  read only = no
  browseable = no

[stefan]
  path = /mnt/homes/stefan
  valid users = stefan
  read only = no
  browseable = no
EOF
systemctl restart smbd"
```

> **Windows-Verbindung:** `\\fileserver\andrea` im Explorer öffnen,
> Anmeldedaten: `andrea` + Passwort — einmalig speichern.

---

## Phase 6: Frigate LXC (Surveillance)

```bash
# Ubuntu-Template herunterladen (im Web-UI oder:)
pveam download data3 ubuntu-24.04-standard_24.04-2_amd64.tar.zst

# LXC erstellen
pct create 111 data3:vztmpl/ubuntu-24.04-standard_24.04-2_amd64.tar.zst \
  --hostname frigate \
  --memory 4096 \
  --cores 2 \
  --rootfs local-lvm:32 \
  --net0 name=eth0,bridge=vmbr0,ip=dhcp \
  --unprivileged 1 \
  --features nesting=1

# Bind-Mount für Aufnahmen
pct set 111 --mp0 /mnt/data/frigate,mp=/media/frigate

# GPU-Zugang
cat >> /etc/pve/lxc/111.conf << 'EOF'
lxc.cgroup2.devices.allow: c 195:* rwm
lxc.cgroup2.devices.allow: c 508:* rwm
lxc.mount.entry: /dev/nvidia0 dev/nvidia0 none bind,optional,create=file
lxc.mount.entry: /dev/nvidiactl dev/nvidiactl none bind,optional,create=file
lxc.mount.entry: /dev/nvidia-uvm dev/nvidia-uvm none bind,optional,create=file
lxc.mount.entry: /dev/nvidia-uvm-tools dev/nvidia-uvm-tools none bind,optional,create=file
EOF

pct start 111

# Frigate installieren
pct exec 111 -- bash -c "
  apt update && apt install -y docker.io docker-compose-plugin
  mkdir -p /opt/frigate
"
```

---

## Phase 7: Monitoring

### SMART-Monitoring
```bash
apt install -y smartmontools

# Alle Platten automatisch eintragen
for DEV in $(lsblk -dno NAME | grep -v loop | grep -v nvme); do
  echo "/dev/${DEV} -a -o on -S on -s (S/../.././02|L/../../6/03) -m root -M exec /usr/share/smartmontools/smartd-runner" \
    >> /etc/smartd.conf
done

systemctl enable smartd --now
```

### Automatische Backups
```
Proxmox Web-UI → Datacenter → Backup → Add:
  Storage:   backup-data3
  Schedule:  täglich 03:00
  Auswahl:   Alle Container
  Modus:     Snapshot
  Retention: 7 Tage
```

---

## Abschluss-Checkliste

- [ ] Alle Container laufen (`pct list`)
- [ ] DNS funktioniert (AdGuard)
- [ ] Externe Domains erreichbar (Cloudflare Tunnel)
- [ ] CILI erreichbar, Whisper/NLLB auf GPU (`nvidia-smi`)
- [ ] Fileserver: `\\fileserver\andrea` und `\\fileserver\stefan` erreichbar
- [ ] CILI: `/var/cili/data` gemountet (`df -h` im Container)
- [ ] Frigate: `/media/frigate` gemountet, Kameras verbunden
- [ ] Automatische Backups aktiv
- [ ] SMART-Monitoring aktiv

---

## Nützliche Befehle

```bash
pct list                          # Container-Status
nvidia-smi                        # GPU-Status
pvesm status                      # Storage-Übersicht
smartctl -a /dev/sda              # SMART einer Platte
ls -lh /mnt/data3/dump/           # Backup-Liste
lsblk -o NAME,SIZE,FSTYPE,MODEL   # Festplatten-Übersicht
df -h /mnt/data /mnt/data3        # Füllstand beider Platten
```
