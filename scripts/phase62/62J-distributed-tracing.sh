#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62J "Distributed tracing and durable correlation"
phase_run "tracing contract" python3 scripts/phase62/verify_distributed_tracing.py
if phase_is_preflight; then phase_finalize PREPARED 0 "OTLP and durable trace correlation are ready"; exit 0; fi
phase_run "trace tests" ./mvnw -B -Dtest=TraceContextSupportTest test
phase_finalize PASS 0 "trace context tests passed"
