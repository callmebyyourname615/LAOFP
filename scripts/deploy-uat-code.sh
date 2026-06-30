#!/usr/bin/env bash
set -euo pipefail

SERVER="${SERVER:-root@175.11.0.200}"
REMOTE_DIR="${REMOTE_DIR:-/opt/switching}"
BASE_URL="${BASE_URL:-https://175.11.0.200}"
APP_JAR="${APP_JAR:-target/switching-0.0.1-SNAPSHOT.jar}"
REMOTE_JAR="${REMOTE_JAR:-app.jar}"
DOCKERFILE="${DOCKERFILE:-Dockerfile.uat}"
IMAGE_NAME="${IMAGE_NAME:-switching-app}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-app}"
HEALTH_RETRIES="${HEALTH_RETRIES:-30}"
HEALTH_SLEEP_SECONDS="${HEALTH_SLEEP_SECONDS:-3}"

CLIENT_CERT="${CLIENT_CERT:-$HOME/sundaybank-client.crt}"
CLIENT_KEY="${CLIENT_KEY:-$HOME/sundaybank-client.key}"
CA_CERT="${CA_CERT:-$HOME/uat-ca.crt}"

log() {
  printf '\n==> %s\n' "$*"
}

require_file() {
  local path="$1"
  if [[ ! -f "$path" ]]; then
    echo "Missing required file: $path" >&2
    exit 1
  fi
}

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_DIR"

require_file "$CLIENT_CERT"
require_file "$CLIENT_KEY"
require_file "$CA_CERT"

log "Building application jar locally"
./mvnw -q -DskipTests clean package
require_file "$APP_JAR"

log "Backing up current remote jar"
ssh "$SERVER" "cd '$REMOTE_DIR' && if [ -f '$REMOTE_JAR' ]; then cp '$REMOTE_JAR' '$REMOTE_JAR.bak.'\$(date +%Y%m%d%H%M%S); fi"

log "Uploading jar to UAT"
scp "$APP_JAR" "$SERVER:$REMOTE_DIR/$REMOTE_JAR"

log "Building Docker image on UAT"
ssh "$SERVER" "cd '$REMOTE_DIR' && test -f '$DOCKERFILE'"
ssh "$SERVER" "cd '$REMOTE_DIR' && docker build --no-cache -f '$DOCKERFILE' -t '$IMAGE_NAME' ."

log "Restarting app service"
ssh "$SERVER" "cd '$REMOTE_DIR' && docker compose up -d --force-recreate '$COMPOSE_SERVICE'"

log "Waiting for health: $BASE_URL/actuator/health"
for attempt in $(seq 1 "$HEALTH_RETRIES"); do
  body="$(curl -k -s "$BASE_URL/actuator/health" \
    --cert "$CLIENT_CERT" \
    --key "$CLIENT_KEY" \
    --cacert "$CA_CERT" || true)"

  if printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    echo "$body"
    log "Deploy finished successfully"
    exit 0
  fi

  echo "health attempt $attempt/$HEALTH_RETRIES: ${body:-<no response>}"
  sleep "$HEALTH_SLEEP_SECONDS"
done

log "Health did not become UP. Recent app logs:"
ssh "$SERVER" "cd '$REMOTE_DIR' && docker compose ps '$COMPOSE_SERVICE' && docker compose logs --tail=250 '$COMPOSE_SERVICE'"
exit 1
