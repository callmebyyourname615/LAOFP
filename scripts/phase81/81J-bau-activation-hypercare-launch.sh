#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81J
python3 scripts/phase81/build_activation_decision.py "$PHASE81_EVIDENCE_ROOT" "${PHASE80_FINAL_DECISION:-}" "$PHASE81_MODE"
decision="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["decision"])' "$PHASE81_EVIDENCE_ROOT/FINAL_DECISION.json")"
if [[ "$decision" == BAU_ACTIVE ]]; then phase81_emit PASS "$decision"
elif [[ "$decision" == PREPARED ]]; then phase81_emit PREPARED "$decision"
else phase81_emit BLOCKED "$decision"; [[ "$PHASE81_MODE" != full ]]; fi
