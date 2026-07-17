#!/bin/bash
# Health Check für Nginx Server (läuft auf 192.168.178.110)
# Prüft: Nginx, CILI-Container Erreichbarkeit, SSL-Zertifikat
# Cron: */5 * * * * root /opt/cili/health-check-nginx.sh

set -e

LOGFILE="/var/log/cili/health-check-nginx.log"
CILI_IP="192.168.178.120"
CILI_PORT="8080"

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOGFILE"
}

# ===== NGINX CHECK =====
nginx_check() {
  if systemctl is-active --quiet nginx; then
    log "✓ Nginx is running"
    return 0
  else
    log "✗ Nginx is DOWN - attempting restart..."
    systemctl start nginx
    sleep 2
    if systemctl is-active --quiet nginx; then
      log "✓ Nginx restarted successfully"
      return 0
    else
      log "✗ Nginx restart FAILED"
      return 1
    fi
  fi
}

# Hinweis: CILI Container wird NICHT von hier aus geprüft
# CILI Container ist Verantwortung des Container-Health-Checks

# ===== SSL CERTIFICATE CHECK =====
ssl_check() {
  # Prüfe ob Zertifikat existiert
  if [ ! -f /etc/ssl/certs/cloudflare-origin.crt ]; then
    log "⚠️  SSL Certificate not found: /etc/ssl/certs/cloudflare-origin.crt"
    return 1
  fi

  # Prüfe Ablaufdatum
  CERT_DATE=$(openssl x509 -enddate -noout -in /etc/ssl/certs/cloudflare-origin.crt 2>/dev/null | cut -d= -f2)
  CERT_TIMESTAMP=$(date -d "$CERT_DATE" +%s 2>/dev/null || echo "0")
  NOW_TIMESTAMP=$(date +%s)
  DAYS_UNTIL_EXPIRY=$(( ($CERT_TIMESTAMP - $NOW_TIMESTAMP) / 86400 ))

  if [ "$DAYS_UNTIL_EXPIRY" -lt 0 ]; then
    log "✗ SSL Certificate has EXPIRED: $CERT_DATE"
    return 1
  elif [ "$DAYS_UNTIL_EXPIRY" -lt 30 ]; then
    log "⚠️  SSL Certificate expires in $DAYS_UNTIL_EXPIRY days: $CERT_DATE"
    return 1
  else
    log "✓ SSL Certificate valid for $DAYS_UNTIL_EXPIRY more days: $CERT_DATE"
    return 0
  fi
}

# ===== NGINX ACCESS LOG CHECK =====
log_check() {
  # Prüfe ob Nginx Errors in den letzten 5 Minuten
  RECENT_ERRORS=$(tail -100 /var/log/nginx/cili_external_error.log 2>/dev/null | grep -c "error" || echo "0")
  if [ "$RECENT_ERRORS" -gt 0 ]; then
    log "⚠️  Found $RECENT_ERRORS errors in Nginx logs"
    tail -3 /var/log/nginx/cili_external_error.log >> "$LOGFILE"
  else
    log "✓ No recent Nginx errors"
  fi
}

# ===== DISK SPACE CHECK =====
disk_check() {
  DISK_USAGE=$(df /var/log | awk 'NR==2 {print $5}' | sed 's/%//')
  if [ "$DISK_USAGE" -gt 90 ]; then
    log "⚠️  Disk usage HIGH: $DISK_USAGE%"
    return 1
  else
    log "✓ Disk usage: $DISK_USAGE%"
    return 0
  fi
}

# ===== MAIN =====
log "════════════════════════════════════════════════════════════════════════════"
log "Nginx Health Check START"

failed=0

nginx_check || ((failed++))
ssl_check || ((failed++))
log_check || ((failed++))
disk_check || ((failed++))

if [ $failed -eq 0 ]; then
  log "✓ All checks PASSED"
else
  log "⚠️  $failed checks had issues"
fi

log "Nginx Health Check END"
log "════════════════════════════════════════════════════════════════════════════"
log ""
