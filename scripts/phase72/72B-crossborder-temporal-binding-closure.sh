#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72B
json="$PHASE72_ARTIFACT_DIR/cross-border-temporal-binding.json"
if ! phase72_run_logged 72B-self-test python3 "$PHASE72_ROOT/scripts/verify_cross_border_temporal_binding.py" --self-test; then
  phase72_result "$phase" FAIL "Temporal binding scanner self-test failed"; exit 1
fi
if phase72_run_logged 72B-scan python3 "$PHASE72_ROOT/scripts/verify_cross_border_temporal_binding.py" --root "$PHASE72_ROOT" --json-output "$json"; then
  phase72_result "$phase" PASS "No untyped Instant JDBC binding found in cross-border source" --detail violations=0
else
  count=$(python3 -c 'import json,sys; print(len(json.load(open(sys.argv[1]))["violations"]))' "$json" 2>/dev/null || echo 1)
  phase72_result "$phase" FAIL "Cross-border temporal binding violations remain" --detail violations="$count"; exit 1
fi
