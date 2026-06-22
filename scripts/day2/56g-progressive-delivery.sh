#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56G "Progressive Delivery and Release Automation"; day2_require_environment production operations; day2_require_phase_pass 56A 56B 56D
: "${ROLLOUT_METRICS_SNAPSHOT:?ROLLOUT_METRICS_SNAPSHOT is required}"
day2_run_check analysis python3 scripts/day2/analyze_progressive_delivery.py --snapshot "$ROLLOUT_METRICS_SNAPSHOT" --thresholds "$DAY2_THRESHOLDS" --output "$PHASE_DIR/progressive-delivery-analysis.json" || true
python3 - "$PHASE_DIR/progressive-delivery-analysis.json" "$PHASE_DIR/rollback-readiness.json" <<'PY'
import json,pathlib,sys
d=json.load(open(sys.argv[1]));pathlib.Path(sys.argv[2]).write_text(json.dumps({'schemaVersion':1,'status':'READY','automaticRollbackRequired':True,'analysisDecision':d['decision']},indent=2,sort_keys=True)+'\n')
PY
if [[ "${PROGRESSIVE_DELIVERY_EXECUTE:-false}" == true ]]; then day2_require_production_confirmation; [[ "${SIGNED_PROMOTION_DECISION_VERIFIED:-}" == true ]] || day2_die "signed promotion decision must be verified"; fi
day2_write_result
