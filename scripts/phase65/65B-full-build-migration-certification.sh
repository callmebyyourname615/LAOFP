#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65B 'Full build and migration certification'
args=(); [[ "${PHASE65_ALLOW_MISSING_AUTHORITATIVE_PHASE_II:-false}" == true ]] && args+=(--allow-missing-phase-ii)
phase_run 'migration inventory' python3 scripts/phase65/verify_migration_chain.py --output "$PHASE65_DIR/migration-inventory.json" "${args[@]}"
if phase_preflight; then phase_finalize PREPARED 0 'migration inventory checked; clean/upgrade DB and Maven execution pending'; exit 0; fi
phase_run 'clean Maven verification' ./mvnw -B clean verify
phase_run 'fresh test certification' python3 scripts/phase65/certify_test_reports.py --target target --output "$PHASE65_DIR/test-summary.json"
phase_run 'production readiness gates' ./scripts/execute-and-verify/00-run-all.sh
phase_finalize PASS 0 'build, tests, migration inventory and readiness gates passed'
