#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."
source scripts/evidence/promotions/lib.sh
check_requirements

SCENARIO="01-create-delete-draft"
mkdir -p "$EVDIR/$SCENARIO"

ADMIN_TOKEN="$(login "$ADMIN_USERNAME" "$ADMIN_PASSWORD" "$EVDIR/$SCENARIO/01-admin-login.json")"
RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
PROMO_ID="$(create_promotion "$ADMIN_TOKEN" "DEL_TEST_$RUN_ID" "Delete draft scenario $RUN_ID" "$EVDIR/$SCENARIO/02-create.txt")"

api_json GET "/v1/promotions" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/03-list-after-create.txt"
json_body "$EVDIR/$SCENARIO/03-list-after-create.txt" | jq --arg id "$PROMO_ID" 'map(select(.id == $id)) | .[0]'

api_json DELETE "/v1/promotions/$PROMO_ID" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/04-delete-draft.txt"
assert_status "$EVDIR/$SCENARIO/04-delete-draft.txt" 204

api_json GET "/v1/promotions" "$ADMIN_TOKEN" "$EVDIR/$SCENARIO/05-list-after-delete.txt"
json_body "$EVDIR/$SCENARIO/05-list-after-delete.txt" \
  | jq --arg id "$PROMO_ID" '{deleted: (map(select(.id == $id)) | length == 0), id: $id}' \
  | tee "$EVDIR/$SCENARIO/06-delete-assertion.json"

if [ "$(jq -r '.deleted' "$EVDIR/$SCENARIO/06-delete-assertion.json")" != "true" ]; then
  echo "Promotion was not deleted: $PROMO_ID" >&2
  exit 1
fi

write_result "$SCENARIO" "PASS" "DRAFT promotion create and delete succeeded."
echo "EVDIR=$EVDIR"
echo "PROMO_ID=$PROMO_ID"
