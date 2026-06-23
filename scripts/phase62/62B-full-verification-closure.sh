#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62B "Full build and verification closure"
static_args=()
if [[ "${PHASE62_ALLOW_MISSING_AUTHORITATIVE_PHASE_II:-false}" == "true" ]]; then
  static_args+=(--allow-missing-authoritative-phase-ii)
fi
phase_run "Phase 62 static contract" python3 scripts/verify_phase62_static.py "${static_args[@]}"
if phase_is_preflight; then phase_finalize PREPARED 0 "static contract passed; Maven execution pending"; exit 0; fi
phase_run "clean verify" ./mvnw -B clean verify
phase_run "certify fresh reports" python3 scripts/phase62/certify_test_reports.py --reports target --output "$PHASE62_PHASE_DIR/test-summary.json"
phase_finalize PASS 0 "Maven verification and fresh report certification passed"
