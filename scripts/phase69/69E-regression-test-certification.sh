#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69E
cd "$PHASE69_ROOT"
required=(
  src/test/java/com/example/switching/webhook/crypto/WebhookEncryptionConfigurationTest.java
  src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java
  scripts/phase69/verify_crossborder_temporal_binding.py
)
for file in "${required[@]}"; do [[ -f "$file" ]] || { phase69_result "$phase" FAIL "Missing regression artifact: $file"; exit 1; }; done
python3 scripts/phase69/verify_crossborder_temporal_binding.py --self-test > "$PHASE69_LOG_DIR/$phase-self-test.log"
phase69_result "$phase" PASS "P0 blocker regression artifacts are present and temporal scanner self-tests pass" \
  --evidence "logs/$phase-self-test.log" --metric regressionArtifacts=${#required[@]}
