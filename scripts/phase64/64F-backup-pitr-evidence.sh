#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64F" "Backup, restore and PITR evidence"
STATUS=FAIL; MESSAGE="backup/PITR evidence certification failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/validate_backup_attestation.py
phase64_require_file docs/templates/PHASE64_BACKUP_PITR_ATTESTATION.example.json
if phase64_is_preflight; then
  phase64_run "validate backup attestation template JSON" python3 -m json.tool docs/templates/PHASE64_BACKUP_PITR_ATTESTATION.example.json
  STATUS=PREPARED; MESSAGE="backup checksum, restore, PITR, RPO and RTO evidence contract is ready"; exit 0
fi
phase64_require_release_identity
: "${BACKUP_PITR_ATTESTATION:?BACKUP_PITR_ATTESTATION is required}"
phase64_require_file "$BACKUP_PITR_ATTESTATION"
manifest="$PHASE64_RUN_DIR/64C/runtime/manifest.json"
phase64_run "certify runtime backup/restore control" python3 scripts/phase64/extract_runtime_controls.py \
  --manifest "$manifest" --category backup-restore --required backup-restore-drill \
  --output "$PHASE64_PHASE_DIR/runtime-backup-control.json"
cp "$BACKUP_PITR_ATTESTATION" "$PHASE64_PHASE_DIR/backup-pitr-attestation.json"
phase64_run "validate backup/PITR objectives" python3 scripts/phase64/validate_backup_attestation.py \
  --config "$PHASE64_CONFIG" --attestation "$PHASE64_PHASE_DIR/backup-pitr-attestation.json" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --output "$PHASE64_PHASE_DIR/backup-pitr-summary.json"
STATUS=PASS; MESSAGE="backup integrity, restore, PITR, RPO and RTO evidence passed"
