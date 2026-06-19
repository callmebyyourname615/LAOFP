#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh

name="${1:-}"
destination="${2:-}"
[[ -n "$name" && -n "$destination" ]] || exit 2
validate_wal_name "$name" || exit 2
require_env S3_BUCKET BACKUP_AGE_IDENTITY_FILE
require_file "$BACKUP_AGE_IDENTITY_FILE"

work="$(mktemp -d "${TMPDIR:-/tmp}/restore-wal.XXXXXX")"
trap 'rm -f "${destination}.tmp"; cleanup_dir "$work"' EXIT
key="${S3_PREFIX:-switching}/wal/${name:0:8}/${name}.age"

if ! s3_download_with_fallback "$key" "${work}/${name}.age"; then
  log WARN "WAL object not found key=${key}"
  exit 1
fi
s3_download_with_fallback "${key}.sha256" "${work}/${name}.sha256" || exit 1
expected="$(tr -d '[:space:]' <"${work}/${name}.sha256")"
actual="$(sha256sum "${work}/${name}.age" | awk '{print $1}')"
[[ "$actual" == "$expected" ]] || die "WAL checksum mismatch segment=${name}"

mkdir -p "$(dirname "$destination")"
age --decrypt --identity "$BACKUP_AGE_IDENTITY_FILE" --output "${destination}.tmp" "${work}/${name}.age"
chmod 0600 "${destination}.tmp"
mv -f "${destination}.tmp" "$destination"
