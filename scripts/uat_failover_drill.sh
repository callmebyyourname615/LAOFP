#!/usr/bin/env bash
set -Eeuo pipefail

[[ "${DRILL_ENVIRONMENT:-}" == "uat" ]] || {
  echo "Refusing to run: set DRILL_ENVIRONMENT=uat explicitly." >&2
  exit 64
}
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }
docker compose version >/dev/null

primary=postgres
replica=postgres-read-replica
db="${POSTGRES_DB:-switching_db}"
user="${POSTGRES_USER:-switching}"
evidence_dir="${DRILL_EVIDENCE_DIR:-build/dr-evidence}"
mkdir -p "$evidence_dir"
started_epoch="$(date +%s)"
started_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
marker="dr-$(date -u +%Y%m%d%H%M%S)-$RANDOM"

compose_ps() { docker compose ps --status running --services | grep -qx "$1"; }
compose_ps "$primary" || { echo "primary is not running" >&2; exit 1; }
compose_ps "$replica" || { echo "replica is not running" >&2; exit 1; }

primary_recovery="$(docker compose exec -T "$primary" psql -U "$user" -d "$db" -AtX -c 'SELECT pg_is_in_recovery()')"
replica_recovery="$(docker compose exec -T "$replica" psql -U "$user" -d "$db" -AtX -c 'SELECT pg_is_in_recovery()')"
[[ "$primary_recovery" == f && "$replica_recovery" == t ]] || {
  echo "Unexpected topology: primary_recovery=$primary_recovery replica_recovery=$replica_recovery" >&2
  exit 1
}

# Stop application writes before intentionally stopping the database primary.
docker compose stop app >/dev/null 2>&1 || true

docker compose exec -T "$primary" psql -U "$user" -d "$db" -v ON_ERROR_STOP=1 <<SQL
CREATE SCHEMA IF NOT EXISTS dr_drill;
CREATE TABLE IF NOT EXISTS dr_drill.markers (
  marker text PRIMARY KEY,
  created_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO dr_drill.markers(marker) VALUES ('$marker');
SQL
marker_lsn="$(docker compose exec -T "$primary" psql -U "$user" -d "$db" -AtX -c 'SELECT pg_current_wal_lsn()')"

replication_deadline=$(( $(date +%s) + ${DRILL_REPLICATION_TIMEOUT_SECONDS:-60} ))
until [[ "$(docker compose exec -T "$replica" psql -U "$user" -d "$db" -AtX -c "SELECT EXISTS (SELECT 1 FROM dr_drill.markers WHERE marker='$marker')")" == t ]]; do
  (( $(date +%s) < replication_deadline )) || { echo "marker did not reach replica" >&2; exit 1; }
  sleep 1
done

echo "Stopping primary. The script will not restart it automatically to avoid split brain." >&2
docker compose stop "$primary"
failure_epoch="$(date +%s)"
docker compose exec -T "$replica" gosu postgres pg_ctl -D /var/lib/postgresql/data promote -w

promotion_deadline=$(( failure_epoch + ${DRILL_PROMOTION_TIMEOUT_SECONDS:-30} ))
until [[ "$(docker compose exec -T "$replica" psql -U "$user" -d "$db" -AtX -c 'SELECT pg_is_in_recovery()')" == f ]]; do
  (( $(date +%s) < promotion_deadline )) || { echo "replica promotion timed out" >&2; exit 1; }
  sleep 1
done
promoted_epoch="$(date +%s)"
marker_present="$(docker compose exec -T "$replica" psql -U "$user" -d "$db" -AtX -c "SELECT EXISTS (SELECT 1 FROM dr_drill.markers WHERE marker='$marker')")"
[[ "$marker_present" == t ]] || { echo "promoted replica is missing drill marker" >&2; exit 1; }
rto="$((promoted_epoch - failure_epoch))"

cat >"${evidence_dir}/uat-failover-${started_epoch}.json" <<JSON
{
  "schemaVersion": 1,
  "environment": "uat",
  "drill": "postgres-primary-stop-and-replica-promote",
  "startedAt": "${started_at}",
  "marker": "${marker}",
  "markerLsn": "${marker_lsn}",
  "primaryFailureEpoch": ${failure_epoch},
  "replicaPromotedEpoch": ${promoted_epoch},
  "rtoSeconds": ${rto},
  "markerPresentAfterPromotion": true,
  "result": "PASS"
}
JSON

echo "Failover drill PASS. RTO=${rto}s evidence=${evidence_dir}/uat-failover-${started_epoch}.json"
echo "ACTION REQUIRED: keep the old primary stopped, point clients to the promoted replica, then re-seed the old primary as a standby." >&2
