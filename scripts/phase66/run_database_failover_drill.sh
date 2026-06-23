#!/usr/bin/env bash
set -Eeuo pipefail
: "${DB_FAILOVER_COMMAND:?DB_FAILOVER_COMMAND is required}"
: "${DB_RECOVERY_CHECK_COMMAND:?DB_RECOVERY_CHECK_COMMAND is required}"
: "${EVIDENCE_DIR:?EVIDENCE_DIR is required}"
[[ "${DR_ENVIRONMENT:-}" == "uat" || "${DR_ENVIRONMENT:-}" == "dr" ]] || { echo "DR_ENVIRONMENT must be uat or dr" >&2; exit 64; }
[[ "${DR_CONFIRMATION:-}" == "I_UNDERSTAND_THIS_IS_DESTRUCTIVE" ]] || { echo "DR_CONFIRMATION missing" >&2; exit 64; }
mkdir -p "$EVIDENCE_DIR"
started=$(date +%s)
printf '%s\tdatabase-failover\tSTART\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$EVIDENCE_DIR/timeline.tsv"
bash -Eeuo pipefail -c "$DB_FAILOVER_COMMAND" > "$EVIDENCE_DIR/database-failover-command.log" 2>&1
bash -Eeuo pipefail -c "$DB_RECOVERY_CHECK_COMMAND" > "$EVIDENCE_DIR/database-failover-recovery.log" 2>&1
finished=$(date +%s)
python3 - "$EVIDENCE_DIR/database-failover.json" "$started" "$finished" <<'PYDBFAILOVER'
import json,sys,datetime
out,start,finish=sys.argv[1],int(sys.argv[2]),int(sys.argv[3])
doc={"schemaVersion":1,"scenario":"database-failover","status":"PASS","durationSeconds":finish-start,"finishedAt":datetime.datetime.now(datetime.timezone.utc).isoformat().replace('+00:00','Z')}
open(out,'w').write(json.dumps(doc,indent=2,sort_keys=True)+'\n')
PYDBFAILOVER
printf '%s\tdatabase-failover\tCOMPLETE\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$EVIDENCE_DIR/timeline.tsv"
