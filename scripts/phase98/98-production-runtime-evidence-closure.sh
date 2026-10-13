#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

STAMP="${STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
BASE_URL="${BASE_URL:-https://175.11.0.200}"
STATEMENT_DATE="${STATEMENT_DATE:-$(date -u +%F)}"
EVIDENCE_DIR="${EVIDENCE_DIR:-runtime-evidence/phase98-production-runtime-${STAMP}}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/switching}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-app}"
DB_SERVICE="${DB_SERVICE:-db}"
DB_NAME="${DB_NAME:-${POSTGRES_DB:-switching}}"
DB_USER="${DB_USER:-${POSTGRES_USER:-switching}}"
SMOS_USERNAME="${SMOS_USERNAME:-admin}"

CLIENT_CERT="${CLIENT_CERT:-$HOME/sundaybank-client.crt}"
CLIENT_KEY="${CLIENT_KEY:-$HOME/sundaybank-client.key}"
CA_CERT="${CA_CERT:-$HOME/uat-ca.crt}"
ACCESS_TOKEN="${ACCESS_TOKEN:-}"
CAPTURE_DOCKER="${CAPTURE_DOCKER:-true}"
CAPTURE_DB="${CAPTURE_DB:-true}"
CURL_CONNECT_TIMEOUT_SECONDS="${CURL_CONNECT_TIMEOUT_SECONDS:-10}"
CURL_MAX_TIME_SECONDS="${CURL_MAX_TIME_SECONDS:-30}"

mkdir -p "$EVIDENCE_DIR"/{01-deployment-state,02-auth-security,03-phase97-rail-facade,04-audit-db,05-observability}
RESULTS="$EVIDENCE_DIR/checks.jsonl"
: > "$RESULTS"

for command in curl jq sha256sum; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 64; }
done

curl_tls_args=()
if [[ -f "$CLIENT_CERT" && -f "$CLIENT_KEY" && -f "$CA_CERT" ]]; then
  curl_tls_args=(--cert "$CLIENT_CERT" --key "$CLIENT_KEY" --cacert "$CA_CERT")
elif [[ "${REQUIRE_MTLS_FILES:-false}" == "true" ]]; then
  echo "Missing mTLS files. Set CLIENT_CERT, CLIENT_KEY and CA_CERT." >&2
  exit 64
else
  curl_tls_args=(-k)
fi

record() {
  jq -cn --arg id "$1" --arg status "$2" --arg detail "$3" \
    --arg observedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{id:$id,status:$status,detail:$detail,observedAt:$observedAt}' >> "$RESULTS"
}

capture_cmd() {
  local id="$1" output="$2"; shift 2
  set +e
  "$@" > "$output" 2>&1
  local rc=$?
  set -e
  if [[ "$rc" -eq 0 ]]; then
    record "$id" PASS "$output"
  else
    record "$id" WARN "$output exit=$rc"
  fi
}

request() {
  local id="$1" method="$2" path="$3" auth="${4:-}" expected="${5:-200}"
  local raw="$EVIDENCE_DIR/$id.raw" body="$EVIDENCE_DIR/$id.body" meta="$EVIDENCE_DIR/$id.meta.json" code
  local -a args=(--silent --show-error --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --max-time "$CURL_MAX_TIME_SECONDS" --output "$raw" --write-out '%{http_code}' -X "$method")
  args+=("${curl_tls_args[@]}")
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  : > "$raw"
  code="$(curl "${args[@]}" "$BASE_URL$path" || true)"
  mv "$raw" "$body"
  jq -n --arg endpoint "$BASE_URL$path" --arg method "$method" --arg httpCode "$code" \
    '{endpoint:$endpoint,method:$method,httpCode:($httpCode|tonumber?)}' > "$meta"
  if [[ "$code" == "$expected" ]]; then
    record "$id" PASS "HTTP $code $path"
  else
    record "$id" FAIL "expected HTTP $expected; observed HTTP ${code:-no-response}; $path"
  fi
  REQUEST_HTTP_CODE="$code"
  REQUEST_BODY="$body"
}

validate_contains() {
  local id="$1" file="$2" pattern="$3" detail="$4"
  if grep -Eq "$pattern" "$file"; then
    record "$id" PASS "$detail"
  else
    record "$id" FAIL "$detail missing pattern: $pattern"
  fi
}

git rev-parse HEAD > "$EVIDENCE_DIR/01-deployment-state/git-commit.txt" 2>/dev/null || true
git status --short > "$EVIDENCE_DIR/01-deployment-state/git-status-short.txt" 2>/dev/null || true

if [[ "$CAPTURE_DOCKER" == "true" ]] && command -v docker >/dev/null 2>&1; then
  capture_cmd docker-compose-ps "$EVIDENCE_DIR/01-deployment-state/docker-compose-ps.txt" \
    bash -lc "cd '$COMPOSE_DIR' && docker compose ps"
  capture_cmd docker-image-inspect "$EVIDENCE_DIR/01-deployment-state/docker-image-inspect.json" \
    bash -lc "docker inspect '$COMPOSE_SERVICE' 2>/dev/null || cd '$COMPOSE_DIR' && docker compose images '$COMPOSE_SERVICE'"
else
  record docker-compose-ps NOT_RUN "CAPTURE_DOCKER=false or docker unavailable"
fi

request "02-auth-security/actuator-health" GET "/actuator/health" "" 200
if jq -e '.status == "UP"' "$REQUEST_BODY" >/dev/null 2>&1; then
  record actuator-health-status PASS "actuator status UP"
else
  record actuator-health-status FAIL "actuator status is not UP"
fi

if [[ -z "$ACCESS_TOKEN" && -n "${SMOS_PASSWORD:-}" ]]; then
  login_raw="$EVIDENCE_DIR/02-auth-security/login.raw.json"
  login_sanitized="$EVIDENCE_DIR/02-auth-security/login.json"
  login_body="$(jq -n --arg username "$SMOS_USERNAME" --arg password "$SMOS_PASSWORD" '{username:$username,password:$password}')"
  : > "$login_raw"
  login_code="$(curl --silent --show-error --connect-timeout "$CURL_CONNECT_TIMEOUT_SECONDS" --max-time "$CURL_MAX_TIME_SECONDS" --output "$login_raw" --write-out '%{http_code}' \
    "${curl_tls_args[@]}" -H 'Content-Type: application/json' --data "$login_body" "$BASE_URL/api/auth/login" || true)"
  unset login_body SMOS_PASSWORD
  if jq -e . "$login_raw" >/dev/null 2>&1; then
    ACCESS_TOKEN="$(jq -r '.accessToken // empty' "$login_raw")"
    jq 'del(.accessToken,.refreshToken,.mfaToken)' "$login_raw" > "$login_sanitized"
  else
    cp "$login_raw" "$login_sanitized"
  fi
  rm -f "$login_raw"
  if [[ "$login_code" == 200 && -n "$ACCESS_TOKEN" ]]; then
    record smos-login PASS "HTTP 200; token issued and redacted"
  else
    record smos-login FAIL "expected HTTP 200 and token; observed HTTP ${login_code:-no-response}"
  fi
fi

if [[ -z "$ACCESS_TOKEN" ]]; then
  record protected-auth BLOCKED "Set ACCESS_TOKEN or SMOS_PASSWORD to run protected runtime checks"
else
  request "02-auth-security/operations-health" GET "/api/operations/health" "$ACCESS_TOKEN" 200
  if jq -e '.status == "UP"' "$REQUEST_BODY" >/dev/null 2>&1; then
    record operations-health-status PASS "operations health UP"
  else
    record operations-health-status WARN "operations health endpoint returned non-UP payload"
  fi

  request "03-phase97-rail-facade/itmx-settlementfile" GET "/crossborder/itmx/settlementfile?statementDate=$STATEMENT_DATE" "$ACCESS_TOKEN" 200
  validate_contains phase97-itmx-file "$REQUEST_BODY" '^HDR\|ITMX\|PROMPTPAY\|SETTLEMENT_FILE\|' "ITMX settlement file header maps to PROMPTPAY"
  validate_contains phase97-itmx-checksum "$REQUEST_BODY" '^CHK\|[a-f0-9]{64}$' "ITMX settlement file has checksum"

  request "03-phase97-rail-facade/napas-settlementreport" GET "/crossborder/napas/settlementreport?statementDate=$STATEMENT_DATE" "$ACCESS_TOKEN" 200
  validate_contains phase97-napas-report "$REQUEST_BODY" '^"SUMMARY","NAPAS","NAPAS","SETTLEMENT_REPORT"' "NAPAS report preserves source rail"

  request "03-phase97-rail-facade/upi-summary" GET "/crossborder/upi/summary?statementDate=$STATEMENT_DATE" "$ACCESS_TOKEN" 200
  validate_contains phase97-upi-summary "$REQUEST_BODY" '^"SUMMARY","UPI","UPI","SUMMARY_REPORT"' "UPI summary report generated"

  request "03-phase97-rail-facade/lfx-report" GET "/lfx/report?statementDate=$STATEMENT_DATE" "$ACCESS_TOKEN" 200
  validate_contains phase97-lfx-report "$REQUEST_BODY" '^"SUMMARY","LFX","LFX","REPORT"' "LFX guarded report generated"
fi

if [[ "$CAPTURE_DB" == "true" ]] && command -v docker >/dev/null 2>&1; then
  capture_cmd flyway-version "$EVIDENCE_DIR/04-audit-db/flyway-version.txt" \
    bash -lc "cd '$COMPOSE_DIR' && docker compose exec -T '$DB_SERVICE' psql -U '$DB_USER' -d '$DB_NAME' -c \"SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;\""
  capture_cmd phase97-audit-events "$EVIDENCE_DIR/04-audit-db/phase97-audit-events.txt" \
    bash -lc "cd '$COMPOSE_DIR' && docker compose exec -T '$DB_SERVICE' psql -U '$DB_USER' -d '$DB_NAME' -c \"SELECT event_type, reference_id, actor, created_at FROM audit_logs WHERE event_type = 'CROSS_BORDER_LEGACY_RAIL_FILE_EXPORTED' ORDER BY created_at DESC LIMIT 10;\""
else
  record flyway-version NOT_RUN "CAPTURE_DB=false or docker unavailable"
  record phase97-audit-events NOT_RUN "CAPTURE_DB=false or docker unavailable"
fi

if [[ "$CAPTURE_DOCKER" == "true" ]] && command -v docker >/dev/null 2>&1; then
  capture_cmd app-log-tail "$EVIDENCE_DIR/05-observability/app-log-tail.txt" \
    bash -lc "cd '$COMPOSE_DIR' && docker compose logs --tail=300 '$COMPOSE_SERVICE'"
fi

jq -s --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
  --arg baseUrl "$BASE_URL" --arg statementDate "$STATEMENT_DATE" \
  '{schemaVersion:1,phase:"98",title:"Production Runtime Evidence Closure",generatedAt:$generatedAt,baseUrl:$baseUrl,statementDate:$statementDate,checks:.}' \
  "$RESULTS" > "$EVIDENCE_DIR/manifest.json"

jq -s '
  def c($s): map(select(.status == $s)) | length;
  {
    scope:"Phase 98 runtime evidence closure",
    totalChecks:length,
    passed:c("PASS"),
    warnings:c("WARN"),
    failed:c("FAIL"),
    blocked:c("BLOCKED"),
    notRun:c("NOT_RUN")
  }
  | .scorePercent = (if .totalChecks == 0 then 0 else ((.passed * 100) / .totalChecks | floor) end)
  | .productionReady = (.failed == 0 and .blocked == 0 and .warnings == 0 and .notRun == 0)
  | .verdict = (if .productionReady then "READY_FOR_PRODUCTION_RUNTIME_GATE" else "NOT_READY_FOR_PRODUCTION" end)
' "$RESULTS" > "$EVIDENCE_DIR/score.json"

{
  echo "# Phase 98 Production Runtime Evidence Closure"
  echo
  echo "Generated: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "Base URL: $BASE_URL"
  echo "Statement date: $STATEMENT_DATE"
  echo
  echo "## Score"
  echo
  jq . "$EVIDENCE_DIR/score.json"
  echo
  echo "## Checks"
  echo
  jq -s -r '"| Check | Status | Detail |\n| --- | --- | --- |", (.[] | "| \(.id) | \(.status) | \(.detail | gsub("\\|"; "/")) |")' "$RESULTS"
} > "$EVIDENCE_DIR/SUMMARY.md"

find "$EVIDENCE_DIR" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE_DIR/SHA256SUMS"

jq . "$EVIDENCE_DIR/score.json"
echo "Evidence directory: $EVIDENCE_DIR"

! jq -s -e 'any(.[]; .status == "FAIL" or .status == "BLOCKED")' "$RESULTS" >/dev/null
