#!/usr/bin/env bash
set -Eeuo pipefail
MODE="${1:-}"; case "$MODE" in pull|push|verify) ;; *) echo "usage: $0 <pull|push|verify>" >&2; exit 64;; esac
: "${RELEASE_REFERENCE:?RELEASE_REFERENCE is required}"
: "${PHASE57_EVIDENCE_BUCKET:?PHASE57_EVIDENCE_BUCKET is required}"
: "${PHASE57_EVIDENCE_PREFIX:?PHASE57_EVIDENCE_PREFIX is required}"
PHASE57_EVIDENCE_LOCAL_ROOT="${PHASE57_EVIDENCE_LOCAL_ROOT:-build}"
[[ "$RELEASE_REFERENCE" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || { echo "invalid RELEASE_REFERENCE" >&2; exit 64; }
[[ "$PHASE57_EVIDENCE_BUCKET" =~ ^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$ ]] || { echo "invalid evidence bucket" >&2; exit 64; }
[[ "$PHASE57_EVIDENCE_PREFIX" =~ ^[A-Za-z0-9][A-Za-z0-9/_=-]{0,255}$ && "$PHASE57_EVIDENCE_PREFIX" != *".."* && "$PHASE57_EVIDENCE_PREFIX" != /* ]] || { echo "unsafe evidence prefix" >&2; exit 64; }
command -v aws >/dev/null 2>&1 || { echo "aws CLI is required" >&2; exit 69; }
aws_args=(); if [[ -n "${PHASE57_S3_ENDPOINT_URL:-}" ]]; then [[ "$PHASE57_S3_ENDPOINT_URL" =~ ^https:// ]] || { echo "endpoint must use HTTPS" >&2; exit 64; }; aws_args+=(--endpoint-url "$PHASE57_S3_ENDPOINT_URL"); fi
remote="s3://${PHASE57_EVIDENCE_BUCKET}/${PHASE57_EVIDENCE_PREFIX%/}/${RELEASE_REFERENCE}/"
verify_store(){
  local v l
  v="$(aws "${aws_args[@]}" s3api get-bucket-versioning --bucket "$PHASE57_EVIDENCE_BUCKET" --output json)"
  python3 - "$v" <<'PY'
import json,sys
if json.loads(sys.argv[1]).get('Status')!='Enabled': raise SystemExit('bucket versioning must be Enabled')
PY
  l="$(aws "${aws_args[@]}" s3api get-object-lock-configuration --bucket "$PHASE57_EVIDENCE_BUCKET" --output json)"
  python3 - "$l" <<'PY'
import json,sys
c=json.loads(sys.argv[1]).get('ObjectLockConfiguration',{}); r=c.get('Rule',{}).get('DefaultRetention',{})
if c.get('ObjectLockEnabled')!='Enabled' or r.get('Mode')!='COMPLIANCE' or not (r.get('Days') or r.get('Years')): raise SystemExit('Object Lock COMPLIANCE default retention is required')
PY
}
case "$MODE" in
 verify) verify_store;;
 pull) verify_store; mkdir -p "$PHASE57_EVIDENCE_LOCAL_ROOT"; aws "${aws_args[@]}" s3 sync "$remote" "$PHASE57_EVIDENCE_LOCAL_ROOT/" --only-show-errors --no-follow-symlinks --exclude '*' --include 'phase54-certification/**' --include 'phase55-golive/**' --include 'phase56-day2/**' --include 'phase57-enterprise/**' --include 'phase57-inputs/**';;
 push) verify_store; : "${PHASE57_EVIDENCE_KMS_KEY_ID:?KMS key is required}"; [[ -d "$PHASE57_EVIDENCE_LOCAL_ROOT" ]] || { echo "evidence root missing" >&2; exit 66; }; find "$PHASE57_EVIDENCE_LOCAL_ROOT" -type l -print -quit | grep -q . && { echo "symlinks prohibited" >&2; exit 65; }; mkdir -p "$PHASE57_EVIDENCE_LOCAL_ROOT/phase57-enterprise"; python3 scripts/certification/scan_evidence.py --root "$PHASE57_EVIDENCE_LOCAL_ROOT" --output "$PHASE57_EVIDENCE_LOCAL_ROOT/phase57-enterprise/evidence-secret-scan.json"; aws "${aws_args[@]}" s3 sync "$PHASE57_EVIDENCE_LOCAL_ROOT/" "$remote" --only-show-errors --no-follow-symlinks --exclude '*' --include 'phase54-certification/**' --include 'phase55-golive/**' --include 'phase56-day2/**' --include 'phase57-enterprise/**' --include 'phase57-inputs/**' --sse aws:kms --sse-kms-key-id "$PHASE57_EVIDENCE_KMS_KEY_ID";;
esac
