#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74A 'Hermetic build and Maven green closure'
if phase_preflight || phase_repo; then
  [[ -x ./mvnw && -f pom.xml ]] || { phase_finalize BLOCKED 2 'Maven wrapper or pom.xml missing'; exit 0; }
  phase_finalize PREPARED 0 'Maven clean verify, fresh reports and flaky-test repetition are ready; execution pending'; exit 0
fi
require_flag PHASE74_EXECUTE_MAVEN
phase_run 'Maven clean verify' ./mvnw --batch-mode --no-transfer-progress clean verify
phase_run 'fresh test report certification' python3 scripts/phase74/verify_test_reports.py --output "$PHASE74_DIR/test-summary.json" --minimum-tests "${PHASE74_MINIMUM_TESTS:-1}"
phase_run 'production static gates' scripts/certification/run_static_gates.sh
if [[ -n "${PHASE74_CRITICAL_TESTS:-}" ]]; then
  for i in 1 2 3; do phase_run "critical test repetition $i" ./mvnw --batch-mode --no-transfer-progress -Dtest="$PHASE74_CRITICAL_TESTS" test; done
fi
phase_finalize PASS 0 'Maven verification and current-commit test evidence passed'
