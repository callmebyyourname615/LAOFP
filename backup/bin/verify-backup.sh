#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh

require_env S3_BUCKET
work="$(mktemp -d "${BACKUP_WORK_DIR:-/var/lib/switching-backup/work}/verify.XXXXXX")"
trap 'cleanup_dir "$work"' EXIT
s3_download_with_fallback "${S3_PREFIX:-switching}/base/latest.json" "${work}/latest.json"
metadata_key="$(jq -er '.metadataKey' "${work}/latest.json")"
s3_download_with_fallback "$metadata_key" "${work}/metadata.json"
archive_key="$(jq -er '.archiveKey' "${work}/metadata.json")"
checksum_key="$(jq -er '.checksumKey' "${work}/metadata.json")"

s3_object_exists primary "$S3_BUCKET" "$archive_key" || die "primary backup archive missing"
s3_object_exists primary "$S3_BUCKET" "$checksum_key" || die "primary backup checksum missing"
if is_true "${SECONDARY_S3_ENABLED:-false}"; then
  s3_object_exists secondary "$SECONDARY_S3_BUCKET" "$archive_key" || die "secondary backup archive missing"
  s3_object_exists secondary "$SECONDARY_S3_BUCKET" "$checksum_key" || die "secondary backup checksum missing"
fi
jq -e '.backupId and .completedAt and .archiveKey and .checksumKey' "${work}/metadata.json" >/dev/null
log INFO "backup metadata and required objects verified backup_id=$(jq -r .backupId "${work}/metadata.json")"
