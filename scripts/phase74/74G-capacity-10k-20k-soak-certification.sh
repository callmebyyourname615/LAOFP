#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74G '10K, 20K burst and 8-hour soak capacity certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 '10K/20K/soak policy and evidence verification ready'; exit 0; fi
require_uat; require_identity; require_flag PHASE74_EXECUTE_LOAD
: "${PHASE74_CAPACITY_COMMAND:?PHASE74_CAPACITY_COMMAND required}"
: "${PHASE74_CAPACITY_ATTESTATION:?PHASE74_CAPACITY_ATTESTATION required}"
: "${PHASE74_PERFORMANCE_RESULT:?PHASE74_PERFORMANCE_RESULT required}"
phase_run 'critical capacity scenarios' bash -lc "$PHASE74_CAPACITY_COMMAND"
phase_run 'performance invariant verification' python3 scripts/phase74/verify_financial_evidence.py --kind performance --file "$PHASE74_PERFORMANCE_RESULT" --output "$PHASE74_DIR/performance-result-verification.json"
phase_run 'capacity attestation' python3 scripts/phase74/verify_attestation.py --kind performance-capacity --file "$PHASE74_CAPACITY_ATTESTATION" --output "$PHASE74_DIR/capacity-attestation.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 '10K sustained, 20K burst and 8-hour soak passed without exhaustion or unbounded lag'
