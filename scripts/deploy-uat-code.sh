#!/usr/bin/env bash
set -euo pipefail

SERVER="${SERVER:-root@175.11.0.200}"
REMOTE_DIR="${REMOTE_DIR:-/opt/switching}"

echo "==> Packaging source for UAT deploy"
./mvnw -q -DskipTests package

echo "==> Backing up remote source metadata"
ssh "$SERVER" "cd '$REMOTE_DIR' && tar -czf /opt/switching-code-backup-\$(date +%Y%m%d%H%M%S).tgz src pom.xml Dockerfile mvnw .mvn 2>/dev/null || true"

echo "==> Syncing application code only"
rsync -az --delete \
  src pom.xml Dockerfile mvnw .mvn \
  "$SERVER:$REMOTE_DIR/"

echo "==> Building and restarting app container"
ssh "$SERVER" "cd '$REMOTE_DIR' && docker compose build app && docker compose up -d --force-recreate app"

echo "==> Waiting for app health"
sleep 8
curl -k -s "https://175.11.0.200/actuator/health" \
  --cert ~/sundaybank-client.crt \
  --key ~/sundaybank-client.key \
  --cacert ~/uat-ca.crt
echo

echo "==> Done"
