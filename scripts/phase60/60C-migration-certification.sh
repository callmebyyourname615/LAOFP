#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60C" "Migration V1 to V100 certification"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="migration certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_run "migration inventory and checksums" python3 scripts/phase60/verify_migration_inventory.py \
  --root . --output "$PHASE60_PHASE_DIR/migration-inventory.json"

if phase_is_preflight; then
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="migration inventory is valid; clean-install and upgrade tests require Maven/Testcontainers"
  exit 0
fi

phase_run "clean install and V100 migration integration tests" ./mvnw -B \
  -Dtest=MigrationApplicationIntegrationTest,V83CleanInstallCertificationIntegrationTest,V83PayloadSha256SchemaAlignmentIntegrationTest,V97SmosUserAccessMigrationIntegrationTest,V100CurrentStatusReportingRepairIntegrationTest \
  test

if [[ -n "${PHASE60_UPGRADE_DB_URL:-}" ]]; then
  phase_require_command psql
  : "${PHASE60_UPGRADE_DB_USERNAME:?PHASE60_UPGRADE_DB_USERNAME is required when PHASE60_UPGRADE_DB_URL is set}"
  : "${PHASE60_UPGRADE_DB_PASSWORD:?PHASE60_UPGRADE_DB_PASSWORD is required when PHASE60_UPGRADE_DB_URL is set}"
  export PGPASSWORD="$PHASE60_UPGRADE_DB_PASSWORD"
  psql "$PHASE60_UPGRADE_DB_URL" -U "$PHASE60_UPGRADE_DB_USERNAME" -v ON_ERROR_STOP=1 -AtF, <<'SQL' \
    > "$PHASE60_PHASE_DIR/upgrade-database-verification.csv"
SELECT 'flyway_success_count', count(*)::text FROM flyway_schema_history WHERE success
UNION ALL
SELECT 'flyway_latest_version', max(version)::text FROM flyway_schema_history WHERE success
UNION ALL
SELECT 'smos_role_count', count(*)::text FROM smos_roles
UNION ALL
SELECT 'smos_permission_count', count(*)::text FROM smos_permissions;
SQL
  grep -qx 'flyway_success_count,95' "$PHASE60_PHASE_DIR/upgrade-database-verification.csv"
  grep -qx 'flyway_latest_version,100' "$PHASE60_PHASE_DIR/upgrade-database-verification.csv"
  grep -qx 'smos_role_count,8' "$PHASE60_PHASE_DIR/upgrade-database-verification.csv"
  grep -qx 'smos_permission_count,16' "$PHASE60_PHASE_DIR/upgrade-database-verification.csv"
else
  phase_log "Production-like upgrade database variables are not set; Testcontainers clean-install path was certified"
fi

PHASE_STATUS="PASS"
PHASE_MESSAGE="migration inventory, clean install, V97 SMOS schema and V100 reporting repair certification passed"
