#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh

require_command age
require_command jq
require_command pg_verifybackup
require_env S3_BUCKET BACKUP_AGE_IDENTITY_FILE
require_file "$BACKUP_AGE_IDENTITY_FILE"

target_dir="${RESTORE_TARGET_DIR:-/var/lib/postgresql/restored-data}"
backup_id="${BACKUP_ID:-latest}"
recovery_target_time="${RECOVERY_TARGET_TIME:-}"
[[ "$target_dir" != "/" && -n "$target_dir" ]] || die "unsafe restore target directory"
if [[ -e "$target_dir" && -n "$(find "$target_dir" -mindepth 1 -maxdepth 1 -print -quit 2>/dev/null)" ]]; then
  die "restore target directory is not empty: ${target_dir}"
fi
mkdir -p "$target_dir"
chmod 0700 "$target_dir"

work="$(mktemp -d "${BACKUP_WORK_DIR:-/var/lib/switching-backup/work}/restore.XXXXXX")"
trap 'cleanup_dir "$work"' EXIT

if [[ "$backup_id" == "latest" ]]; then
  s3_download_with_fallback "${S3_PREFIX:-switching}/base/latest.json" "${work}/latest.json" \
    || die "cannot download latest backup pointer"
  metadata_key="$(jq -er '.metadataKey' "${work}/latest.json")"
else
  validate_safe_token "$backup_id" BACKUP_ID
  metadata_key="$(aws_for_target primary s3api list-objects-v2 \
    --bucket "$S3_BUCKET" --prefix "${S3_PREFIX:-switching}/base/" \
    --query "Contents[?contains(Key, '/${backup_id}/metadata.json')].Key | [0]" --output text)"
  [[ -n "$metadata_key" && "$metadata_key" != None ]] || die "backup id not found: ${backup_id}"
fi

s3_download_with_fallback "$metadata_key" "${work}/metadata.json" || die "cannot download backup metadata"
archive_key="$(jq -er '.archiveKey' "${work}/metadata.json")"
checksum_key="$(jq -er '.checksumKey' "${work}/metadata.json")"
restored_backup_id="$(jq -er '.backupId' "${work}/metadata.json")"

s3_download_with_fallback "$archive_key" "${work}/basebackup.tar.age" || die "cannot download base backup"
s3_download_with_fallback "$checksum_key" "${work}/basebackup.tar.age.sha256" || die "cannot download base backup checksum"
expected="$(tr -d '[:space:]' <"${work}/basebackup.tar.age.sha256")"
actual="$(sha256sum "${work}/basebackup.tar.age" | awk '{print $1}')"
[[ "$actual" == "$expected" ]] || die "base backup checksum mismatch id=${restored_backup_id}"

age --decrypt --identity "$BACKUP_AGE_IDENTITY_FILE" "${work}/basebackup.tar.age" \
  | tar --numeric-owner -C "$target_dir" -xf -
pg_verifybackup "$target_dir"
rm -f "$target_dir/standby.signal"
touch "$target_dir/recovery.signal"

restore_command="/opt/switching-backup/bin/restore-wal.sh %f %p"
{
  printf "restore_command = '%s'\n" "$restore_command"
  printf "recovery_target_timeline = 'latest'\n"
  printf "recovery_target_action = 'promote'\n"
  if [[ -n "$recovery_target_time" ]]; then
    [[ "$recovery_target_time" =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z$ ]] \
      || die "RECOVERY_TARGET_TIME must be UTC RFC3339 seconds, e.g. 2026-06-18T03:00:00Z"
    printf "recovery_target_time = '%s'\n" "$recovery_target_time"
    printf "recovery_target_inclusive = true\n"
  fi
} >>"$target_dir/postgresql.auto.conf"
chmod 0600 "$target_dir/postgresql.auto.conf"

cat >"$target_dir/.switching-restore-metadata.json" <<EOF_META
{"backupId":"${restored_backup_id}","metadataKey":"${metadata_key}","recoveryTargetTime":"${recovery_target_time}"}
EOF_META
log INFO "base backup restored id=${restored_backup_id} target=${target_dir}"
