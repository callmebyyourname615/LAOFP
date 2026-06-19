#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
: "${DB_HOST:?DB_HOST required}"
: "${DB_PORT:=5432}"
: "${DB_NAME:?DB_NAME required}"
: "${DB_MAINTENANCE_USERNAME:?DB_MAINTENANCE_USERNAME required}"
: "${DB_MAINTENANCE_PASSWORD:?DB_MAINTENANCE_PASSWORD required}"
: "${DB_SSLMODE:=verify-full}"
: "${DB_SSLROOTCERT:?DB_SSLROOTCERT required}"

export PGHOST="$DB_HOST" PGPORT="$DB_PORT" PGDATABASE="$DB_NAME"
export PGUSER="$DB_MAINTENANCE_USERNAME" PGPASSWORD="$DB_MAINTENANCE_PASSWORD"
export PGSSLMODE="$DB_SSLMODE" PGSSLROOTCERT="$DB_SSLROOTCERT" PGAPPNAME="switching-db-maintenance"
run_id="$(cat /proc/sys/kernel/random/uuid)"
started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

finish() {
  rc=$?
  status=SUCCEEDED
  (( rc == 0 )) || status=FAILED
  details="{\"runner\":\"kubernetes-cronjob\",\"exitCode\":${rc}}"
  psql -X -v ON_ERROR_STOP=1 -v run_id="$run_id" -v status="$status" -v details="$details" <<'SQL' || true
UPDATE database_maintenance_runs
SET status = :'status', completed_at = NOW(), details_json = :'details'
WHERE run_id = :'run_id'::uuid;
SELECT pg_advisory_unlock(94712048);
SQL
  exit "$rc"
}
trap finish EXIT INT TERM

locked="$(psql -XAtqc 'SELECT pg_try_advisory_lock(94712048)')"
[[ "$locked" == "t" ]] || { echo "another database maintenance run holds the lock" >&2; exit 75; }
psql -X -v ON_ERROR_STOP=1 -v run_id="$run_id" -v started="$started" <<'SQL'
INSERT INTO database_maintenance_runs(run_id, operation, status, started_at, details_json)
VALUES (:'run_id'::uuid, 'VACUUM_ANALYZE', 'STARTED', :'started'::timestamptz, '{}');
SQL
psql -X -f "${REPO_ROOT}/database/maintenance/preflight.sql"
psql -X -f "${REPO_ROOT}/database/maintenance/vacuum-analyze.sql"
