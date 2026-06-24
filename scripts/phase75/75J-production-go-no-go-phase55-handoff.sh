#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75J 'Production GO/NO-GO and Phase 55/67 handoff'
if [[ ! -f scripts/phase67/run_phase67.sh ]]; then phase_finalize BLOCKED 2 'authoritative Phase 67 production cutover source is missing'; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'production decision and Phase 55/67 handoff bundle builder ready'; exit 0; fi
require_prod; require_identity
: "${PHASE75_PRODUCTION_DECISION_ATTESTATION:?PHASE75_PRODUCTION_DECISION_ATTESTATION required}"
PHASE75_PHASE54_MANIFEST="${PHASE75_PHASE54_MANIFEST:-build/phase54-certification/manifest.json}"
phase_run 'production decision attestation' python3 scripts/phase75/verify_production_attestation.py --kind production-decision --file "$PHASE75_PRODUCTION_DECISION_ATTESTATION" --output "$PHASE75_DIR/production-decision.json" --commit "$PHASE75_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_run 'production handoff bundle' python3 scripts/phase75/build_production_handoff.py --phase75-root "$PHASE75_RUN_DIR" --phase54-manifest "$PHASE75_PHASE54_MANIFEST" --decision-attestation "$PHASE75_PRODUCTION_DECISION_ATTESTATION" --output "$PHASE75_DIR/phase55-phase67-handoff.json" --commit "$PHASE75_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'production decision is GO and Phase 55/67 cutover is authorized'
