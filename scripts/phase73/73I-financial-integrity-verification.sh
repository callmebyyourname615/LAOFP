#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73I" "Post-chaos financial integrity and recovery certification"
STATUS=FAIL; MESSAGE="post-chaos integrity certification failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file scripts/phase73/verify_scenario_evidence.py
if phase73_is_preflight; then
  STATUS=PREPARED; MESSAGE="zero-loss, zero-duplicate, balance, outbox and RTO verification contract is ready"; exit 0
fi
phase73_require_execution_approval
phase73_run "scenario evidence certification" python3 scripts/phase73/verify_scenario_evidence.py \
  --policy "$PHASE73_POLICY" --evidence-root "$PHASE73_RUN_DIR" --output "$PHASE73_PHASE_DIR/scenario-summary.json"
STATUS=PASS; MESSAGE="all required scenarios passed RTO and zero financial loss thresholds"
