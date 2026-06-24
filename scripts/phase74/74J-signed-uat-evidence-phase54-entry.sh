#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74J 'Signed UAT evidence and Phase 54 entry'
missing=(); for f in scripts/phase61/run_phase61.sh scripts/phase64/run_phase64.sh scripts/phase65/run_phase65.sh scripts/phase66/run_phase66.sh scripts/phase68/run_phase68.sh scripts/phase69/run_phase69.sh scripts/phase70/run_phase70.sh scripts/phase71/run_phase71.sh scripts/phase72/run_phase72.sh scripts/phase73/run_phase73.sh; do [[ -f "$f" ]] || missing+=("$f"); done
if ((${#missing[@]})); then phase_finalize BLOCKED 2 "authoritative prerequisite source missing: ${missing[*]}"; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'all prerequisite phase sources present; signed UAT aggregation pending'; exit 0; fi
require_uat; require_identity
: "${PHASE74_PHASE54_ENTRY_ATTESTATION:?PHASE74_PHASE54_ENTRY_ATTESTATION required}"
phase_run 'Phase 54 entry attestation' python3 scripts/phase74/verify_attestation.py --kind phase54-entry --file "$PHASE74_PHASE54_ENTRY_ATTESTATION" --output "$PHASE74_DIR/phase54-entry-attestation.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_run 'immutable UAT bundle' python3 scripts/phase74/build_uat_bundle.py --phase74-root "$PHASE74_RUN_DIR" --attestation "$PHASE74_PHASE54_ENTRY_ATTESTATION" --output "$PHASE74_DIR/phase54-entry-bundle.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'all UAT evidence is commit-matched and Phase 54 entry decision is GO'
