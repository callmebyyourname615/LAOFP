#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63E 'Backup, verification and point-in-time recovery execution'
STATUS=FAIL; MESSAGE='backup/PITR execution failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
for file in backup/bin/{full-backup,verify-backup,restore-drill}.sh; do phase63_require_file "$file"; done
phase63_require_file scripts/phase63/verify_backup_pitr_attestation.py
if ! phase63_is_full; then
  STATUS=PREPARED; MESSAGE='full backup, checksum verification, row-count validation and PITR drill hooks are ready'; exit 0
fi
phase63_require_uat_confirmation
: "${PHASE63_BACKUP_PITR_ATTESTATION:?PHASE63_BACKUP_PITR_ATTESTATION is required}"
phase63_require_attestation "$PHASE63_BACKUP_PITR_ATTESTATION"
backup_dir="$PHASE63_PHASE_DIR/runtime"
mkdir -p "$backup_dir"
phase63_controlled_command full-backup "${PHASE63_FULL_BACKUP_COMMAND:-}" "$backup_dir/full-backup.log"
phase63_controlled_command verify-backup "${PHASE63_VERIFY_BACKUP_COMMAND:-}" "$backup_dir/verify-backup.log"
phase63_controlled_command pitr-restore "${PHASE63_PITR_COMMAND:-}" "$backup_dir/pitr-restore.log"
phase63_run 'backup/PITR evidence verification' python3 scripts/phase63/verify_backup_pitr_attestation.py \
  --attestation "$PHASE63_BACKUP_PITR_ATTESTATION" --evidence-dir "$backup_dir" \
  --output "$PHASE63_PHASE_DIR/backup-pitr-verification.json"
STATUS=PASS; MESSAGE='backup integrity, row counts and PITR met RPO below 5 minutes and RTO below 30 minutes'
