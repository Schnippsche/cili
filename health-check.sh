#!/bin/bash

# Health Check Script für CILI in Produktion
# Cron: * * * * * /opt/cili/health-check.sh >> /var/log/cili/health-check.log 2>&1

set -e

LOGFILE="/var/log/cili/health-check.log"
ALERT_THRESHOLD_DISK=90
ALERT_THRESHOLD_CPU=80
ALERT_THRESHOLD_MEMORY=85
WEBHOOK_URL="${WEBHOOK_URL:-}"  # Optional: Discord/Slack Webhook

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOGFILE"; }
alert() {
  log "🚨 ALERT: $*"
  if [ -n "$WEBHOOK_URL" ]; then
    curl -X POST "$WEBHOOK_URL" -H 'Content-Type: application/json' \
      -d "{\"content\":\"🚨 CILI Alert: $*\"}" 2>/dev/null || true
  fi
}

# ===== TOMCAT CHECK =====
tomcat_status() {
  if systemctl is-active --quiet tomcat; then
    log "✓ Tomcat is running"

    # HTTP Response check
    response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/ --max-time 5 || echo "000")
    if [ "$response" = "302" ] || [ "$response" = "200" ]; then
      log "✓ Tomcat HTTP: $response"
    else
      alert "Tomcat HTTP error: $response"
      return 1
    fi
  else
    alert "Tomcat is NOT running"
    systemctl start tomcat || alert "Failed to restart Tomcat"
    return 1
  fi
}

# ===== MYSQL CHECK =====
mysql_status() {
  if systemctl is-active --quiet mysql; then
    log "✓ MySQL is running"

    # Connection test
    mysql -u cili -p"${DB_PASSWORD}" -e "SELECT 1;" 2>/dev/null || {
      alert "MySQL connection failed"
      return 1
    }
    log "✓ MySQL connection OK"
  else
    alert "MySQL is NOT running"
    systemctl start mysql || alert "Failed to restart MySQL"
    return 1
  fi
}

# ===== APPLICATION HEALTH =====
app_health() {
  health=$(curl -s http://localhost:8080/api/actuator/health 2>/dev/null || echo '{}')
  status=$(echo "$health" | grep -o '"status":"[^"]*' | cut -d'"' -f4)

  if [ "$status" = "UP" ]; then
    log "✓ Application health: $status"
  else
    alert "Application health: $status - $health"
    return 1
  fi
}

# ===== DISK SPACE CHECK =====
disk_check() {
  disk_usage=$(df /var/cili/data | awk 'NR==2 {print $5}' | sed 's/%//')

  if [ "$disk_usage" -gt "$ALERT_THRESHOLD_DISK" ]; then
    alert "Disk usage: ${disk_usage}% (threshold: ${ALERT_THRESHOLD_DISK}%)"
    return 1
  fi
  log "✓ Disk usage: ${disk_usage}%"
}

# ===== MEMORY CHECK =====
memory_check() {
  memory_usage=$(free | awk '/^Mem:/ {printf "%.0f", ($3/$2)*100}')

  if [ "$memory_usage" -gt "$ALERT_THRESHOLD_MEMORY" ]; then
    alert "Memory usage: ${memory_usage}% (threshold: ${ALERT_THRESHOLD_MEMORY}%)"
    return 1
  fi
  log "✓ Memory usage: ${memory_usage}%"
}

# ===== LOG ERROR CHECK =====
log_error_check() {
  recent_errors=$(tail -100 /var/log/cili/cili-error.log 2>/dev/null | grep -c "Exception\|Error" || echo "0")

  if [ "$recent_errors" -gt 5 ]; then
    alert "Recent errors detected: $recent_errors in last 100 lines"
    tail -5 /var/log/cili/cili-error.log >> "$LOGFILE"
    return 1
  fi
  log "✓ Error log: $recent_errors errors in recent logs"
}

# ===== FILE CLEANUP =====
cleanup_old_uploads() {
  # Alte Upload-Sessions (>24h) löschen
  find /var/cili/data/uploads -type d -mtime +1 -exec rm -rf {} \; 2>/dev/null || true
  log "✓ Cleanup: removed old upload sessions"
}

# ===== MAIN =====
log "========== HEALTH CHECK START =========="

failed=0

tomcat_status || ((failed++))
mysql_status || ((failed++))
app_health || ((failed++))
disk_check || ((failed++))
memory_check || ((failed++))
log_error_check || ((failed++))
cleanup_old_uploads

if [ $failed -eq 0 ]; then
  log "========== ALL CHECKS PASSED =========="
else
  log "========== $failed CHECKS FAILED =========="
fi

log ""
