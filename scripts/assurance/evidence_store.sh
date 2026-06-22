#!/usr/bin/env bash
set -Eeuo pipefail
[[ $# -eq 2 ]] || { echo "usage: $0 <evidence-root> <release-reference>" >&2; exit 2; }
root="$1"; release="$2"
: "${ASSURANCE_EVIDENCE_BUCKET:?ASSURANCE_EVIDENCE_BUCKET is required}"
: "${ASSURANCE_EVIDENCE_KMS_KEY_ID:?ASSURANCE_EVIDENCE_KMS_KEY_ID is required}"
: "${ASSURANCE_EVIDENCE_RETENTION_UNTIL:?ASSURANCE_EVIDENCE_RETENTION_UNTIL is required}"
[[ "$release" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$ ]] || { echo "invalid release reference" >&2; exit 1; }
[[ -d "$root" && ! -L "$root" ]] || { echo "unsafe evidence root" >&2; exit 1; }
command -v aws >/dev/null 2>&1 || { echo "aws CLI is required" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 1; }
python3 scripts/security/scan_repository_secrets.py --path "$root" --mode evidence
versioning="$(aws s3api get-bucket-versioning --bucket "$ASSURANCE_EVIDENCE_BUCKET" --query Status --output text)"
[[ "$versioning" == Enabled ]] || { echo "evidence bucket versioning is not enabled" >&2; exit 1; }
lock="$(aws s3api get-object-lock-configuration --bucket "$ASSURANCE_EVIDENCE_BUCKET" --query 'ObjectLockConfiguration.ObjectLockEnabled' --output text)"
[[ "$lock" == Enabled ]] || { echo "evidence bucket Object Lock is not enabled" >&2; exit 1; }
while IFS= read -r -d '' file; do
  rel="${file#"$root"/}"; [[ "$rel" != "$file" && "$rel" != *'..'* ]] || { echo "unsafe evidence path" >&2; exit 1; }
  aws s3api put-object --bucket "$ASSURANCE_EVIDENCE_BUCKET" --key "phase58/$release/$rel" --body "$file" --server-side-encryption aws:kms --ssekms-key-id "$ASSURANCE_EVIDENCE_KMS_KEY_ID" --object-lock-mode COMPLIANCE --object-lock-retain-until-date "$ASSURANCE_EVIDENCE_RETENTION_UNTIL" >/dev/null
done < <(find "$root" -type f -not -type l -print0 | sort -z)
echo "Phase 58 evidence uploaded with Object Lock COMPLIANCE and SSE-KMS"
