#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."
source scripts/evidence/promotions/lib.sh
check_requirements

SCENARIO="00-smoke-list"
mkdir -p "$EVDIR/$SCENARIO"

ADMIN_TOKEN="$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$EVDIR/$SCENARIO/01-admin-login.json")"

api_json GET "/actuator/health" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/02-health.txt"
assert_status "$EVDIR/$SCENARIO/02-health.txt" 200
json_body "$EVDIR/$SCENARIO/02-health.txt" | jq .

api_json GET "/v1/promotions" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/03-list-promotions.txt"
assert_status "$EVDIR/$SCENARIO/03-list-promotions.txt" 200
json_body "$EVDIR/$SCENARIO/03-list-promotions.txt" | jq .

write_result "$SCENARIO" "PASS" "Login, health, and promotion listing succeeded."
echo "EVDIR=$EVDIR"
