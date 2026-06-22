#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56B "Continuous Data Integrity Reconciliation"; day2_require_environment production operations
snapshot="$PHASE_DIR/reconciliation-snapshot.json"
if [[ -n "${RECONCILIATION_SNAPSHOT:-}" ]]; then cp "$RECONCILIATION_SNAPSHOT" "$snapshot"; else day2_require_production_confirmation; day2_require_command psql; : "${DAY2_DATABASE_URL:?DAY2_DATABASE_URL is required}"; psql "$DAY2_DATABASE_URL" -X -qAt -f reconciliation/sql/continuous-reconciliation.sql > "$snapshot"; fi
day2_run_check evaluate python3 scripts/day2/evaluate_reconciliation.py --snapshot "$snapshot" --rules reconciliation/rules/continuous-integrity.yaml --thresholds "$DAY2_THRESHOLDS" --output "$PHASE_DIR/reconciliation-report.json" || true
day2_write_checksum "$PHASE_DIR/reconciliation-report.json" "$PHASE_DIR/reconciliation-report.sha256"; day2_write_result
