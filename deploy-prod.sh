#!/bin/bash
# Deployment-Automatisierung für Ubuntu 24.04 LTS

set -e

CONTAINER_IP="${1:-}"
CONTAINER_USER="${2:-root}"
DB_PASSWORD="${3:-}"

if [ -z "$CONTAINER_IP" ] || [ -z "$DB_PASSWORD" ]; then
  echo "Usage: $0 <container-ip> <container-user> <db-password>"
  echo "Example: $0 192.168.1.100 root 'MySecurePassword123'"
  exit 1
fi

echo "===== CILI Production Deployment ====="
echo "Container: $CONTAINER_IP"
echo "User: $CONTAINER_USER"

# 1. WAR bauen
echo ""
echo "📦 Building WAR..."
./mvnw.cmd clean package -DskipTests -q

# 2. Auf Container kopieren
echo "📤 Uploading to container..."
scp target/cili-app.war "${CONTAINER_USER}@${CONTAINER_IP}:/opt/cili/" 2>/dev/null

# 3. Configuration updaten
echo "⚙️  Configuring environment..."
ssh "${CONTAINER_USER}@${CONTAINER_IP}" bash <<EOF
  set -e

  # Permissions
  chown tomcat:tomcat /opt/cili/cili-app.war
  chmod 640 /opt/cili/cili-app.war

  # DB Password in Environment
  sed -i "s/DB_PASSWORD=.*/DB_PASSWORD=$DB_PASSWORD/" /opt/cili/cili.env

  # Restart Tomcat
  echo "🔄 Restarting Tomcat..."
  systemctl restart tomcat11
  sleep 5

  # Warte auf Startup
  echo "⏳ Waiting for application startup..."
  for i in {1..30}; do
    if curl -s http://localhost:8080/ > /dev/null 2>&1; then
      echo "✓ Application is ready"
      break
    fi
    echo "   Attempt \$i/30..."
    sleep 1
  done

  # Health Check
  echo ""
  echo "🏥 Health Check:"
  curl -s http://localhost/api/actuator/health | jq .

  # Logs
  echo ""
  echo "📋 Recent logs:"
  tail -10 /var/log/cili/cili.log
EOF

echo ""
echo "✓ Deployment complete!"
echo ""
echo "Available at:"
echo "  http://$CONTAINER_IP/"
echo "  http://your-domain.com/ (if DNS configured)"
echo ""
echo "Check health:"
echo "  curl http://$CONTAINER_IP/api/actuator/health"
