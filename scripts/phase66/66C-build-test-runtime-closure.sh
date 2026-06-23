#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66C" "Build and test runtime closure"
STATUS="FAIL"; MESSAGE="build/test runtime closure failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_require_file scripts/phase66/collect_test_results.py
if phase66_is_preflight; then
  phase66_run "test collector self-test" python3 scripts/phase66/collect_test_results.py --self-test
  STATUS="PREPARED"; MESSAGE="Maven and JUnit evidence collection contracts are ready"; exit 0
fi
phase66_require_command java
phase66_capture "Maven clean verify" "$PHASE66_PHASE_DIR/maven-verify.log" ./mvnw -B clean verify
phase66_run "aggregate fresh JUnit reports" python3 scripts/phase66/collect_test_results.py \
  --root . --output "$PHASE66_PHASE_DIR/test-summary.json" --require-fresh-minutes "${PHASE66_TEST_FRESHNESS_MINUTES:-180}"
STATUS="PASS"; MESSAGE="Maven verify and fresh JUnit aggregation completed with zero failures/errors"
