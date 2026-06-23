#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66F" "Backup, PITR and restore execution"
STATUS="FAIL"; MESSAGE="backup/PITR certification failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
for f in backup/bin/full-backup.sh backup/bin/verify-backup.sh backup/bin/restore-drill.sh; do phase66_require_file "$f"; done
if ! phase66_is_full; then
  phase66_run "validate backup result schema" python3 -m json.tool schemas/phase66/backup-result.schema.json
  STATUS="PREPARED"; MESSAGE="backup/verify/restore chain is ready; no backup or restore executed"; exit 0
fi
phase66_require_uat; phase66_require_destructive_confirmation
: "${PHASE66_BACKUP_COMMAND:?PHASE66_BACKUP_COMMAND is required (for example docker exec switching-backup full-backup.sh)}"
: "${PHASE66_VERIFY_BACKUP_COMMAND:?PHASE66_VERIFY_BACKUP_COMMAND is required}"
: "${PHASE66_RESTORE_COMMAND:?PHASE66_RESTORE_COMMAND is required and must target an isolated restore environment}"
start=$(date +%s)
phase66_capture "full backup" "$PHASE66_PHASE_DIR/full-backup.log" bash -Eeuo pipefail -c "$PHASE66_BACKUP_COMMAND"
phase66_capture "backup verification" "$PHASE66_PHASE_DIR/verify-backup.log" bash -Eeuo pipefail -c "$PHASE66_VERIFY_BACKUP_COMMAND"
restore_start=$(date +%s)
phase66_capture "isolated restore drill" "$PHASE66_PHASE_DIR/restore-drill.log" bash -Eeuo pipefail -c "$PHASE66_RESTORE_COMMAND"
finish=$(date +%s)
python3 - "$PHASE66_PHASE_DIR/backup-result.json" "$start" "$restore_start" "$finish" <<'PY'
import json,sys,datetime
out,start,restore,finish=sys.argv[1],*map(int,sys.argv[2:])
doc={'schemaVersion':1,'generatedAt':datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z'),'backupDurationSeconds':restore-start,'restoreDurationSeconds':finish-restore,'rtoMinutes':round((finish-restore)/60,2),'status':'PASS'}
open(out,'w').write(json.dumps(doc,indent=2,sort_keys=True)+'\n')
PY
STATUS="PASS"; MESSAGE="backup, verification and isolated restore drill completed"
