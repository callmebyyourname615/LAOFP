#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78C 'Migration V1-V106 runtime certification'
set +e; python3 scripts/phase78/verify_source_convergence.py --root . --output "$PHASE78_DIR/migration-inventory.json" >/dev/null 2>&1; rc=$?; set -e
if ((rc!=0)); then phase_finalize BLOCKED 2 'migration/source convergence contract incomplete'; exit 0; fi
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'clean-install and upgrade hooks ready'; exit 0; fi
require_uat; require_flag PHASE78_EXECUTE_MIGRATIONS
: "${PHASE78_MIGRATION_CLEAN_COMMAND:?required}" "${PHASE78_MIGRATION_UPGRADE_COMMAND:?required}" "${PHASE78_POST_MIGRATION_DSN:?required}"
phase_run 'clean migration path' bash -lc "$PHASE78_MIGRATION_CLEAN_COMMAND"
phase_run 'upgrade paths' bash -lc "$PHASE78_MIGRATION_UPGRADE_COMMAND"
phase_run 'financial integrity' psql "$PHASE78_POST_MIGRATION_DSN" -v ON_ERROR_STOP=1 -f sql/phase78/post-migration-financial-integrity.sql
phase_finalize PASS 0 'migration runtime certification passed'
