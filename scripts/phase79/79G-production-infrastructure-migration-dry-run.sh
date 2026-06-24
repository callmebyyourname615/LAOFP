#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE79_ROOT"; phase_setup 79G 'Production infrastructure and migration dry run'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'production infrastructure attestation and dry-run hooks ready'; exit 0; fi
require_prod; require_identity; require_flag PHASE79_EXECUTE_DRY_RUN; : "${PHASE79_PRODUCTION_INFRA_ATTESTATION:?required}" "${PHASE79_MIGRATION_DRY_RUN_COMMAND:?required}"
phase_run 'production infrastructure attestation' python3 scripts/phase79/verify_production_attestation.py --kind production-infrastructure --file "$PHASE79_PRODUCTION_INFRA_ATTESTATION" --output "$PHASE79_DIR/production-infrastructure.json" --commit "$PHASE79_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_run 'production migration dry run' bash -lc "$PHASE79_MIGRATION_DRY_RUN_COMMAND"
phase_finalize PASS 0 'production infrastructure and migration dry run passed'
