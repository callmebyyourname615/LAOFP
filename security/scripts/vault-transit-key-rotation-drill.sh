#!/usr/bin/env bash
set -euo pipefail
: "${VAULT_ADDR:?VAULT_ADDR is required}"
: "${VAULT_TOKEN:?Operator-scoped VAULT_TOKEN is required for the drill}"
: "${VAULT_TRANSIT_MOUNT:=transit}"
: "${VAULT_TRANSIT_KEY:=switching-webhook}"
: "${BASE_URL:?BASE_URL is required}"
: "${API_KEY:?API_KEY is required}"
: "${WEBHOOK_ID:?WEBHOOK_ID is required}"
[[ "$VAULT_ADDR" == https://* ]] || { echo 'Vault must use HTTPS' >&2; exit 2; }
before=$(vault read -format=json "$VAULT_TRANSIT_MOUNT/keys/$VAULT_TRANSIT_KEY" | jq -r '.data.latest_version')
vault write -f "$VAULT_TRANSIT_MOUNT/keys/$VAULT_TRANSIT_KEY/rotate" >/dev/null
after=$(vault read -format=json "$VAULT_TRANSIT_MOUNT/keys/$VAULT_TRANSIT_KEY" | jq -r '.data.latest_version')
[[ "$after" -gt "$before" ]] || { echo 'Vault key version did not advance' >&2; exit 1; }
curl --fail-with-body -sS -X POST "$BASE_URL/v1/webhooks/$WEBHOOK_ID/test" -H "X-API-Key: $API_KEY" >/dev/null
printf '{"beforeVersion":%s,"afterVersion":%s,"webhookTest":"QUEUED","secretMaterialLogged":false}
' "$before" "$after"
