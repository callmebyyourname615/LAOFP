#!/usr/bin/env bash
# Run a minimal authenticated UAT smoke test and store sanitized evidence.
# The password, access token, refresh token, and MFA token are never persisted.
set -Eeuo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$PROJECT_DIR"

: "${SMOS_USERNAME:=admin}"
: "${SMOS_PASSWORD:?SMOS_PASSWORD is required}"
BASE_URL="${BASE_URL:-https://175.11.0.200}"
CLIENT_CERT="${CLIENT_CERT:-$HOME/sundaybank-client.crt}"
CLIENT_KEY="${CLIENT_KEY:-$HOME/sundaybank-client.key}"
CA_CERT="${CA_CERT:-$HOME/uat-ca.crt}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="${EVIDENCE_DIR:-runtime-evidence/uat-authenticated-smoke-${STAMP}}"

for command in curl jq sha256sum; do
  command -v "$command" >/dev/null 2>&1 || { echo "Missing required command: $command" >&2; exit 64; }
done
for file in "$CLIENT_CERT" "$CLIENT_KEY" "$CA_CERT"; do
  [[ -f "$file" ]] || { echo "Missing mTLS file: $file" >&2; exit 64; }
done

mkdir -p "$EVIDENCE_DIR"
RESULTS="$EVIDENCE_DIR/checks.jsonl"
: > "$RESULTS"

record() {
  jq -cn --arg id "$1" --arg status "$2" --arg detail "$3" \
    --arg observedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" \
    '{id:$id, status:$status, detail:$detail, observedAt:$observedAt}' >> "$RESULTS"
}

request() {
  local id="$1" method="$2" path="$3" auth="${4:-}" body="${5:-}"
  local raw="$EVIDENCE_DIR/$id.raw.json" code sanitized
  REQUEST_ACCESS_TOKEN=""
  local -a args=(--silent --show-error --output "$raw" --write-out '%{http_code}'
    --cert "$CLIENT_CERT" --key "$CLIENT_KEY" --cacert "$CA_CERT"
    -X "$method" -H 'Content-Type: application/json')
  [[ -n "$auth" ]] && args+=(-H "Authorization: Bearer $auth")
  [[ -n "$body" ]] && args+=(--data "$body")
  code="$(curl "${args[@]}" "$BASE_URL$path" || true)"
  REQUEST_HTTP_CODE="$code"
  if [[ "$id" == login ]] && jq -e . "$raw" >/dev/null 2>&1; then
    REQUEST_ACCESS_TOKEN="$(jq -r '.accessToken // empty' "$raw")"
  fi
  sanitized="$EVIDENCE_DIR/$id.json"
  if jq -e . "$raw" > /dev/null 2>&1; then
    jq --arg endpoint "$BASE_URL$path" --arg method "$method" --arg httpCode "$code" \
      'if type == "object" then
         del(.accessToken, .refreshToken, .mfaToken) + {_evidence:{endpoint:$endpoint, method:$method, httpCode:($httpCode|tonumber?)}}
       else
         {data: ., _evidence:{endpoint:$endpoint, method:$method, httpCode:($httpCode|tonumber?)}}
       end' \
      "$raw" > "$sanitized"
  else
    jq -n --arg endpoint "$BASE_URL$path" --arg method "$method" --arg httpCode "$code" \
      '{endpoint:$endpoint, method:$method, httpCode:($httpCode|tonumber?), responseWasJson:false}' > "$sanitized"
  fi
  rm -f "$raw"
}

login_body="$(jq -n --arg username "$SMOS_USERNAME" --arg password "$SMOS_PASSWORD" '{username:$username,password:$password}')"
request login POST /api/auth/login '' "$login_body"
login_code="$REQUEST_HTTP_CODE"
token="$REQUEST_ACCESS_TOKEN"
unset login_body

if [[ "$login_code" != 200 ]]; then
  record login FAIL "expected HTTP 200; observed HTTP ${login_code:-no-response}"
elif jq -e '.mfaRequired == true' "$EVIDENCE_DIR/login.json" >/dev/null; then
  record login BLOCKED "MFA is required; rerun the MFA flow before authenticated endpoint tests"
  token=""
else
  if [[ -n "$token" ]]; then record login PASS "HTTP 200; access token issued and redacted"; else record login FAIL "HTTP 200 but no access token was issued"; fi
fi
unset SMOS_PASSWORD

if [[ -n "${token:-}" ]]; then
  request sessions GET /api/auth/sessions "$token"
  sessions_code="$REQUEST_HTTP_CODE"
  if [[ "$sessions_code" == 200 ]]; then record sessions PASS "HTTP 200; authenticated session inventory returned"; else record sessions FAIL "expected HTTP 200; observed HTTP ${sessions_code:-no-response}"; fi

  request operations-health GET /api/operations/health "$token"
  ops_code="$REQUEST_HTTP_CODE"
  if [[ "$ops_code" == 200 ]]; then
    ops_status="$(jq -r '.status // "UNKNOWN"' "$EVIDENCE_DIR/operations-health.json")"
    [[ "$ops_status" == UP ]] && record operations-health PASS "HTTP 200; status UP" || record operations-health WARN "HTTP 200; status $ops_status"
  else
    record operations-health FAIL "expected HTTP 200; observed HTTP ${ops_code:-no-response}"
  fi
else
  record sessions NOT_RUN "login did not issue an access token"
  record operations-health NOT_RUN "login did not issue an access token"
fi
unset token

request actuator-health GET /actuator/health
health_code="$REQUEST_HTTP_CODE"
if [[ "$health_code" == 200 ]] && jq -e '.status == "UP"' "$EVIDENCE_DIR/actuator-health.json" >/dev/null; then
  record actuator-health PASS "HTTP 200; status UP"
else
  record actuator-health FAIL "expected HTTP 200 and status UP; observed HTTP ${health_code:-no-response}"
fi

jq -s --arg generatedAt "$(date -u +%Y-%m-%dT%H:%M:%SZ)" --arg baseUrl "$BASE_URL" \
  '{schemaVersion:1, environment:"uat", generatedAt:$generatedAt, baseUrl:$baseUrl, checks:.}' "$RESULTS" > "$EVIDENCE_DIR/manifest.json"
jq -s '
  def count_status($status): map(select(.status == $status)) | length;
  . as $checks |
  {scope: "authenticated UAT functional smoke only; performance, resilience, migration, and full regression controls are excluded",
   totalChecks: length,
   passed: count_status("PASS"),
   warnings: count_status("WARN"),
   failed: count_status("FAIL"),
   blocked: count_status("BLOCKED"),
   notRun: count_status("NOT_RUN")} |
  .functionalScorePercent = (if .totalChecks == 0 then 0 else ((.passed * 100) / .totalChecks | floor) end) |
  .productionReady = (.warnings == 0 and .failed == 0 and .blocked == 0 and .notRun == 0) |
  .verdict = (if .productionReady then "READY_FOR_NEXT_GATE" else "NOT_READY_FOR_PRODUCTION" end)
' "$RESULTS" > "$EVIDENCE_DIR/score.json"
find "$EVIDENCE_DIR" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE_DIR/SHA256SUMS"
jq . "$EVIDENCE_DIR/manifest.json"
printf '\nScore:\n'
jq . "$EVIDENCE_DIR/score.json"
printf 'Evidence directory: %s\n' "$EVIDENCE_DIR"

! jq -s -e 'any(.[]; .status == "FAIL" or .status == "BLOCKED")' "$RESULTS" >/dev/null
