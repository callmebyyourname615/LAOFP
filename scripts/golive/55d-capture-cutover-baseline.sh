#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55D; live_require_environment production; live_require_production_confirmation
require_phase_pass 55A 55B 55C
[[ "${CUTOVER_BASELINE_CONFIRMATION:-}" == I_UNDERSTAND_THIS_READS_PRODUCTION_FINANCIAL_AGGREGATES ]] || live_die "cutover baseline confirmation missing"
: "${BASELINE_ARCHIVE_BUCKET:?BASELINE_ARCHIVE_BUCKET required}"; : "${BASELINE_ARCHIVE_PREFIX:?BASELINE_ARCHIVE_PREFIX required}"
: "${BASELINE_KMS_KEY_ID:?BASELINE_KMS_KEY_ID required}"; : "${BASELINE_RETENTION_UNTIL:?BASELINE_RETENTION_UNTIL required}"
live_require_command psql; live_require_command aws
phase_begin 55D "Data Reconciliation and Cutover Baseline"
failed=0
object_storage_endpoint_args=()
if [[ -n "${OBJECT_STORAGE_ENDPOINT:-}" ]]; then object_storage_endpoint_args=(--endpoint-url "$OBJECT_STORAGE_ENDPOINT"); fi
run_check retention-date-preflight python3 - "$BASELINE_RETENTION_UNTIL" <<'PYRET' || failed=1
import datetime as dt,sys
try:
    value=dt.datetime.fromisoformat(sys.argv[1].replace('Z','+00:00'))
    now=dt.datetime.now(dt.timezone.utc)
    if value.tzinfo is None or value <= now + dt.timedelta(days=30):
        raise ValueError('retention must be timezone-aware and at least 30 days in the future')
except Exception as exc:
    raise SystemExit(f'invalid BASELINE_RETENTION_UNTIL: {exc}')
PYRET
run_check production-read-only-transaction bash -c 'export PGPASSWORD="$DB_PASSWORD"; psql "${DB_URL#jdbc:}" -X -v ON_ERROR_STOP=1 -c "BEGIN READ ONLY; SELECT current_database(); ROLLBACK;"' || failed=1
run_check capture-baseline python3 scripts/golive/capture_reconciliation.py --output "$PHASE_DIR/cutover-baseline.json" --label production-pre-cutover --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" || failed=1
if (( failed )) || [[ ! -f "$PHASE_DIR/cutover-baseline.json" ]]; then write_phase_result FAIL; exit 1; fi
live_write_checksum "$PHASE_DIR/cutover-baseline.json" "$PHASE_DIR/cutover-baseline.sha256"
chmod 0444 "$PHASE_DIR/cutover-baseline.json" "$PHASE_DIR/cutover-baseline.sha256"
baseline_hash=$(live_sha256_file "$PHASE_DIR/cutover-baseline.json")
archive_key="${BASELINE_ARCHIVE_PREFIX%/}/${RELEASE_RC_ID}/cutover-baseline.json"
run_check archive-baseline aws "${object_storage_endpoint_args[@]}" s3api put-object --bucket "$BASELINE_ARCHIVE_BUCKET" --key "$archive_key" \
  --body "$PHASE_DIR/cutover-baseline.json" --server-side-encryption aws:kms --ssekms-key-id "$BASELINE_KMS_KEY_ID" \
  --object-lock-mode COMPLIANCE --object-lock-retain-until-date "$BASELINE_RETENTION_UNTIL" \
  --metadata "sha256=$baseline_hash" --output json || failed=1
run_check verify-archive-retention bash -c '
  endpoint="$1"; bucket="$2"; key="$3"; receipt="$4"
  args=(); if [[ -n "$endpoint" ]]; then args=(--endpoint-url "$endpoint"); fi
  aws "${args[@]}" s3api get-object-retention --bucket "$bucket" --key "$key" --output json > "$receipt"
  grep -q '''"Mode"[[:space:]]*:[[:space:]]*"COMPLIANCE"''' "$receipt"
' _ "${OBJECT_STORAGE_ENDPOINT:-}" "$BASELINE_ARCHIVE_BUCKET" "$archive_key" "$PHASE_DIR/archive-receipt.json" || failed=1
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
