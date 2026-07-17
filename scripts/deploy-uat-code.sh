#!/usr/bin/env bash
set -euo pipefail

SERVER="${SERVER:-root@175.11.0.200}"
REMOTE_DIR="${REMOTE_DIR:-/opt/switching}"
BASE_URL="${BASE_URL:-https://175.11.0.200}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"
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
ISSUER_CA_CERT="${ISSUER_CA_CERT:-$HOME/issuer-ca.crt}"
MTLS_CONFIG="${MTLS_CONFIG:-nginx/mtls.conf}"
REMOTE_CERT_DIR="${REMOTE_CERT_DIR:-$REMOTE_DIR/certs}"
REMOTE_NGINX_CONFIG="${REMOTE_NGINX_CONFIG:-$REMOTE_DIR/nginx/mtls.conf}"
MTLS_CONTAINER="${MTLS_CONTAINER:-switching-nginx-mtls}"
DEPLOY_SYNC_CONFIG="${DEPLOY_SYNC_CONFIG:-false}"
DEPLOY_SYNC_ENV="${DEPLOY_SYNC_ENV:-false}"

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
require_file "$ISSUER_CA_CERT"
require_file "$MTLS_CONFIG"

if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
  if ! command -v sshpass >/dev/null 2>&1; then
    echo "DEPLOY_SSH_PASSWORD is set, but sshpass is not installed." >&2
    echo "Install it first, or unset DEPLOY_SSH_PASSWORD and use SSH_KEY instead." >&2
    exit 1
  fi
  # `sshpass -e` avoids exposing the password in the process command line and
  # is more reliable than `-p` with non-interactive macOS shell sessions.
  export SSHPASS="$DEPLOY_SSH_PASSWORD"
  SSH_CMD=(sshpass -e ssh -o StrictHostKeyChecking=accept-new)
  SCP_CMD=(sshpass -e scp -o StrictHostKeyChecking=accept-new)
else
  require_file "$SSH_KEY"
  SSH_CMD=(ssh -i "$SSH_KEY")
  SCP_CMD=(scp -i "$SSH_KEY")
fi

log "Building application jar locally"
./mvnw -q -DskipTests clean package
require_file "$APP_JAR"

log "Backing up current remote jar"
"${SSH_CMD[@]}" "$SERVER" "cd '$REMOTE_DIR' && if [ -f '$REMOTE_JAR' ]; then cp '$REMOTE_JAR' '$REMOTE_JAR.bak.'\$(date +%Y%m%d%H%M%S); fi"

log "Uploading jar to UAT"
LOCAL_JAR_SHA256="$(shasum -a 256 "$APP_JAR" | awk '{print $1}')"
REMOTE_JAR_TMP="$REMOTE_DIR/.${REMOTE_JAR}.upload.$$"
"${SCP_CMD[@]}" "$APP_JAR" "$SERVER:$REMOTE_JAR_TMP"

REMOTE_JAR_SHA256="$("${SSH_CMD[@]}" "$SERVER" "sha256sum '$REMOTE_JAR_TMP' | awk '{print \$1}'")"
if [[ "$REMOTE_JAR_SHA256" != "$LOCAL_JAR_SHA256" ]]; then
  "${SSH_CMD[@]}" "$SERVER" "rm -f '$REMOTE_JAR_TMP'"
  echo "Uploaded jar checksum does not match local build; refusing to deploy." >&2
  exit 1
fi
"${SSH_CMD[@]}" "$SERVER" "mv '$REMOTE_JAR_TMP' '$REMOTE_DIR/$REMOTE_JAR'"

if [[ "$DEPLOY_SYNC_CONFIG" == "true" ]]; then
  log "Synchronizing deployment configuration"
  require_file "docker-compose.yml"
  "${SCP_CMD[@]}" "docker-compose.yml" "$SERVER:$REMOTE_DIR/docker-compose.yml"
fi

if [[ "$DEPLOY_SYNC_ENV" == "true" ]]; then
  log "Synchronizing environment file"
  require_file ".env"
  "${SCP_CMD[@]}" ".env" "$SERVER:$REMOTE_DIR/.env"
fi

log "Updating mTLS trust bundle and edge configuration"
"${SCP_CMD[@]}" "$ISSUER_CA_CERT" "$SERVER:$REMOTE_CERT_DIR/issuer-ca.crt"
"${SCP_CMD[@]}" "$MTLS_CONFIG" "$SERVER:$REMOTE_NGINX_CONFIG"
"${SSH_CMD[@]}" "$SERVER" "cat '$REMOTE_CERT_DIR/uat-ca.crt' '$REMOTE_CERT_DIR/issuer-ca.crt' > '$REMOTE_CERT_DIR/uat-client-ca-bundle.crt' && chmod 644 '$REMOTE_CERT_DIR/uat-client-ca-bundle.crt' && docker exec '$MTLS_CONTAINER' nginx -t && docker exec '$MTLS_CONTAINER' nginx -s reload"

log "Building Docker image on UAT"
"${SSH_CMD[@]}" "$SERVER" "cd '$REMOTE_DIR' && test -f '$DOCKERFILE'"
"${SSH_CMD[@]}" "$SERVER" "cd '$REMOTE_DIR' && docker build --no-cache -f '$DOCKERFILE' -t '$IMAGE_NAME' ."

log "Restarting app service"
"${SSH_CMD[@]}" "$SERVER" "cd '$REMOTE_DIR' && docker compose up -d --force-recreate '$COMPOSE_SERVICE'"

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
"${SSH_CMD[@]}" "$SERVER" "cd '$REMOTE_DIR' && docker compose ps '$COMPOSE_SERVICE' && docker compose logs --tail=250 '$COMPOSE_SERVICE'"
exit 1
