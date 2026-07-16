#!/usr/bin/env bash
set -euo pipefail

TARGET="${TARGET:-https://175.11.0.200}"
CLIENT_CERT="${CLIENT_CERT:-$HOME/sundaybank-client.crt}"
CLIENT_KEY="${CLIENT_KEY:-$HOME/sundaybank-client.key}"
CA_CERT="${CA_CERT:-$HOME/uat-ca.crt}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-SundayAdmin@2026Strong!2323}"
CHECKER_USERNAME="${CHECKER_USERNAME:-checker_prod_062628}"
CHECKER_PASSWORD="${CHECKER_PASSWORD:-Checker@2026Strong!2323}"

STAMP="${STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
EVDIR="${EVDIR:-runtime-evidence/promotion-scenarios-$STAMP}"

mkdir -p "$EVDIR"

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required tool: $1" >&2
    exit 1
  fi
}

check_requirements() {
  require_tool curl
  require_tool jq
  for file in "$CLIENT_CERT" "$CLIENT_KEY" "$CA_CERT"; do
    if [ ! -f "$file" ]; then
      echo "Missing mTLS file: $file" >&2
      exit 1
    fi
  done
}

curl_base() {
  curl -k -sS \
    --cert "$CLIENT_CERT" \
    --key "$CLIENT_KEY" \
    --cacert "$CA_CERT" \
    "$@"
}

login() {
  local username="$1" password="$2" outfile="$3"
  curl_base "$TARGET/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$username\",\"password\":\"$password\"}" \
    | tee "$outfile" \
    | jq -r '.accessToken'
}

api_json() {
  local method="$1" path="$2" token="$3" outfile="$4"
  shift 4
  curl_base -w '\nHTTP_STATUS:%{http_code}\n' \
    -X "$method" "$TARGET$path" \
    -H "Authorization: Bearer $token" \
    -H "Content-Type: application/json" \
    "$@" \
    | tee "$outfile"
}

json_body() {
  sed '/^HTTP_STATUS:/d' "$1"
}

http_status() {
  awk -F: '/^HTTP_STATUS:/{print $2}' "$1"
}

assert_status() {
  local file="$1" expected="$2"
  local actual
  actual="$(http_status "$file")"
  if [ "$actual" != "$expected" ]; then
    echo "Expected HTTP $expected but got $actual for $file" >&2
    json_body "$file" | jq . >&2 || true
    exit 1
  fi
}

new_promotion_payload() {
  local code="$1" name="$2" outfile="$3"
  cat > "$outfile" <<EOF
{
  "code": "$code",
  "name": "$name",
  "type": "WAIVER",
  "priority": 100,
  "combinable": false,
  "funderParticipantId": "SUNDAYBANK",
  "currency": "LAK",
  "budgetCap": 1000000,
  "discountValue": 1000,
  "discountMode": "FIXED",
  "startsAt": "$(date -u +%Y-%m-%dT%H:%M:%SZ)",
  "endsAt": "$(date -u -v+30d +%Y-%m-%dT%H:%M:%SZ)",
  "eligibilityRules": [
    {
      "scope": "ALL",
      "channel": "TRANSFER"
    }
  ]
}
EOF
}

create_promotion() {
  local token="$1" code="$2" name="$3" outfile="$4"
  local request_file="${outfile%.txt}-request.json"
  new_promotion_payload "$code" "$name" "$request_file"
  api_json POST "/v1/promotions" "$token" "$outfile" -d @"$request_file"
  assert_status "$outfile" 201
  json_body "$outfile" | jq -r '.id'
}

write_result() {
  local scenario="$1" status="$2" note="$3"
  cat > "$EVDIR/$scenario-RESULT.md" <<EOF
# $scenario

Status: $status

$note
EOF
}
