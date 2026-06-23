#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69D
cd "$PHASE69_ROOT"
log="$PHASE69_LOG_DIR/$phase-temporal-binding.log"
if python3 scripts/phase69/verify_crossborder_temporal_binding.py --self-test 2>&1 | tee "$log"; then
  phase69_result "$phase" PASS "Cross-border Instant bindings require Types.TIMESTAMP_WITH_TIMEZONE" --evidence "logs/$phase-temporal-binding.log"
else
  phase69_result "$phase" FAIL "Unsafe cross-border Instant JDBC binding detected" --evidence "logs/$phase-temporal-binding.log"
  exit 1
fi
