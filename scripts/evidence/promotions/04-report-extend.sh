#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."
source scripts/evidence/promotions/lib.sh
check_requirements

SCENARIO="04-report-extend"
mkdir -p "$EVDIR/$SCENARIO"

ADMIN_TOKEN="$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$EVDIR/$SCENARIO/01-admin-login.json")"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
PROMO_ID="$(create_promotion "$ADMIN_TOKEN" "EXT_TEST_$RUN_ID" "Report extend scenario $RUN_ID" "$EVDIR/$SCENARIO/02-create.txt")"

NEXT_ENDS_AT="$(date -u -v+45d +%Y-%m-%dT%H:%M:%SZ)"
api_json PATCH "/v1/promotions/$PROMO_ID/extend" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/03-extend.txt" \
  -d "{\"endsAt\":\"$NEXT_ENDS_AT\"}"
assert_status "$EVDIR/$SCENARIO/03-extend.txt" 200
json_body "$EVDIR/$SCENARIO/03-extend.txt" | jq .

api_json GET "/v1/promotions/$PROMO_ID/report" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/04-report.txt"
assert_status "$EVDIR/$SCENARIO/04-report.txt" 200
json_body "$EVDIR/$SCENARIO/04-report.txt" | jq .

api_json DELETE "/v1/promotions/$PROMO_ID" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/05-cleanup-delete-draft.txt"
assert_status "$EVDIR/$SCENARIO/05-cleanup-delete-draft.txt" 204

write_result "$SCENARIO" "PASS" "Draft promotion extend, report, and cleanup delete succeeded."
echo "EVDIR=$EVDIR"
echo "PROMO_ID=$PROMO_ID"
