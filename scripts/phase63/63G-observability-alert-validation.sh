#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63G 'Observability inventory and alert routing validation'
STATUS=FAIL; MESSAGE='observability/alert validation failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file scripts/monitoring/verify_alert_runbooks.py
phase63_require_file monitoring/scripts/build-alert-inventory.py
phase63_require_file scripts/monitoring/build_alert_test_matrix.py
phase63_run 'alert and runbook contract' python3 scripts/monitoring/verify_alert_runbooks.py
phase63_run 'complete alert inventory' python3 monitoring/scripts/build-alert-inventory.py \
  --root . --output "$PHASE63_PHASE_DIR/alert-inventory.json"
phase63_run 'UAT PrometheusRule firing matrix' python3 scripts/monitoring/build_alert_test_matrix.py \
  --root . --output "${PHASE63_PHASE_DIR#$PHASE63_ROOT/}/alert-test-matrix.md"
if ! phase63_is_full; then
  STATUS=PREPARED; MESSAGE='all alert/runbook contracts pass; live Pending/Firing/Resolved routing evidence remains'; exit 0
fi
phase63_require_uat_confirmation
: "${ALERTMANAGER_URL:?ALERTMANAGER_URL is required}"
: "${ALERT_EXPECTED_RECEIVER:?ALERT_EXPECTED_RECEIVER is required}"
: "${PHASE63_ALERT_ATTESTATION:?PHASE63_ALERT_ATTESTATION is required}"
phase63_require_attestation "$PHASE63_ALERT_ATTESTATION"
export ALERT_DRILL_CONFIRMATION=I_UNDERSTAND_THIS_SENDS_TEST_ALERTS
export ALERT_DELIVERY_OUTPUT="$PHASE63_PHASE_DIR/alert-delivery-results.json"
phase63_run 'synthetic delivery drill for all PrometheusRule alerts' scripts/monitoring/run_alert_delivery_drill.sh
phase63_run 'alert evidence attestation' python3 scripts/phase63/verify_alert_attestation.py \
  --inventory "$PHASE63_PHASE_DIR/alert-inventory.json" \
  --delivery "$PHASE63_PHASE_DIR/alert-delivery-results.json" \
  --attestation "$PHASE63_ALERT_ATTESTATION" --output "$PHASE63_PHASE_DIR/alert-verification.json"
STATUS=PASS; MESSAGE='alert rules, runbooks, receiver routing, resolution and operator delivery evidence verified'
