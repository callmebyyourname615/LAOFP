#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80B
[[ -x ./mvnw && -x scripts/execute-and-verify/00-run-all.sh ]] || { phase80_emit BLOCKED 'required build executors missing'; exit 1; }
if phase80_full; then
  phase80_run maven ./mvnw clean verify
  phase80_run repository-gates ./scripts/execute-and-verify/00-run-all.sh
  python3 scripts/phase80/collect_junit.py target "$PHASE80_EVIDENCE_ROOT/artifacts/junit-summary.json"
  python3 - "$PHASE80_EVIDENCE_ROOT/artifacts/junit-summary.json" <<'PY'
import json,sys
x=json.load(open(sys.argv[1]))
assert x['tests']>0 and x['failures']==0 and x['errors']==0
PY
  phase80_emit PASS 'Maven verify and repository gates passed with tests executed'
else phase80_emit PREPARED 'build verification commands validated; not executed in preflight'; fi
