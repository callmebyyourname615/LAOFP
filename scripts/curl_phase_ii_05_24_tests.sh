#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
AUTH_HEADER="${AUTH_HEADER:?Set AUTH_HEADER, for example 'Authorization: Bearer ...'}"

curl_json() {
  curl --fail-with-body --silent --show-error \
    -H "$AUTH_HEADER" -H 'Content-Type: application/json' "$@"
}

cat <<'JSON' >/tmp/rtp-authorise.json
{
  "authorisationReference": "AUTH-PHASE-II-001",
  "mode": "FULL",
  "authorisedAmount": 1000.0000,
  "inquiryRef": "INQ-PHASE-II-001",
  "installments": []
}
JSON

echo "Set RTP_ID to exercise authorisation, decline and settlement endpoints."
if [[ -n "${RTP_ID:-}" ]]; then
  curl_json -X POST --data @/tmp/rtp-authorise.json \
    "$BASE_URL/v1/rtp/requests/$RTP_ID/authorise"
fi

echo "Phase II operator and partner endpoints require environment-specific payloads."
echo "See docs/api/phase-ii-services.md before running cross-border or delivery tests."
