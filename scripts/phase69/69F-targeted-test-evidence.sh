#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69F
cd "$PHASE69_ROOT"
if [[ "$PHASE69_MODE" != full ]]; then
  phase69_result "$phase" PREPARED "Targeted Maven tests are configured; full mode is required for execution"
  exit 0
fi
phase69_require_full_confirmation "$phase" || true
log="$PHASE69_LOG_DIR/$phase-targeted-tests.log"
rm -rf target/surefire-reports target/failsafe-reports
if phase69_run_logged "$phase-targeted-tests" ./mvnw -B \
    -Dtest=WebhookEncryptionConfigurationTest,OperationsGenerateRoutesForBankIntegrationTest \
    test; then
  if python3 scripts/phase69/collect_junit_results.py --root target --output "$PHASE69_EVIDENCE_ROOT/targeted-junit-summary.json" \
      | tee "$PHASE69_LOG_DIR/$phase-junit-summary.log"; then
    phase69_result "$phase" PASS "Targeted blocker regression tests pass" \
      --evidence "logs/$phase-targeted-tests.log" --evidence targeted-junit-summary.json
  else
    phase69_result "$phase" FAIL "Targeted Maven command returned success but JUnit evidence is missing or failing" \
      --evidence "logs/$phase-targeted-tests.log"
    exit 1
  fi
else
  phase69_result "$phase" FAIL "Targeted blocker regression tests failed" --evidence "logs/$phase-targeted-tests.log"
  exit 1
fi
