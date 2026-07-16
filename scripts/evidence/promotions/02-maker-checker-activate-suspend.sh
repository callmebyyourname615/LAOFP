#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."
source scripts/evidence/promotions/lib.sh
check_requirements

SCENARIO="02-maker-checker-activate-suspend"
mkdir -p "$EVDIR/$SCENARIO"

ADMIN_TOKEN="$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$EVDIR/$SCENARIO/01-admin-login.json")"
CHECKER_TOKEN="$(login "$CHECKER_USERNAME" "$CHECKER_PASSWORD" "$EVDIR/$SCENARIO/02-checker-login.json")"

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
PROMO_ID="$(create_promotion "$ADMIN_TOKEN" "MCA_TEST_$RUN_ID" "Maker checker activate scenario $RUN_ID" "$EVDIR/$SCENARIO/03-create.txt")"

api_json POST "/v1/promotions/$PROMO_ID/activate" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/04-maker-activate-should-fail.txt"
assert_status "$EVDIR/$SCENARIO/04-maker-activate-should-fail.txt" 400
json_body "$EVDIR/$SCENARIO/04-maker-activate-should-fail.txt" | jq .

api_json POST "/v1/promotions/$PROMO_ID/activate" "$CHECKER_TOKEN" "$EVDIR/$SCENARIO/05-checker-activate.txt"
assert_status "$EVDIR/$SCENARIO/05-checker-activate.txt" 200
json_body "$EVDIR/$SCENARIO/05-checker-activate.txt" | jq .

api_json PATCH "/v1/promotions/$PROMO_ID/suspend" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/06-suspend-active.txt"
assert_status "$EVDIR/$SCENARIO/06-suspend-active.txt" 200
json_body "$EVDIR/$SCENARIO/06-suspend-active.txt" | jq .

FINAL_STATUS="$(json_body "$EVDIR/$SCENARIO/06-suspend-active.txt" | jq -r '.status')"
if [ "$FINAL_STATUS" != "SUSPENDED" ]; then
  echo "Expected final status SUSPENDED but got $FINAL_STATUS" >&2
  exit 1
fi

write_result "$SCENARIO" "PASS" "Maker-checker activation rule and suspend flow succeeded."
echo "EVDIR=$EVDIR"
echo "PROMO_ID=$PROMO_ID"
