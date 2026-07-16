#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."
source scripts/evidence/promotions/lib.sh
check_requirements

SCENARIO="03-delete-non-draft-should-fail"
mkdir -p "$EVDIR/$SCENARIO"

ADMIN_TOKEN="$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$EVDIR/$SCENARIO/01-admin-login.json")"
CHECKER_TOKEN="$(login "$CHECKER_USERNAME" "$CHECKER_PASSWORD" "$EVDIR/$SCENARIO/02-checker-login.json")"

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
PROMO_ID="$(create_promotion "$ADMIN_TOKEN" "NO_DEL_$RUN_ID" "Delete non draft scenario $RUN_ID" "$EVDIR/$SCENARIO/03-create.txt")"

api_json POST "/v1/promotions/$PROMO_ID/activate" "$CHECKER_TOKEN" "$EVDIR/$SCENARIO/04-activate.txt"
assert_status "$EVDIR/$SCENARIO/04-activate.txt" 200

api_json DELETE "/v1/promotions/$PROMO_ID" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/05-delete-active-should-fail.txt"
assert_status "$EVDIR/$SCENARIO/05-delete-active-should-fail.txt" 400
json_body "$EVDIR/$SCENARIO/05-delete-active-should-fail.txt" | jq .

api_json PATCH "/v1/promotions/$PROMO_ID/suspend" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/06-cleanup-suspend.txt"
assert_status "$EVDIR/$SCENARIO/06-cleanup-suspend.txt" 200

write_result "$SCENARIO" "PASS" "ACTIVE promotion delete was rejected and cleanup suspend succeeded."
echo "EVDIR=$EVDIR"
echo "PROMO_ID=$PROMO_ID"
