#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60I" "Backup, PITR, DR and alert evidence"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="resilience evidence certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_run "alert inventory" python3 monitoring/scripts/build-alert-inventory.py \
  --root . --output "$PHASE60_PHASE_DIR/alert-inventory.json"
for required in backup/bin/full-backup.sh backup/bin/verify-backup.sh backup/bin/restore-drill.sh \
  dr/scripts/run-dr-suite.sh monitoring/scripts/run-alert-routing-drill.py \
  scripts/monitoring/verify_alert_runbooks.py; do
  phase_require_file "$required"
done

if phase_is_preflight; then
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="backup, restore, DR and alert drill tooling is ready; no destructive UAT action was executed"
  exit 0
fi

phase_run "alert and runbook static validation" python3 scripts/monitoring/verify_alert_runbooks.py

phase_require_runtime_confirmation
phase_require_command docker
phase_require_command kubectl
: "${BACKUP_AGE_IDENTITY_FILE:?BACKUP_AGE_IDENTITY_FILE is required for restore drill}"
: "${ALERTMANAGER_URL:?ALERTMANAGER_URL is required}"
: "${RESILIENCE_ATTESTATION:?RESILIENCE_ATTESTATION must point to the completed resilience attestation}"

compose=(docker compose)
[[ -n "${COMPOSE_FILE:-}" ]] && compose+=(--file "$COMPOSE_FILE")

set +e
"${compose[@]}" --profile backup run --rm backup-full 2>&1 | tee "$PHASE60_PHASE_DIR/full-backup.log"
backup_code=${PIPESTATUS[0]}
set -e
[[ "$backup_code" -eq 0 ]] || { PHASE_MESSAGE="full backup failed"; exit "$backup_code"; }
phase_run "backup object and metadata verification" \
  "${compose[@]}" --profile backup run --rm \
  --entrypoint /opt/switching-backup/bin/verify-backup.sh backup-full

identity_abs="$(cd "$(dirname "$BACKUP_AGE_IDENTITY_FILE")" && pwd)/$(basename "$BACKUP_AGE_IDENTITY_FILE")"
set +e
"${compose[@]}" --profile backup run --rm \
  -e CERTIFICATION_EVIDENCE_STDOUT=true \
  -e BACKUP_AGE_IDENTITY_FILE=/run/secrets/backup-age-identity \
  -v "$identity_abs:/run/secrets/backup-age-identity:ro" \
  --entrypoint /opt/switching-backup/bin/restore-drill.sh backup-full \
  2>&1 | tee "$PHASE60_PHASE_DIR/restore-drill.log"
restore_code=${PIPESTATUS[0]}
set -e
[[ "$restore_code" -eq 0 ]] || { PHASE_MESSAGE="PITR restore drill failed"; exit "$restore_code"; }
phase_run "extract restore evidence" python3 scripts/phase60/extract_prefixed_json.py \
  --input "$PHASE60_PHASE_DIR/restore-drill.log" \
  --prefix CERTIFICATION_RESTORE_EVIDENCE= \
  --output "$PHASE60_PHASE_DIR/restore-evidence.json"

export DR_ENVIRONMENT=uat
export DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export EVIDENCE_DIR="$PHASE60_PHASE_DIR/dr"
phase_run "mandatory DR scenario suite" dr/scripts/run-dr-suite.sh \
  pod-kill kafka-fail net-partition s3-down ext-timeout deployment-rollback

alert_args=(
  --alertmanager-url "$ALERTMANAGER_URL"
  --run-id "$PHASE60_RUN_ID"
  --output-dir "$PHASE60_PHASE_DIR/alert-routing"
)
if [[ -n "${ALERTMANAGER_BEARER_TOKEN_FILE:-}" ]]; then
  alert_args+=(--bearer-token-file "$ALERTMANAGER_BEARER_TOKEN_FILE")
fi
phase_run "synthetic Alertmanager routing drill" python3 monitoring/scripts/run-alert-routing-drill.py "${alert_args[@]}"

phase_run "operator resilience attestation" python3 scripts/phase60/verify_resilience_attestation.py \
  --attestation "$RESILIENCE_ATTESTATION" \
  --alert-inventory "$PHASE60_PHASE_DIR/alert-inventory.json" \
  --output "$PHASE60_PHASE_DIR/resilience-attestation-verification.json"

PHASE_STATUS="PASS"
PHASE_MESSAGE="backup/PITR, six DR scenarios, zero-loss checks and Alertmanager routing evidence passed and were signed off"
