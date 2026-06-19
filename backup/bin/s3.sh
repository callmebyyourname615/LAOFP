#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh

s3_uri() {
  local bucket="$1" key="$2"
  printf 's3://%s/%s' "$bucket" "${key#/}"
}

aws_for_target() {
  local target="$1"
  shift

  local endpoint region access_key secret_key session_token ca_bundle
  case "$target" in
    primary)
      endpoint="${S3_ENDPOINT:-}"
      region="${S3_REGION:-us-east-1}"
      access_key="${AWS_ACCESS_KEY_ID:-}"
      secret_key="${AWS_SECRET_ACCESS_KEY:-}"
      session_token="${AWS_SESSION_TOKEN:-}"
      ca_bundle="${AWS_CA_BUNDLE:-}"
      ;;
    secondary)
      endpoint="${SECONDARY_S3_ENDPOINT:-}"
      region="${SECONDARY_S3_REGION:-us-east-1}"
      access_key="${SECONDARY_AWS_ACCESS_KEY_ID:-}"
      secret_key="${SECONDARY_AWS_SECRET_ACCESS_KEY:-}"
      session_token="${SECONDARY_AWS_SESSION_TOKEN:-}"
      ca_bundle="${SECONDARY_AWS_CA_BUNDLE:-}"
      ;;
    *) die "unknown S3 target: ${target}" ;;
  esac

  [[ -n "$access_key" && -n "$secret_key" ]] || die "${target} S3 credentials are missing"

  local args=(--region "$region")
  [[ -n "$endpoint" ]] && args+=(--endpoint-url "$endpoint")
  [[ -n "$ca_bundle" ]] && args+=(--ca-bundle "$ca_bundle")

  AWS_ACCESS_KEY_ID="$access_key" \
  AWS_SECRET_ACCESS_KEY="$secret_key" \
  AWS_SESSION_TOKEN="$session_token" \
  AWS_DEFAULT_REGION="$region" \
    aws "${args[@]}" "$@"
}

s3_upload_one() {
  local target="$1" source="$2" bucket="$3" key="$4"
  local destination
  destination="$(s3_uri "$bucket" "$key")"
  local args=(s3 cp "$source" "$destination" --only-show-errors)
  if [[ -n "${S3_STORAGE_CLASS:-}" ]]; then
    args+=(--storage-class "$S3_STORAGE_CLASS")
  fi
  if [[ "${S3_SERVER_SIDE_ENCRYPTION:-none}" == "AES256" ]]; then
    args+=(--sse AES256)
  elif [[ "${S3_SERVER_SIDE_ENCRYPTION:-none}" == "aws:kms" ]]; then
    require_env S3_KMS_KEY_ID
    args+=(--sse aws:kms --sse-kms-key-id "$S3_KMS_KEY_ID")
  fi
  aws_for_target "$target" "${args[@]}"
}

s3_upload_required_targets() {
  local source="$1" key="$2"
  require_env S3_BUCKET
  s3_upload_one primary "$source" "$S3_BUCKET" "$key"

  if is_true "${SECONDARY_S3_ENABLED:-false}"; then
    require_env SECONDARY_S3_BUCKET SECONDARY_AWS_ACCESS_KEY_ID SECONDARY_AWS_SECRET_ACCESS_KEY
    s3_upload_one secondary "$source" "$SECONDARY_S3_BUCKET" "$key"
  fi
}

s3_publish_latest() {
  local source="$1" key="$2"
  require_env S3_BUCKET
  # Publish the secondary pointer first. The primary latest pointer is the
  # authoritative commit marker and is updated only after the off-site copy.
  if is_true "${SECONDARY_S3_ENABLED:-false}"; then
    require_env SECONDARY_S3_BUCKET SECONDARY_AWS_ACCESS_KEY_ID SECONDARY_AWS_SECRET_ACCESS_KEY
    s3_upload_one secondary "$source" "$SECONDARY_S3_BUCKET" "$key"
  fi
  s3_upload_one primary "$source" "$S3_BUCKET" "$key"
}

s3_download_with_fallback() {
  local key="$1" destination="$2"
  require_env S3_BUCKET
  if aws_for_target primary s3 cp "$(s3_uri "$S3_BUCKET" "$key")" "$destination" --only-show-errors; then
    return 0
  fi

  if is_true "${SECONDARY_S3_ENABLED:-false}" && [[ -n "${SECONDARY_S3_BUCKET:-}" ]]; then
    log WARN "primary object unavailable; trying secondary key=${key}"
    aws_for_target secondary s3 cp "$(s3_uri "$SECONDARY_S3_BUCKET" "$key")" "$destination" --only-show-errors
    return $?
  fi
  return 1
}

s3_object_exists() {
  local target="$1" bucket="$2" key="$3"
  aws_for_target "$target" s3api head-object --bucket "$bucket" --key "$key" >/dev/null 2>&1
}
