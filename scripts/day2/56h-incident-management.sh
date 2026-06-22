#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56H "Operational Incident Management"; day2_require_environment production operations
: "${INCIDENT_RECORD:?INCIDENT_RECORD is required}"; day2_run_check incident-audit python3 scripts/day2/manage_incident.py --incident "$INCIDENT_RECORD" --audit-only --output "$PHASE_DIR/incident-audit.json" || true
python3 - "$PHASE_DIR/incident-audit.json" "$PHASE_DIR/escalation-readiness.json" <<'PY'
import json,pathlib,sys
d=json.load(open(sys.argv[1]));pathlib.Path(sys.argv[2]).write_text(json.dumps({'schemaVersion':1,'status':d['status'],'policies':['incident/severity-policy.yaml','incident/escalation-policy.yaml','incident/ownership.yaml']},indent=2,sort_keys=True)+'\n')
PY
day2_write_result
