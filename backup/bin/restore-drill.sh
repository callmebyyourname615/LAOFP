#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh
# shellcheck source=metrics.sh
source /opt/switching-backup/bin/metrics.sh

require_command pg_ctl
require_command pg_isready
require_command psql
require_env S3_BUCKET BACKUP_AGE_IDENTITY_FILE
RESTORE_TARGET_DIR="${RESTORE_TARGET_DIR:-/var/lib/switching-backup/restore-drill/data}"
export RESTORE_TARGET_DIR
[[ "$RESTORE_TARGET_DIR" == *restore-drill* ]] || die "restore drill target must contain restore-drill"
cleanup_dir "$RESTORE_TARGET_DIR"
mkdir -p "$RESTORE_TARGET_DIR"
chmod 0700 "$RESTORE_TARGET_DIR"

started_epoch="$(date +%s)"
started_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
work="$(mktemp -d "${BACKUP_WORK_DIR:-/var/lib/switching-backup/work}/drill.XXXXXX")"
trap 'status=$?; pg_ctl -D "$RESTORE_TARGET_DIR" -m fast stop >/dev/null 2>&1 || true; if [[ $status -ne 0 ]]; then publish_failure_metric switching_restore_drill switching_restore_drill_failed || true; fi; cleanup_dir "$work"' EXIT

restore-basebackup.sh
socket_dir="${work}/socket"
mkdir -p "$socket_dir"
port="${RESTORE_DRILL_PORT:-55432}"
log_file="${work}/postgres.log"

pg_ctl -D "$RESTORE_TARGET_DIR" \
  -l "$log_file" \
  -o "-p ${port} -k ${socket_dir} -c listen_addresses='' -c ssl=off -c archive_mode=off -c archive_command='' -c shared_preload_libraries='' -c max_connections=20" \
  start

ready_deadline=$(( $(date +%s) + ${RESTORE_DRILL_TIMEOUT_SECONDS:-3600} ))
until pg_isready -h "$socket_dir" -p "$port" -d postgres >/dev/null 2>&1; do
  if (( $(date +%s) >= ready_deadline )); then
    tail -200 "$log_file" >&2 || true
    die "restored PostgreSQL did not become ready before timeout"
  fi
  sleep 2
done

verification_sql="${RESTORE_VERIFICATION_SQL:-/opt/switching-backup/config/restore-verification.sql}"
require_file "$verification_sql"
psql -h "$socket_dir" -p "$port" -d "${PGDATABASE:-switching_db}" \
  -v ON_ERROR_STOP=1 -X -f "$verification_sql" | tee "${work}/verification.log"

completed_epoch="$(date +%s)"
completed_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
duration="$((completed_epoch - started_epoch))"
backup_id="$(jq -er '.backupId' "$RESTORE_TARGET_DIR/.switching-restore-metadata.json")"
transaction_count="$(psql -h "$socket_dir" -p "$port" -d "${PGDATABASE:-switching_db}" -AtX -v ON_ERROR_STOP=1 -c 'SELECT count(*) FROM transactions')"
latest_transaction="$(psql -h "$socket_dir" -p "$port" -d "${PGDATABASE:-switching_db}" -AtX -v ON_ERROR_STOP=1 -c "SELECT COALESCE(to_char(max(created_at) AT TIME ZONE 'UTC','YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"'),'none') FROM transactions")"

cat >"${work}/evidence.json" <<EOF_EVIDENCE
{
  "schemaVersion": 1,
  "drillType": "isolated-pitr-restore",
  "backupId": "${backup_id}",
  "startedAt": "${started_at}",
  "completedAt": "${completed_at}",
  "rtoSeconds": ${duration},
  "targetRtoSeconds": ${RESTORE_DRILL_RTO_TARGET_SECONDS:-3600},
  "transactionCount": ${transaction_count},
  "latestTransactionCreatedAt": "${latest_transaction}",
  "verification": "PASS"
}
EOF_EVIDENCE
jq -e . "${work}/evidence.json" >/dev/null
key="${S3_PREFIX:-switching}/drill-evidence/${started_at:0:10}/${backup_id}-${started_epoch}.json"
s3_upload_required_targets "${work}/evidence.json" "$key"

cat >"${work}/metrics.prom" <<EOF_METRICS
# TYPE switching_restore_drill_last_success_timestamp_seconds gauge
switching_restore_drill_last_success_timestamp_seconds{database="${PGDATABASE:-switching_db}"} ${completed_epoch}
# TYPE switching_restore_drill_duration_seconds gauge
switching_restore_drill_duration_seconds{database="${PGDATABASE:-switching_db}"} ${duration}
# TYPE switching_restore_drill_failed gauge
switching_restore_drill_failed{database="${PGDATABASE:-switching_db}"} 0
# TYPE switching_restore_drill_rto_target_met gauge
switching_restore_drill_rto_target_met{database="${PGDATABASE:-switching_db}"} $(( duration <= ${RESTORE_DRILL_RTO_TARGET_SECONDS:-3600} ? 1 : 0 ))
EOF_METRICS
publish_metrics switching_restore_drill "${work}/metrics.prom"

pg_ctl -D "$RESTORE_TARGET_DIR" -m fast stop
log INFO "restore drill passed backup_id=${backup_id} rto_seconds=${duration} evidence_key=${key}"
