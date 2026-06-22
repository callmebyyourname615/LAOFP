#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56E "Security Operations and Threat Detection"; day2_require_environment production security
: "${SECURITY_EVENTS_SNAPSHOT:?SECURITY_EVENTS_SNAPSHOT is required}"; cp "$SECURITY_EVENTS_SNAPSHOT" "$PHASE_DIR/security-event-summary.json"
day2_run_check detections python3 scripts/day2/evaluate_security_events.py --events "$PHASE_DIR/security-event-summary.json" --catalog-dir security/detections --output "$PHASE_DIR/detection-report.json" || true
python3 - "$PHASE_DIR/detection-report.json" "$PHASE_DIR/containment-readiness.json" <<'PY'
import json,pathlib,sys
d=json.load(open(sys.argv[1])); pathlib.Path(sys.argv[2]).write_text(json.dumps({'schemaVersion':1,'status':'PASS' if d['blockingDetectionCount']==0 else 'REQUIRES_INCIDENT','automaticDestructiveContainment':False,'playbooksPresent':all(pathlib.Path(x['runbook']).is_file() for x in d['detections'])},indent=2,sort_keys=True)+'\n')
PY
day2_write_result
