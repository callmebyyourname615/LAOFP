#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh
# shellcheck source=s3.sh
source /opt/switching-backup/bin/s3.sh

require_env S3_BUCKET
policy="${BACKUP_LIFECYCLE_FILE:-/opt/switching-backup/config/s3-lifecycle.json}"
require_file "$policy"
jq -e . "$policy" >/dev/null
aws_for_target primary s3api put-bucket-lifecycle-configuration \
  --bucket "$S3_BUCKET" --lifecycle-configuration "file://${policy}"
if is_true "${SECONDARY_S3_ENABLED:-false}"; then
  require_env SECONDARY_S3_BUCKET
  aws_for_target secondary s3api put-bucket-lifecycle-configuration \
    --bucket "$SECONDARY_S3_BUCKET" --lifecycle-configuration "file://${policy}"
fi
log INFO "backup retention lifecycle applied"
