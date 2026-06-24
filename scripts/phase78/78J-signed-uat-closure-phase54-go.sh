#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78J 'Signed UAT closure and Phase 54 GO'
missing=(); for f in scripts/phase64/run_phase64.sh scripts/phase66/run_phase66.sh scripts/phase72/run_phase72.sh scripts/phase73/run_phase73.sh scripts/phase76/run_phase76.sh; do [[ -f "$f" ]] || missing+=("$f"); done
if ((${#missing[@]})); then phase_finalize BLOCKED 2 "aggregation source missing: ${missing[*]}"; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'signed UAT closure bundle ready'; exit 0; fi
require_uat; require_identity; : "${PHASE78_PHASE54_GO_ATTESTATION:?required}"
phase_run 'Phase 54 GO attestation' python3 scripts/phase78/verify_attestation.py --kind phase54-go --file "$PHASE78_PHASE54_GO_ATTESTATION" --output "$PHASE78_DIR/phase54-go.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_run 'UAT closure bundle' python3 scripts/phase78/build_uat_closure.py --evidence-root "$PHASE78_RUN_DIR" --attestation "$PHASE78_PHASE54_GO_ATTESTATION" --output "$PHASE78_DIR/uat-closure-bundle.json" --commit "$PHASE78_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'signed UAT closure verified and Phase 54 is GO'
