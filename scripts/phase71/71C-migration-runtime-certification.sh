#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71C 'Migration V1-V106 runtime certification'
if phase_preflight; then
  phase_run 'inventory migrations in delivery mode' python3 scripts/phase71/verify_migrations.py --allow-missing --output "$PHASE71_DIR/migration-inventory.json"
  certified="$(python3 -c 'import json,sys; print(str(json.load(open(sys.argv[1]))["certified"]).lower())' "$PHASE71_DIR/migration-inventory.json")"
  if [[ "$certified" == true ]]; then phase_finalize PREPARED 0 'migration inventory complete; clean and upgrade execution pending'; else phase_finalize BLOCKED 2 'authoritative migration chain incomplete'; fi
  exit 0
fi
phase_run 'strict migration inventory' python3 scripts/phase71/verify_migrations.py --output "$PHASE71_DIR/migration-inventory.json"
require_uat
: "${PHASE71_MIGRATION_CERT_COMMAND:?PHASE71_MIGRATION_CERT_COMMAND is required}"
phase_run 'execute clean and upgrade migration certification' bash -lc "$PHASE71_MIGRATION_CERT_COMMAND"
if [[ -f sql/phase71/data-integrity-checks.sql ]]; then
  : "${PHASE71_PSQL_COMMAND:?PHASE71_PSQL_COMMAND is required}"
  phase_run 'run post-migration data integrity SQL' bash -lc "$PHASE71_PSQL_COMMAND -v ON_ERROR_STOP=1 -f sql/phase71/data-integrity-checks.sql"
fi
phase_finalize PASS 0 'V1-V106 clean install, upgrade paths and integrity checks passed'
