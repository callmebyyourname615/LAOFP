#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81F
if phase81_full; then
  [[ "${PHASE81_ALLOW_PROMOTION_UAT:-false}" == true && -n "${PHASE81_PROMOTION_UAT_COMMAND:-}" ]] || { phase81_emit BLOCKED 'promotion UAT confirmation/command missing'; exit 1; }
  bash -lc "$PHASE81_PROMOTION_UAT_COMMAND" > "$PHASE81_EVIDENCE_ROOT/artifacts/promotion-uat.log" 2>&1
  [[ -n "${DB_URL:-}" && -n "${DB_USERNAME:-}" ]] || { phase81_emit BLOCKED 'database settings missing'; exit 1; }
  PGPASSWORD="${DB_PASSWORD:-}" psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -f sql/phase81/promotion-funder-reconciliation.sql > "$PHASE81_EVIDENCE_ROOT/artifacts/promotion-reconciliation.txt"
  phase81_emit PASS 'promotion UAT and funder reconciliation completed'
else phase81_emit PREPARED 'promotion UAT executor ready; feature remains disabled'; fi
