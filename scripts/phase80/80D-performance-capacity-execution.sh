#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80D
scenarios=(smoke sustained2k sustained10k burst20k soak8h)
if phase80_full; then
  [[ "${PHASE80_ALLOW_LOAD_TESTS:-false}" == true ]] || { phase80_emit BLOCKED 'load-test confirmation missing'; exit 1; }
  phase80_require_env BASE_URL API_KEY || { phase80_emit BLOCKED 'performance endpoint/credential missing'; exit 1; }
  export RESULT_DIR="$PHASE80_EVIDENCE_ROOT/artifacts/performance" RUN_ID="$PHASE80_RUN_ID"
  for scenario in "${scenarios[@]}"; do phase80_run "k6-$scenario" performance/scripts/run-k6.sh "$scenario"; done
  [[ -z "${PHASE80_CAPACITY_CAPTURE_COMMAND:-}" ]] || bash -lc "$PHASE80_CAPACITY_CAPTURE_COMMAND" > "$PHASE80_EVIDENCE_ROOT/artifacts/capacity-snapshot.txt"
  python3 scripts/phase80/evaluate_performance.py "$RESULT_DIR/$RUN_ID" "$PHASE80_EVIDENCE_ROOT/artifacts/performance-decision.json"
  python3 - "$PHASE80_EVIDENCE_ROOT/artifacts/performance-decision.json" <<'PY'
import json,sys
assert json.load(open(sys.argv[1]))['passed'] is True
PY
  phase80_emit PASS 'mandatory performance scenarios passed strict thresholds'
else phase80_emit PREPARED 'performance campaign defined; load not started'; fi
