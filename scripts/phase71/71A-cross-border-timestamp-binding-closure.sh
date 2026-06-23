#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71A 'Cross-border timestamp binding closure'
phase_run 'verify explicit JDBC temporal binding' python3 scripts/phase71/verify_timestamp_binding.py --output "$PHASE71_DIR/timestamp-binding.json"
if phase_preflight; then phase_finalize PREPARED 0 'typed temporal binder and regression test present; targeted Maven tests pending'; exit 0; fi
phase_run 'targeted timestamp and cross-border tests' ./mvnw -B -Dtest=JdbcTemporalBinderTest,CrossBorderAmlBlockIntegrationTest test
phase_finalize PASS 0 'cross-border timestamp binding and targeted integration test passed'
