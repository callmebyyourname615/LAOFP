#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80F
if phase80_full; then
  [[ "${PHASE80_ALLOW_RESTORE_DRILL:-false}" == true ]] || { phase80_emit BLOCKED 'restore confirmation missing'; exit 1; }
  if [[ -n "${PHASE80_BACKUP_RESTORE_COMMAND:-}" ]]; then bash -lc "$PHASE80_BACKUP_RESTORE_COMMAND" > "$PHASE80_EVIDENCE_ROOT/artifacts/backup-restore.log" 2>&1
  else phase80_run full-backup backup/bin/full-backup.sh; phase80_run verify-backup backup/bin/verify-backup.sh; phase80_run restore-drill backup/bin/restore-drill.sh; fi
  [[ -f "${PHASE80_BACKUP_ATTESTATION:-}" ]] || { phase80_emit BLOCKED 'backup attestation missing'; exit 1; }
  python3 scripts/phase80/validate_attestation.py "$PHASE80_BACKUP_ATTESTATION" backup "$PHASE80_EVIDENCE_ROOT/artifacts/backup-attestation.json"
  phase80_emit PASS 'backup, PITR and isolated restore attested'
else phase80_emit PREPARED 'backup/PITR hooks ready; no restore performed'; fi
