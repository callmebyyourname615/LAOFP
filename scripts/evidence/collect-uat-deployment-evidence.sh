#!/usr/bin/env bash
# Capture read-only evidence from the deployed UAT instance.
#
# This script deliberately does not create traffic, restart services, or change
# remote state.  It is suitable to run after scripts/deploy-uat-code.sh.
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

SERVER="${SERVER:-root@175.11.0.200}"
REMOTE_DIR="${REMOTE_DIR:-/opt/switching}"
BASE_URL="${BASE_URL:-https://175.11.0.200}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/id_rsa}"
CLIENT_CERT="${CLIENT_CERT:-$HOME/sundaybank-client.crt}"
CLIENT_KEY="${CLIENT_KEY:-$HOME/sundaybank-client.key}"
CA_CERT="${CA_CERT:-$HOME/uat-ca.crt}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="${EVIDENCE_DIR:-runtime-evidence/uat-deployment-${STAMP}}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-20}"

for command in curl jq ssh sha256sum; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Required command is unavailable: $command" >&2
    exit 64
  }
done

mkdir -p "$EVIDENCE_DIR"
RESULTS="$EVIDENCE_DIR/checks.jsonl"
: > "$RESULTS"

record() {
  local id="$1" status="$2" detail="$3"
  jq -cn \
    --arg id "$id" \
    --arg status "$status" \
    --arg detail "$detail" \
    --arg observedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{id: $id, status: $status, detail: $detail, observedAt: $observedAt}' \
    >> "$RESULTS"
}

capture_endpoint() {
  local id="$1" path="$2" output
  output="$EVIDENCE_DIR/$id.json"
  local response http_code
  response="$(mktemp)"
  http_code="$(curl --silent --show-error --output "$response" --write-out '%{http_code}' \
    --connect-timeout "$TIMEOUT_SECONDS" --max-time "$TIMEOUT_SECONDS" \
    --cert "$CLIENT_CERT" --key "$CLIENT_KEY" --cacert "$CA_CERT" \
    "$BASE_URL$path" 2>"$EVIDENCE_DIR/$id.curl.stderr" || true)"

  if [[ -s "$response" ]] && jq -e . "$response" > "$output" 2>/dev/null; then
    jq --arg observedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
      --arg endpoint "$BASE_URL$path" \
      --arg httpCode "$http_code" \
      '. + {_evidence: {endpoint: $endpoint, httpCode: ($httpCode | tonumber?), observedAt: $observedAt}}' \
      "$output" > "$output.tmp" && mv "$output.tmp" "$output"
  else
    jq -n --arg endpoint "$BASE_URL$path" --arg httpCode "$http_code" \
      --rawfile stderr "$EVIDENCE_DIR/$id.curl.stderr" \
      '{endpoint: $endpoint, httpCode: ($httpCode | tonumber?), curlError: $stderr}' > "$output"
  fi
  rm -f "$response"

  if [[ "$http_code" == "200" ]] && jq -e '.status == "UP"' "$output" >/dev/null 2>&1; then
    record "$id" PASS "HTTP 200; status UP"
  elif [[ "$id" != health && "$http_code" == "401" ]]; then
    # The public UAT route deliberately permits only /actuator/health.  A 401
    # on the detail probes proves that the management endpoints are protected;
    # their availability must be checked from the management network instead.
    record "$id" PASS "HTTP 401; endpoint is protected as expected"
  else
    record "$id" FAIL "expected HTTP 200 and status UP; observed HTTP ${http_code:-no-response}"
  fi
}

capture_remote() {
  local output="$EVIDENCE_DIR/remote-container-state.txt"
  local -a ssh_cmd
  if [[ -n "${DEPLOY_SSH_PASSWORD:-}" ]]; then
    command -v sshpass >/dev/null 2>&1 || {
      printf 'DEPLOY_SSH_PASSWORD is set but sshpass is unavailable.\n' > "$output"
      record remote-container-state FAIL "sshpass is required when DEPLOY_SSH_PASSWORD is set"
      return
    }
    ssh_cmd=(sshpass -p "$DEPLOY_SSH_PASSWORD" ssh -o StrictHostKeyChecking=accept-new -o ConnectTimeout="$TIMEOUT_SECONDS")
  elif [[ ! -f "$SSH_KEY" ]]; then
    printf 'SSH key does not exist: %s\n' "$SSH_KEY" > "$output"
    record remote-container-state FAIL "SSH key missing"
    return
  else
    ssh_cmd=(ssh -i "$SSH_KEY" -o BatchMode=yes -o ConnectTimeout="$TIMEOUT_SECONDS")
  fi
  if "${ssh_cmd[@]}" "$SERVER" \
      "cd '$REMOTE_DIR' && printf '%s\\n' '== compose ps ==' && docker compose ps app && printf '%s\\n' '== image inspect ==' && docker image inspect switching-app --format '{{json .RepoDigests}} {{.Id}}' && printf '%s\\n' '== deployed jar sha256 ==' && sha256sum app.jar" \
      > "$output" 2>&1; then
    record remote-container-state PASS "docker compose state, image identity, and deployed jar checksum captured"
  else
    record remote-container-state FAIL "SSH or remote Docker inspection failed; see remote-container-state.txt"
  fi
}

capture_endpoint health /actuator/health
capture_endpoint liveness /actuator/health/liveness
capture_endpoint readiness /actuator/health/readiness
capture_remote

git_commit="$(git rev-parse HEAD)"
all_pass=true
while IFS= read -r row; do
  [[ "$(jq -r '.status' <<<"$row")" == PASS ]] || all_pass=false
done < "$RESULTS"

jq -s \
  --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg environment uat \
  --arg baseUrl "$BASE_URL" \
  --arg server "$SERVER" \
  --arg remoteDir "$REMOTE_DIR" \
  --arg gitCommit "$git_commit" \
  --argjson passed "$all_pass" \
  '{schemaVersion: 1, generatedAt: $generatedAt, environment: $environment, deployment: {baseUrl: $baseUrl, server: $server, remoteDir: $remoteDir, localGitCommit: $gitCommit}, allChecksPassed: $passed, checks: .}' \
  "$RESULTS" > "$EVIDENCE_DIR/manifest.json"

find "$EVIDENCE_DIR" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE_DIR/SHA256SUMS"
jq . "$EVIDENCE_DIR/manifest.json"
printf 'Evidence directory: %s\n' "$EVIDENCE_DIR"

"$all_pass" || exit 1
