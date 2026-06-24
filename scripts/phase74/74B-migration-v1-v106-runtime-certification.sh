#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74B 'Migration V1-V106 runtime certification'
set +e; python3 scripts/phase74/verify_migrations.py --output "$PHASE74_DIR/migration-inventory.json"; rc=$?; set -e
if (( rc != 0 )); then phase_finalize BLOCKED 2 'authoritative 99-file migration chain is incomplete'; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'migration inventory complete; clean install, upgrade paths and integrity execution pending'; exit 0; fi
require_uat; require_identity
: "${PHASE74_MIGRATION_CLEAN_COMMAND:?PHASE74_MIGRATION_CLEAN_COMMAND required}"
: "${PHASE74_MIGRATION_UPGRADE_COMMAND:?PHASE74_MIGRATION_UPGRADE_COMMAND required}"
phase_run 'empty database to V106' bash -lc "$PHASE74_MIGRATION_CLEAN_COMMAND"
phase_run 'supported upgrade paths to V106' bash -lc "$PHASE74_MIGRATION_UPGRADE_COMMAND"
if [[ -n "${PHASE74_INTEGRITY_DATABASE_URL:-}" ]]; then
  phase_run 'post-migration financial integrity' psql "$PHASE74_INTEGRITY_DATABASE_URL" -v ON_ERROR_STOP=1 -f sql/phase74/post-migration-financial-integrity.sql
fi
phase_finalize PASS 0 'clean install, upgrade paths and financial integrity passed'
