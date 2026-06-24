#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75H 'Production infrastructure and migration dry run'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'production contract, external connectivity and migration dry-run gate ready'; exit 0; fi
require_prod; require_identity; require_flag PHASE75_EXECUTE_PRODUCTION_DRY_RUN
: "${PHASE75_PRODUCTION_INFRA_ATTESTATION:?PHASE75_PRODUCTION_INFRA_ATTESTATION required}"
: "${PHASE75_PRODUCTION_MIGRATION_DRY_RUN_COMMAND:?PHASE75_PRODUCTION_MIGRATION_DRY_RUN_COMMAND required}"
phase_run 'production-like migration dry run' bash -lc "$PHASE75_PRODUCTION_MIGRATION_DRY_RUN_COMMAND"
phase_run 'production infrastructure attestation' python3 scripts/phase75/verify_production_attestation.py --kind production-infrastructure --file "$PHASE75_PRODUCTION_INFRA_ATTESTATION" --output "$PHASE75_DIR/production-infrastructure.json" --commit "$PHASE75_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'production contract, connectivity, baseline and migration dry run passed'
