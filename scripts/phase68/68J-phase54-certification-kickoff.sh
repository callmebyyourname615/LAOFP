#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68J 'Phase 54 certification kickoff and RC freeze'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'kickoff bundle builder ready; Phase 68A-I PASS results and approvals pending'; exit 0; fi
require_uat
: "${PHASE68_KICKOFF_ATTESTATION:?PHASE68_KICKOFF_ATTESTATION is required}"
phase_run 'verify kickoff attestation' python3 scripts/phase68/verify_attestation.py --kind kickoff --file "$PHASE68_KICKOFF_ATTESTATION" --output "$PHASE68_DIR/kickoff-attestation-verification.json"
phase_run 'build immutable Phase 54 kickoff bundle' python3 scripts/phase68/build_phase54_kickoff_bundle.py --phase68-root "$PHASE68_RUN_DIR" --attestation "$PHASE68_KICKOFF_ATTESTATION" --output "$PHASE68_DIR/phase54-kickoff-bundle.json"
phase_finalize PASS 0 'immutable RC frozen and Phase 54A approved to start'
