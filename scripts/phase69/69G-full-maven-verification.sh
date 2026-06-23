#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69G
cd "$PHASE69_ROOT"
if [[ "$PHASE69_MODE" != full ]]; then
  phase69_result "$phase" PREPARED "Maven clean verify is configured; full mode is required for execution"
  exit 0
fi
phase69_require_full_confirmation "$phase" || true
rm -rf target/surefire-reports target/failsafe-reports
if phase69_run_logged "$phase-maven-clean-verify" ./mvnw -B clean verify; then
  if python3 scripts/phase69/collect_junit_results.py --root target --output "$PHASE69_EVIDENCE_ROOT/full-junit-summary.json" \
      | tee "$PHASE69_LOG_DIR/$phase-junit-summary.log"; then
    phase69_result "$phase" PASS "Maven clean verify passes with zero test errors and failures" \
      --evidence "logs/$phase-maven-clean-verify.log" --evidence full-junit-summary.json
  else
    phase69_result "$phase" FAIL "Maven verify returned success without valid zero-failure JUnit evidence"
    exit 1
  fi
else
  phase69_result "$phase" FAIL "Maven clean verify failed" --evidence "logs/$phase-maven-clean-verify.log"
  exit 1
fi
