#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72C
[[ -x "$PHASE72_ROOT/mvnw" ]] || { phase72_result "$phase" BLOCKED "Maven wrapper is missing or not executable"; exit 2; }
if [[ "$PHASE72_MODE" != full ]]; then
  java_version=$(java -version 2>&1 | head -n1 || echo unavailable)
  phase72_result "$phase" PREPARED "Maven verification commands are ready; tests are not executed in preflight" --detail java="$java_version"
  exit 0
fi
phase72_require_full "$phase" PHASE72_CONFIRM_FULL
if ! docker info >/dev/null 2>&1; then phase72_result "$phase" BLOCKED "Docker is required for Testcontainers integration tests"; exit 2; fi
classes=(CrossBorderTemporalBindingRegressionTest)
for pair in   "src/test/java/com/example/switching/webhook/crypto/WebhookEncryptionConfigurationTest.java:WebhookEncryptionConfigurationTest"   "src/test/java/com/example/switching/webhook/crypto/WebhookEncryptionConfigurationContextTest.java:WebhookEncryptionConfigurationContextTest"   "src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java:OperationsGenerateRoutesForBankIntegrationTest"   "src/test/java/com/example/switching/aml/SanctionsScreeningIntegrationTest.java:SanctionsScreeningIntegrationTest"   "src/test/java/com/example/switching/phaseii/RailMessageJournalIntegrationTest.java:RailMessageJournalIntegrationTest"; do
  file=${pair%%:*}; cls=${pair##*:}; [[ -f "$PHASE72_ROOT/$file" ]] && classes+=("$cls")
done
csv=$(IFS=,; echo "${classes[*]}")
cd "$PHASE72_ROOT"
if ! phase72_run_logged 72C-targeted ./mvnw -B -DskipTests=false -Dtest="$csv" test; then
  phase72_result "$phase" FAIL "Targeted P0 regression tests failed"; exit 1
fi
if ! phase72_run_logged 72C-full-verify ./mvnw -B -DskipTests=false clean verify; then
  phase72_result "$phase" FAIL "Maven clean verify failed"; exit 1
fi
summary="$PHASE72_ARTIFACT_DIR/junit-summary.json"
if ! python3 scripts/phase72/collect_junit_results.py --root target --output "$summary" | tee "$PHASE72_LOG_DIR/72C-junit-summary.log"; then
  phase72_result "$phase" FAIL "JUnit reports contain failures/errors or zero executed tests"; exit 1
fi
tests=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["tests"])' "$summary")
phase72_result "$phase" PASS "Maven clean verify completed with zero failures and errors" --detail tests="$tests"
