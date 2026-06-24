#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80E
if phase80_full; then
  [[ "${PHASE80_ALLOW_SETTLEMENT_BENCHMARK:-false}" == true ]] || { phase80_emit BLOCKED 'settlement confirmation missing'; exit 1; }
  phase80_require_env BASE_URL API_KEY DB_URL DB_USERNAME DB_PASSWORD || { phase80_emit BLOCKED 'settlement settings missing'; exit 1; }
  export RESULT_DIR="$PHASE80_EVIDENCE_ROOT/artifacts/settlement" SETTLEMENT_SUMMARY_OUTPUT="$PHASE80_EVIDENCE_ROOT/artifacts/settlement-summary.json"
  phase80_run settlement-500k performance/settlement/run_settlement_benchmark.sh
  export PGPASSWORD="$DB_PASSWORD"
  psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -f sql/phase80/financial-integrity.sql > "$PHASE80_EVIDENCE_ROOT/artifacts/financial-integrity.txt"
  grep -q PHASE80_FINANCIAL_INTEGRITY_PASS "$PHASE80_EVIDENCE_ROOT/artifacts/financial-integrity.txt"
  phase80_emit PASS 'settlement 500K and non-waivable financial controls passed'
else phase80_emit PREPARED 'settlement and reconciliation proof ready; no data generated'; fi
