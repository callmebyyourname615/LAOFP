#!/usr/bin/env bash
set -Eeuo pipefail

: "${PHASE61_RESILIENCE_EVIDENCE_DIR:?PHASE61_RESILIENCE_EVIDENCE_DIR is required}"
: "${ALERTMANAGER_URL:?ALERTMANAGER_URL is required}"
mkdir -p "$PHASE61_RESILIENCE_EVIDENCE_DIR"/{backup,platform,dr,alerts}

run_controlled() {
  local name="$1" command_value="$2" output="$3"
  [[ -n "$command_value" ]] || { echo "Missing command hook for $name" >&2; return 64; }
  printf '%s\n' "command hook: $name" > "$output.command.txt"
  timeout "${PHASE61_DRILL_TIMEOUT:-3600}" bash -Eeuo pipefail -c "$command_value" > "$output" 2>&1
}

run_controlled full-backup "${PHASE61_FULL_BACKUP_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/backup/full-backup.log"
run_controlled verify-backup "${PHASE61_VERIFY_BACKUP_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/backup/verify-backup.log"
run_controlled pitr-restore "${PHASE61_PITR_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/backup/pitr-restore.log"
run_controlled postgres-primary-failover "${PHASE61_POSTGRES_FAILOVER_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/platform/postgres-primary-failover.log"
run_controlled postgres-failback "${PHASE61_POSTGRES_FAILBACK_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/platform/postgres-failback.log"
run_controlled vault-leader-fail "${PHASE61_VAULT_FAILOVER_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/platform/vault-leader-fail.log"
run_controlled object-storage-node-fail "${PHASE61_OBJECT_STORAGE_FAILOVER_COMMAND:-}" "$PHASE61_RESILIENCE_EVIDENCE_DIR/platform/object-storage-node-fail.log"

export DR_ENVIRONMENT=uat
export DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export EVIDENCE_DIR="$PHASE61_RESILIENCE_EVIDENCE_DIR/dr"
dr/scripts/run-dr-suite.sh pod-kill kafka-fail net-partition ext-timeout deployment-rollback

alert_args=(
  --alertmanager-url "$ALERTMANAGER_URL"
  --run-id "${PHASE61_RUN_ID:?PHASE61_RUN_ID is required}"
  --output-dir "$PHASE61_RESILIENCE_EVIDENCE_DIR/alerts"
)
if [[ -n "${ALERTMANAGER_BEARER_TOKEN_FILE:-}" ]]; then
  alert_args+=(--bearer-token-file "$ALERTMANAGER_BEARER_TOKEN_FILE")
fi
python3 monitoring/scripts/run-alert-routing-drill.py "${alert_args[@]}"

python3 - "$PHASE61_RESILIENCE_EVIDENCE_DIR" <<'PY'
from __future__ import annotations
import hashlib, json, pathlib, sys
root=pathlib.Path(sys.argv[1])
files=[]
for path in sorted(root.rglob('*')):
    if path.is_file() and path.name != 'phase61-resilience-evidence.json':
        files.append({'path':path.relative_to(root).as_posix(),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest()})
(root/'phase61-resilience-evidence.json').write_text(json.dumps({'schemaVersion':1,'files':files},indent=2,sort_keys=True)+'\n',encoding='utf-8')
PY
