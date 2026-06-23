#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62I "JPA N+1 detection and pagination enforcement"
phase_run "SQL inspection and pagination contract" python3 scripts/phase62/verify_pagination_and_nplus1.py
if phase_is_preflight; then phase_finalize PREPARED 0 "N+1 instrumentation and bounded admin pagination are ready"; exit 0; fi
phase_run "N+1 tests" ./mvnw -B -Dtest=NPlusOneStatementInspectorTest test
phase_finalize PASS 0 "N+1 instrumentation tests passed"
