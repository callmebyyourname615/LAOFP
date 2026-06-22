#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56D "Capacity Management and Autoscaling"; day2_require_environment production operations
: "${CAPACITY_SNAPSHOT:?CAPACITY_SNAPSHOT is required}"; cp "$CAPACITY_SNAPSHOT" "$PHASE_DIR/capacity-snapshot.json"
day2_run_check capacity python3 scripts/day2/verify_capacity_policy.py --snapshot "$PHASE_DIR/capacity-snapshot.json" --policy capacity/capacity-policy.yaml --output "$PHASE_DIR/capacity-report.json" || true
python3 - "$PHASE_DIR/capacity-report.json" "$PHASE_DIR/autoscaling-safeguards.json" <<'PY'
import json,sys,pathlib
d=json.load(open(sys.argv[1])); pathlib.Path(sys.argv[2]).write_text(json.dumps({'schemaVersion':1,'status':d['status'],'safeguards':['db-connection-budget','consumer-partition-bound','jvm-headroom','storage-forecast']},indent=2,sort_keys=True)+'\n')
PY
day2_write_result
