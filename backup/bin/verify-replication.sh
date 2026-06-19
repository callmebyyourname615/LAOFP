#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh
# shellcheck source=metrics.sh
source /opt/switching-backup/bin/metrics.sh

is_true "${SECONDARY_S3_ENABLED:-false}" || die "secondary S3 is not enabled"
require_env S3_BUCKET SECONDARY_S3_BUCKET
work="$(mktemp -d "${BACKUP_WORK_DIR:-/var/lib/switching-backup/work}/replication.XXXXXX")"
trap 'cleanup_dir "$work"' EXIT
s3_download_with_fallback "${S3_PREFIX:-switching}/base/latest.json" "${work}/latest.json"
metadata_key="$(jq -er '.metadataKey' "${work}/latest.json")"
archive_key="$(aws_for_target primary s3 cp "$(s3_uri "$S3_BUCKET" "$metadata_key")" - --only-show-errors | jq -er '.archiveKey')"

success=1
s3_object_exists secondary "$SECONDARY_S3_BUCKET" "$metadata_key" || success=0
s3_object_exists secondary "$SECONDARY_S3_BUCKET" "$archive_key" || success=0
now="$(date +%s)"
cat >"${work}/metrics.prom" <<EOF_METRICS
# TYPE switching_backup_cross_region_success gauge
switching_backup_cross_region_success{database="${PGDATABASE:-switching_db}"} ${success}
# TYPE switching_backup_cross_region_last_check_timestamp_seconds gauge
switching_backup_cross_region_last_check_timestamp_seconds{database="${PGDATABASE:-switching_db}"} ${now}
EOF_METRICS
publish_metrics switching_backup_replication "${work}/metrics.prom"
[[ "$success" -eq 1 ]] || die "latest backup is missing from secondary object storage"
log INFO "cross-region backup copy verified"
