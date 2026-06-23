#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71J 'Signed UAT bundle and Phase 54 entry decision'
if [[ ! -f scripts/phase64/run_phase64.sh ]]; then
  phase_finalize BLOCKED 2 'authoritative Phase 64 source is missing; signed entry gate cannot execute'
  exit 0
fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'Phase 64 runner and signed bundle builder ready; Phase 71A-I PASS and approvals pending'; exit 0; fi
require_uat
: "${PHASE71_PHASE54_ENTRY_ATTESTATION:?PHASE71_PHASE54_ENTRY_ATTESTATION is required}"
phase_run 'execute Phase 64 signed entry gate' scripts/phase64/run_phase64.sh --full
phase_run 'verify Phase 54 entry attestation' python3 scripts/phase71/verify_attestation.py --kind phase54-entry --file "$PHASE71_PHASE54_ENTRY_ATTESTATION" --output "$PHASE71_DIR/phase54-entry-attestation.json"
phase_run 'build immutable signed UAT bundle' python3 scripts/phase71/build_uat_bundle.py --phase71-root "$PHASE71_RUN_DIR" --attestation "$PHASE71_PHASE54_ENTRY_ATTESTATION" --output "$PHASE71_DIR/phase54-entry-bundle.json"
phase_finalize PASS 0 'Phase 64 passed, P0 blockers closed and Phase 54 entry decision is GO'
