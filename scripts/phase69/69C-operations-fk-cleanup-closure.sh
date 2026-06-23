#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69C
cd "$PHASE69_ROOT"
file=src/test/java/com/example/switching/operations/service/OperationsGenerateRoutesForBankIntegrationTest.java
grep -Fq 'deactivateConnectorlessParticipants()' "$file" || { phase69_result "$phase" FAIL "Connector-less participant isolation is missing"; exit 1; }
grep -Fq "SET status = 'INACTIVE'" "$file" || { phase69_result "$phase" FAIL "Connector-less participants are not deactivated"; exit 1; }
if grep -Eq 'DELETE[[:space:]]+FROM[[:space:]]+participants' "$file"; then
  phase69_result "$phase" FAIL "FK-sensitive participant deletion is still present"
  exit 1
fi
phase69_result "$phase" PASS "Shared integration-test setup isolates connector-less participants without FK deletion" --evidence "$file"
