#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55I; live_require_environment hypercare
require_phase_pass 55A 55B 55C 55D 55E 55F 55G 55H
[[ "${HYPERCARE_VALIDATION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_READS_PRODUCTION_OBSERVABILITY_AND_FINANCIAL_AGGREGATES ]] || live_die "hypercare confirmation missing"
: "${HYPERCARE_OBSERVATIONS_FILE:?required}"; : "${HYPERCARE_INCIDENT_REGISTER_FILE:?required}"; : "${HYPERCARE_ALERT_SUMMARY_FILE:?required}"
for file in "$HYPERCARE_OBSERVATIONS_FILE" "$HYPERCARE_INCIDENT_REGISTER_FILE" "$HYPERCARE_ALERT_SUMMARY_FILE"; do [[ -f "$file" && ! -L "$file" ]] || live_die "hypercare input must be a regular non-symlink file: $file"; done
phase_begin 55I "Hypercare and Production Validation"
failed=0
cp "$HYPERCARE_ALERT_SUMMARY_FILE" "$PHASE_DIR/alert-summary.json"
run_check capture-hypercare-reconciliation python3 scripts/golive/capture_reconciliation.py --output "$PHASE_DIR/current-reconciliation.json" --label production-hypercare --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" || failed=1
run_check reconciliation-summary python3 scripts/golive/compare_reconciliation.py --baseline "$GOLIVE_ROOT/phases/55D/cutover-baseline.json" --current "$PHASE_DIR/current-reconciliation.json" --output "$PHASE_DIR/reconciliation-summary.json" || failed=1
run_check hypercare-exit-criteria python3 scripts/golive/build_hypercare_report.py --observations "$HYPERCARE_OBSERVATIONS_FILE" --incidents "$HYPERCARE_INCIDENT_REGISTER_FILE" --reconciliation "$PHASE_DIR/reconciliation-summary.json" --alerts "$PHASE_DIR/alert-summary.json" --output "$PHASE_DIR/hypercare-summary.json" || failed=1
run_check alert-runbooks python3 scripts/monitoring/verify_alert_runbooks.py || failed=1
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
