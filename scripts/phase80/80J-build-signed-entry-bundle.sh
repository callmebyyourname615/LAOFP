#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80J
python3 scripts/phase80/build_bundle.py "$PHASE80_EVIDENCE_ROOT" "$PHASE80_MODE"
decision="$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["decision"])' "$PHASE80_EVIDENCE_ROOT/FINAL_DECISION.json")"
case "$decision" in
  GO_PHASE54|GO_PRODUCTION_CANARY) phase80_emit PASS "$decision" ;;
  PREPARED) phase80_emit PREPARED 'preflight bundle complete; runtime evidence absent' ;;
  *) phase80_emit BLOCKED "$decision"; [[ "$PHASE80_MODE" != full ]] ;;
esac
