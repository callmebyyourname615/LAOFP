#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh
# shellcheck source=metrics.sh
source /opt/switching-backup/bin/metrics.sh

require_command age
require_command sha256sum
require_env S3_BUCKET BACKUP_AGE_RECIPIENT
spool="${WAL_SPOOL_DIR:-/var/lib/switching-backup/incoming}"
mkdir -p "$spool"

upload_wal_file() {
  local path="$1" name work key completed_epoch
  name="$(basename "$path")"
  validate_wal_name "$name" || return 0

  exec {lock_fd}>"${path}.lock"
  flock -n "$lock_fd" || return 0
  [[ -f "$path" ]] || return 0

  work="${path}.age.tmp"
  rm -f "$work" "${work}.sha256"
  if ! age --encrypt --recipient "$BACKUP_AGE_RECIPIENT" --output "$work" "$path"; then
    rm -f "$work" "${work}.sha256"
    return 1
  fi
  sha256sum "$work" | awk '{print $1}' >"${work}.sha256"
  key="${S3_PREFIX:-switching}/wal/${name:0:8}/${name}.age"

  if ! s3_upload_required_targets "$work" "$key" \
      || ! s3_upload_required_targets "${work}.sha256" "${key}.sha256"; then
    rm -f "$work" "${work}.sha256"
    return 1
  fi
  completed_epoch="$(date +%s)"

  rm -f "$path" "$work" "${work}.sha256" "${path}.lock"
  printf '%s %s\n' "$completed_epoch" "$name" >"${spool}/.last-upload"

  local metrics
  metrics="$(mktemp)"
  cat >"$metrics" <<EOF_METRICS
# TYPE switching_wal_archive_last_success_timestamp_seconds gauge
switching_wal_archive_last_success_timestamp_seconds{database="${PGDATABASE:-switching_db}"} ${completed_epoch}
# TYPE switching_wal_archive_last_segment gauge
switching_wal_archive_last_segment{database="${PGDATABASE:-switching_db}",segment="${name}"} 1
# TYPE switching_wal_archive_failed gauge
switching_wal_archive_failed{database="${PGDATABASE:-switching_db}"} 0
EOF_METRICS
  publish_metrics switching_wal_archive "$metrics"
  rm -f "$metrics"
  log INFO "WAL archived segment=${name}"
}

if [[ "$#" -gt 0 ]]; then
  upload_wal_file "$1"
  exit 0
fi

while true; do
  found=0
  while IFS= read -r -d '' path; do
    found=1
    upload_wal_file "$path" || {
      publish_failure_metric switching_wal_archive switching_wal_archive_failed || true
      log ERROR "failed to archive WAL file=$(basename "$path")"
    }
  done < <(find "$spool" -regextype posix-extended -maxdepth 1 -type f \
    \( -regex '.*/[0-9A-F]{24}' \
       -o -regex '.*/[0-9A-F]{8}\.history' \
       -o -regex '.*/[0-9A-F]{24}\.[0-9A-F]{8}\.backup' \) -print0)
  [[ "$found" -eq 1 ]] || sleep "${WAL_UPLOAD_SCAN_SECONDS:-5}"
done
