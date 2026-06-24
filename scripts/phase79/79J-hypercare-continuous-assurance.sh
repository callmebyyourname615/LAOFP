#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79J 'Hypercare and continuous assurance activation'
[[ -f scripts/phase77/run_phase77.sh ]] || { phase_finalize BLOCKED 2 'authoritative Phase 77 source missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 '14-day hypercare and BAU activation bundle ready'; exit 0; fi
require_prod; require_identity; : "${PHASE79_HYPERCARE_ATTESTATION:?required}" "${PHASE79_PRODUCTION_GO_ATTESTATION:?required}"
phase_run 'hypercare attestation' python3 scripts/phase79/verify_production_attestation.py --kind hypercare-exit --file "$PHASE79_HYPERCARE_ATTESTATION" --output "$PHASE79_DIR/hypercare-exit.json" --commit "$PHASE79_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_run 'production GO attestation' python3 scripts/phase79/verify_production_attestation.py --kind production-go --file "$PHASE79_PRODUCTION_GO_ATTESTATION" --output "$PHASE79_DIR/production-go.json" --commit "$PHASE79_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_run 'production bundle' python3 scripts/phase79/build_production_bundle.py --evidence-root "$PHASE79_RUN_DIR" --attestation "$PHASE79_PRODUCTION_GO_ATTESTATION" --output "$PHASE79_DIR/production-go-live-bundle.json" --commit "$PHASE79_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'hypercare exited and continuous assurance activated'
