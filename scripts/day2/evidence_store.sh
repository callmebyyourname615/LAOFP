#!/usr/bin/env bash
set -Eeuo pipefail

MODE="${1:-}"
case "$MODE" in pull|push|verify) ;; *) echo "usage: $0 <pull|push|verify>" >&2; exit 64;; esac

: "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
: "${PHASE56_EVIDENCE_BUCKET:?PHASE56_EVIDENCE_BUCKET is required}"
: "${PHASE56_EVIDENCE_PREFIX:?PHASE56_EVIDENCE_PREFIX is required}"
PHASE56_EVIDENCE_LOCAL_ROOT="${PHASE56_EVIDENCE_LOCAL_ROOT:-build}"

[[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || { echo "invalid RELEASE_REFERENCE" >&2; exit 64; }
[[ "$PHASE56_EVIDENCE_BUCKET" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]] || { echo "invalid PHASE56_EVIDENCE_BUCKET" >&2; exit 64; }
[[ "$PHASE56_EVIDENCE_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9/_=-]{0,255}$ ]] || { echo "invalid PHASE56_EVIDENCE_PREFIX" >&2; exit 64; }
[[ "$PHASE56_EVIDENCE_PREFIX" != *".."* && "$PHASE56_EVIDENCE_PREFIX" != /* ]] || { echo "unsafe PHASE56_EVIDENCE_PREFIX" >&2; exit 64; }
command -v aws >/dev/null 2>&1 || { echo "aws CLI is required" >&2; exit 69; }

aws_args=()
if [[ -n "${PHASE56_S3_ENDPOINT_URL:-}" ]]; then
  [[ "$PHASE56_S3_ENDPOINT_URL" =~ ^https:// ]] || { echo "PHASE56_S3_ENDPOINT_URL must use HTTPS" >&2; exit 64; }
  aws_args+=(--endpoint-url "$PHASE56_S3_ENDPOINT_URL")
fi
remote="s3://${PHASE56_EVIDENCE_BUCKET}/${PHASE56_EVIDENCE_PREFIX%/}/${RELEASE_REFERENCE}/"

verify_store() {
  local lock_json versioning
  versioning="$(aws "${aws_args[@]}" s3api get-bucket-versioning --bucket "$PHASE56_EVIDENCE_BUCKET" --output json)"
  python3 - "$versioning" <<'PY'
import json,sys
value=json.loads(sys.argv[1]).get('Status')
if value!='Enabled': raise SystemExit('evidence bucket versioning must be Enabled')
PY
  lock_json="$(aws "${aws_args[@]}" s3api get-object-lock-configuration --bucket "$PHASE56_EVIDENCE_BUCKET" --output json)"
  python3 - "$lock_json" <<'PY'
import json,sys
cfg=json.loads(sys.argv[1]).get('ObjectLockConfiguration',{})
rule=cfg.get('Rule',{}).get('DefaultRetention',{})
if cfg.get('ObjectLockEnabled')!='Enabled': raise SystemExit('evidence bucket Object Lock must be Enabled')
if rule.get('Mode')!='COMPLIANCE': raise SystemExit('evidence bucket default retention must use COMPLIANCE mode')
if not (rule.get('Days') or rule.get('Years')): raise SystemExit('evidence bucket default retention duration is missing')
PY
}

case "$MODE" in
  verify)
    verify_store
    ;;
  pull)
    verify_store
    mkdir -p "$PHASE56_EVIDENCE_LOCAL_ROOT"
    aws "${aws_args[@]}" s3 sync "$remote" "$PHASE56_EVIDENCE_LOCAL_ROOT/" \
      --only-show-errors --no-follow-symlinks \
      --exclude "*" \
      --include "phase54-certification/*" --include "phase54-certification/**" \
      --include "phase55-golive/*" --include "phase55-golive/**" \
      --include "phase56-day2/*" --include "phase56-day2/**" \
      --include "phase56-inputs/*" --include "phase56-inputs/**"
    ;;
  push)
    verify_store
    : "${PHASE56_EVIDENCE_KMS_KEY_ID:?PHASE56_EVIDENCE_KMS_KEY_ID is required for push}"
    [[ -d "$PHASE56_EVIDENCE_LOCAL_ROOT" ]] || { echo "evidence root does not exist: $PHASE56_EVIDENCE_LOCAL_ROOT" >&2; exit 66; }
    find "$PHASE56_EVIDENCE_LOCAL_ROOT" -type l -print -quit | grep -q . && { echo "symlinks are prohibited in evidence root" >&2; exit 65; }
    if [[ -x scripts/certification/scan_evidence.py || -f scripts/certification/scan_evidence.py ]]; then
      python3 scripts/certification/scan_evidence.py --root "$PHASE56_EVIDENCE_LOCAL_ROOT" --output "$PHASE56_EVIDENCE_LOCAL_ROOT/phase56-day2/evidence-secret-scan.json"
    fi
    aws "${aws_args[@]}" s3 sync "$PHASE56_EVIDENCE_LOCAL_ROOT/" "$remote" \
      --only-show-errors --no-follow-symlinks \
      --exclude "*" \
      --include "phase54-certification/*" --include "phase54-certification/**" \
      --include "phase55-golive/*" --include "phase55-golive/**" \
      --include "phase56-day2/*" --include "phase56-day2/**" \
      --include "phase56-inputs/*" --include "phase56-inputs/**" \
      --sse aws:kms --sse-kms-key-id "$PHASE56_EVIDENCE_KMS_KEY_ID"
    ;;
esac
