#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68B 'Full Maven and migration green gate'
if phase_preflight; then
  phase_run 'inventory migrations in delivery mode' python3 scripts/phase68/verify_migration_inventory.py --allow-missing --output "$PHASE68_DIR/migration-inventory.json"
  certified="$(python3 -c 'import json,sys; print(str(json.load(open(sys.argv[1]))["certified"]).lower())' "$PHASE68_DIR/migration-inventory.json")"
  if [[ "$certified" == true ]]; then phase_finalize PREPARED 0 'migration chain complete; Maven execution pending'; else phase_finalize BLOCKED 2 'authoritative migration chain incomplete; see migration-inventory.json'; fi
  exit 0
fi
phase_run 'strict migration inventory' python3 scripts/phase68/verify_migration_inventory.py --output "$PHASE68_DIR/migration-inventory.json"
phase_run 'clean Maven verification' ./mvnw -B clean verify
phase_run 'static production verifiers' python3 scripts/verify_all_static.py
phase_finalize PASS 0 'Maven tests, static gates and V1-V106 migration inventory passed'
