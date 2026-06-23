#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66G" "DR and failure-recovery campaign"
STATUS="FAIL"; MESSAGE="DR campaign failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_require_file config/phase66/dr-scenarios.yaml
phase66_require_file dr/scripts/run-dr-suite.sh
if ! phase66_is_full; then
  phase66_run "validate DR scenario contract" python3 scripts/phase66/validate_dr_results.py --config config/phase66/dr-scenarios.yaml --contract-only --output "$PHASE66_PHASE_DIR/dr-results.json"
  STATUS="PREPARED"; MESSAGE="DR campaign contract is ready; no fault injection executed"; exit 0
fi
phase66_require_uat; phase66_require_destructive_confirmation
: "${DR_EVIDENCE_DIR:?DR_EVIDENCE_DIR required}"
export DR_ENVIRONMENT=uat DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE EVIDENCE_DIR="$DR_EVIDENCE_DIR"
phase66_capture "execute application/dependency DR suite" "$PHASE66_PHASE_DIR/dr-suite.log" \
  dr/scripts/run-dr-suite.sh kill-application-pod kafka-broker-failure network-partition object-storage-failure external-timeout
phase66_run "record successful DR scenarios" python3 scripts/phase66/record_dr_scenario_results.py \
  --evidence-dir "$DR_EVIDENCE_DIR" --scenario pod-kill --scenario kafka-broker-failure \
  --scenario network-partition --scenario object-storage-failure --scenario external-timeout
phase66_capture "execute database failover drill" "$PHASE66_PHASE_DIR/database-failover.log" scripts/phase66/run_database_failover_drill.sh
phase66_run "validate DR evidence" python3 scripts/phase66/validate_dr_results.py --config config/phase66/dr-scenarios.yaml \
  --evidence-dir "$DR_EVIDENCE_DIR" --output "$PHASE66_PHASE_DIR/dr-results.json"
STATUS="PASS"; MESSAGE="all mandatory DR scenarios recovered within contract and produced evidence"
