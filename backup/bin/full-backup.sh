#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh
# shellcheck source=metrics.sh
source /opt/switching-backup/bin/metrics.sh

require_command pg_basebackup
require_command pg_verifybackup
require_command age
require_command aws
require_command jq
require_command openssl
require_command flock
require_env PGHOST PGUSER PGPASSWORD S3_BUCKET BACKUP_AGE_RECIPIENT

validate_safe_token "${S3_PREFIX:-switching}" S3_PREFIX
BACKUP_WORK_DIR="${BACKUP_WORK_DIR:-/var/lib/switching-backup/work}"
mkdir -p "$BACKUP_WORK_DIR"
exec 9>"${BACKUP_WORK_DIR}/full-backup.lock"
flock -n 9 || die "another full backup is already running"

started_epoch="$(date +%s)"
started_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
backup_id="${started_at//[-:TZ]/}-$(openssl rand -hex 4)"
work="$(mktemp -d "${BACKUP_WORK_DIR}/base-${backup_id}.XXXXXX")"
trap 'status=$?; if [[ $status -ne 0 ]]; then publish_failure_metric switching_backup switching_backup_last_attempt_failed || true; fi; cleanup_dir "$work"' EXIT

base_dir="${work}/base"
mkdir -p "$base_dir"
connection="$(postgres_connection_uri postgres)"
log INFO "starting physical base backup id=${backup_id} host=${PGHOST}"

before_lsn="$(psql "$connection" -AtX -v ON_ERROR_STOP=1 -c 'SELECT pg_current_wal_lsn()' 2>/dev/null || printf unknown)"
system_identifier="$(psql "$connection" -AtX -v ON_ERROR_STOP=1 -c 'SELECT system_identifier FROM pg_control_system()' 2>/dev/null || printf unavailable)"

pg_basebackup \
  --dbname="$connection" \
  --pgdata="$base_dir" \
  --format=plain \
  --wal-method=stream \
  --checkpoint=fast \
  --manifest-checksums=SHA256 \
  --progress \
  --verbose

if find "$base_dir/pg_tblspc" -mindepth 1 -maxdepth 1 -type l -print -quit 2>/dev/null | grep -q .; then
  die "external PostgreSQL tablespaces require explicit restore mappings and are not accepted by this backup profile"
fi

pg_verifybackup "$base_dir"
after_lsn="$(psql "$connection" -AtX -v ON_ERROR_STOP=1 -c 'SELECT pg_current_wal_lsn()' 2>/dev/null || printf unknown)"

archive="${work}/basebackup.tar.age"
tar --numeric-owner -C "$base_dir" -cf - . \
  | age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$archive"
sha256sum "$archive" | awk '{print $1}' >"${archive}.sha256"
archive_size="$(stat -c %s "$archive")"
completed_epoch="$(date +%s)"
completed_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
duration="$((completed_epoch - started_epoch))"

prefix="${S3_PREFIX:-switching}/base/${started_at:0:4}/${started_at:5:2}/${started_at:8:2}/${backup_id}"
cat >"${work}/metadata.json" <<EOF_META
{
  "schemaVersion": 1,
  "backupId": "${backup_id}",
  "database": "${PGDATABASE:-switching_db}",
  "postgresMajor": 16,
  "systemIdentifier": "${system_identifier}",
  "startedAt": "${started_at}",
  "completedAt": "${completed_at}",
  "durationSeconds": ${duration},
  "startLsn": "${before_lsn}",
  "endLsn": "${after_lsn}",
  "archiveBytes": ${archive_size},
  "encryption": "age",
  "archiveKey": "${prefix}/basebackup.tar.age",
  "checksumKey": "${prefix}/basebackup.tar.age.sha256"
}
EOF_META
jq -e . "${work}/metadata.json" >/dev/null

s3_upload_required_targets "$archive" "${prefix}/basebackup.tar.age"
s3_upload_required_targets "${archive}.sha256" "${prefix}/basebackup.tar.age.sha256"
s3_upload_required_targets "${work}/metadata.json" "${prefix}/metadata.json"

cat >"${work}/latest.json" <<EOF_LATEST
{"backupId":"${backup_id}","completedAt":"${completed_at}","metadataKey":"${prefix}/metadata.json"}
EOF_LATEST
s3_publish_latest "${work}/latest.json" "${S3_PREFIX:-switching}/base/latest.json"

metrics="${work}/metrics.prom"
cat >"$metrics" <<EOF_METRICS
# TYPE switching_backup_last_success_timestamp_seconds gauge
switching_backup_last_success_timestamp_seconds{database="${PGDATABASE:-switching_db}"} ${completed_epoch}
# TYPE switching_backup_last_duration_seconds gauge
switching_backup_last_duration_seconds{database="${PGDATABASE:-switching_db}"} ${duration}
# TYPE switching_backup_last_size_bytes gauge
switching_backup_last_size_bytes{database="${PGDATABASE:-switching_db}"} ${archive_size}
# TYPE switching_backup_last_attempt_failed gauge
switching_backup_last_attempt_failed{database="${PGDATABASE:-switching_db}"} 0
# TYPE switching_backup_cross_region_success gauge
switching_backup_cross_region_success{database="${PGDATABASE:-switching_db}"} $(is_true "${SECONDARY_S3_ENABLED:-false}" && printf 1 || printf 0)
EOF_METRICS
publish_metrics switching_backup "$metrics"

log INFO "base backup completed id=${backup_id} duration_seconds=${duration} bytes=${archive_size}"
