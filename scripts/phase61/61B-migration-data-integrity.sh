#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61B" "Migration and data-integrity certification"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="migration/data-integrity certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_run "migration inventory" python3 scripts/phase61/verify_migration_inventory.py \
  --migration-dir src/main/resources/db/migration --latest 101 --count 90 --reserved 88 89 90 91 92 93 94 95 96 98 99 \
  --output "$PHASE61_PHASE_DIR/migration-inventory.json"
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="V1-V101 clean/upgrade/idempotency certification tests and inventory are ready"; exit 0
fi
phase_run "clean-install and repair migrations" ./mvnw -B \
  -Dtest=V83CleanInstallCertificationIntegrationTest,V97SmosUserAccessMigrationIntegrationTest,V100CurrentStatusReportingRepairIntegrationTest,V101SmosSecurityHardeningMigrationIntegrationTest test
phase_run "migration application lifecycle" ./mvnw -B -Dtest=MigrationApplicationIntegrationTest test
phase_run "financial data-integrity regression" ./mvnw -B \
  -Dtest=SettlementLifecycleIntegrationTest,CrossBorderAmlBlockIntegrationTest test
PHASE_STATUS="PASS"; PHASE_MESSAGE="V1-V101 clean install, upgrade-safe hardening and financial integrity tests passed"
