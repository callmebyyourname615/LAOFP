#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
python3 "$ROOT/scripts/phase76/decision_policy_selftest.py" > "$PHASE76_EVIDENCE_DIR/logs/76I-policy-selftest.log"
write_result 76I PASS "Decision engine policy self-test passed"
