#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TOKEN="${TOKEN:-}"
CORRELATION_ID="${CORRELATION_ID:-RTP-CURL-$(date +%s)}"

AUTH=()
if [[ -n "$TOKEN" ]]; then
  AUTH=(-H "Authorization: Bearer $TOKEN")
fi

response_file="$(mktemp)"
trap 'rm -f "$response_file"' EXIT

curl --fail-with-body --silent --show-error \
  "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/v1/rtp/requests" \
  -d "{\"requestCorrelationId\":\"$CORRELATION_ID\",\"payeeParticipantId\":\"BANK_A\",\"payerParticipantId\":\"BANK_B\",\"payeeAccount\":\"010100000001\",\"requestedAmount\":150000.00,\"currency\":\"LAK\",\"description\":\"Curl verification\"}" \
  | tee "$response_file"

request_id="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["id"])' "$response_file")"

curl --fail-with-body --silent --show-error "${AUTH[@]}" \
  "$BASE_URL/v1/rtp/requests/$request_id"

curl --fail-with-body --silent --show-error "${AUTH[@]}" \
  -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/v1/rtp/requests/$request_id/cancel" \
  -d '{"reason":"Curl verification complete"}'
