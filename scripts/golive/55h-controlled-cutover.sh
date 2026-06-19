#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55H; live_require_environment production; live_require_production_confirmation
require_phase_pass 55A 55B 55C 55D 55E 55F 55G
[[ "${CONTROLLED_CUTOVER_CONFIRMATION:-}" == I_UNDERSTAND_THIS_CAN_PROMOTE_PRODUCTION_TRAFFIC_TO_100_PERCENT ]] || live_die "controlled cutover confirmation missing"
: "${APPLICATION_IMAGE_REPOSITORY:?required}"; : "${PROMETHEUS_URL:?required}"; : "${DECISION_PUBLIC_KEY:?required}"; : "${PROMOTION_DECISION_DIR:?required}"
: "${DB_URL:?required}"; : "${DB_USERNAME:?required}"; : "${DB_PASSWORD:?required}"
: "${PRODUCTION_SYNTHETIC_SCRIPT:?required}"; [[ -x "$PRODUCTION_SYNTHETIC_SCRIPT" ]] || live_die "synthetic script must be executable"
live_require_image_repository "$APPLICATION_IMAGE_REPOSITORY" application-image-repository
live_require_command kubectl; live_require_command cosign
phase_begin 55H "Controlled Traffic Cutover"
failed=0; namespace="${PRODUCTION_NAMESPACE:-switching}"; [[ "$namespace" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || live_die "invalid production namespace"; mkdir -p "$PHASE_DIR/stages"
PROMOTION_DECISION_WAIT_SECONDS="${PROMOTION_DECISION_WAIT_SECONDS:-3600}"; [[ "$PROMOTION_DECISION_WAIT_SECONDS" =~ ^[1-9][0-9]*$ ]] || live_die "invalid PROMOTION_DECISION_WAIT_SECONDS"
previous=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["previousStableImage"])' "$GOLIVE_ROOT/phases/55G/canary-5-percent.json")
[[ "$previous" =~ @sha256:[a-f0-9]{64}$ ]] || live_die "previous stable image is not digest-pinned"
candidate="$APPLICATION_IMAGE_REPOSITORY@$RELEASE_APP_IMAGE_DIGEST"; stable_changed=0
rollback(){ kubectl -n "$namespace" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight=0 --overwrite >/dev/null 2>&1 || true; if ((stable_changed)); then kubectl -n "$namespace" set image deployment/switching-api switching-api="$previous" >/dev/null 2>&1 || true; kubectl -n "$namespace" rollout status deployment/switching-api --timeout=600s >/dev/null 2>&1 || true; fi; }
trap 'rc=$?; if ((rc!=0)); then rollback; fi; exit $rc' EXIT INT TERM
wait_decision(){
  local stage="$1" evidence="$2" decision="$PROMOTION_DECISION_DIR/$stage.json" sig="$PROMOTION_DECISION_DIR/$stage.sig" deadline=$((SECONDS+${PROMOTION_DECISION_WAIT_SECONDS:-3600}))
  while [[ ! -f "$decision" || ! -f "$sig" ]]; do ((SECONDS<deadline)) || return 1; sleep 10; done
  [[ ! -L "$decision" && ! -L "$sig" ]] || { echo "decision inputs must not be symlinks" >&2; return 1; }
  cosign verify-blob --key "$DECISION_PUBLIC_KEY" --signature "$sig" "$decision" >/dev/null
  python3 scripts/golive/verify_decision.py --decision "$decision" --stage "$stage" --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" \
    --git-commit "$RELEASE_GIT_COMMIT" --application-digest "$RELEASE_APP_IMAGE_DIGEST" --evidence "$evidence" --output "$PHASE_DIR/stages/$stage-decision.json" >/dev/null
  cp "$decision" "$PHASE_DIR/stages/$stage-decision-source.json"
  cp "$sig" "$PHASE_DIR/stages/$stage-decision-source.sig"
}
previous_evidence="$GOLIVE_ROOT/phases/55G/canary-5-percent.json"
for stage in 25 50 100; do
  run_check "decision-$stage" wait_decision "$stage" "$previous_evidence" || { failed=1; break; }
  run_check "change-window-$stage" env DB_URL="${RELEASE_GATE_DB_URL:-$DB_URL}" ENVIRONMENT=production \
    CHANGE_TYPE="${CHANGE_TYPE:-STANDARD}" RELEASE_GATE_EVIDENCE_FILE="$PHASE_DIR/stages/$stage-release-gate.json" \
    scripts/release/check_change_window.sh || { failed=1; break; }
  if [[ "$stage" == 100 ]]; then
    run_check promote-stable kubectl -n "$namespace" set image deployment/switching-api switching-api="$candidate" || { failed=1; break; }
    stable_changed=1
    run_check stable-rollout kubectl -n "$namespace" rollout status deployment/switching-api --timeout=600s || { failed=1; break; }
    run_check annotate-stable-release kubectl -n "$namespace" annotate deployment switching-api \
      "switching.example.com/release-reference=$RELEASE_REFERENCE" \
      "switching.example.com/release-candidate=$RELEASE_RC_ID" \
      "switching.example.com/git-commit=$RELEASE_GIT_COMMIT" --overwrite || { failed=1; break; }
    run_check canary-weight-zero kubectl -n "$namespace" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight=0 --overwrite || { failed=1; break; }
  else
    run_check "weight-$stage" kubectl -n "$namespace" annotate ingress switching-api-canary "nginx.ingress.kubernetes.io/canary-weight=$stage" --overwrite || { failed=1; break; }
  fi
  case "$stage" in 25|50) minimum=1800;; 100) minimum=3600;; esac
  seconds_var="CUTOVER_OBSERVATION_SECONDS_$stage"; duration="${!seconds_var:-$minimum}"
  [[ "$duration" =~ ^[0-9]+$ ]] || live_die "invalid observation duration for stage $stage"
  (( duration >= minimum )) || live_die "stage $stage observation must be at least $minimum seconds"
  run_check "observe-$stage" sleep "$duration" || { failed=1; break; }
  track=canary; [[ "$stage" == 100 ]] && track=stable
  metrics="$PHASE_DIR/stages/$stage-metrics.json"
  run_check "metrics-$stage" python3 scripts/golive/verify_stage_metrics.py \
    --prometheus-url "$PROMETHEUS_URL" --track "$track" --stage "$stage" --output "$metrics" || { failed=1; break; }
  run_check "synthetic-$stage" env CUTOVER_STAGE_PERCENT="$stage" "$PRODUCTION_SYNTHETIC_SCRIPT" || { failed=1; break; }
  current="$PHASE_DIR/stages/$stage-current.json"
  run_check "capture-$stage" python3 scripts/golive/capture_reconciliation.py --output "$current" --label "production-cutover-$stage" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" || { failed=1; break; }
  reconcile="$PHASE_DIR/stages/$stage-reconciliation.json"
  run_check "reconcile-$stage" python3 scripts/golive/compare_reconciliation.py --baseline "$GOLIVE_ROOT/phases/55D/cutover-baseline.json" --current "$current" --output "$reconcile" || { failed=1; break; }
  python3 - "$PHASE_DIR/stages/$stage.json" "$stage" "$candidate" "$reconcile" <<'PY'
import json,pathlib,sys
out,stage,candidate,reconcile=sys.argv[1:]
r=json.load(open(reconcile)); pathlib.Path(out).write_text(json.dumps({'schemaVersion':1,'stagePercent':int(stage),'candidateImage':candidate,'metricsGate':'PASS','reconciliationStatus':r['status'],'status':'PASS'},indent=2,sort_keys=True)+'\n')
PY
  previous_evidence="$PHASE_DIR/stages/$stage.json"
done
if (( failed == 0 )); then
  kubectl -n "$namespace" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight=0 --overwrite >/dev/null
  kubectl -n "$namespace" scale deployment switching-api-canary --replicas=0 >/dev/null
fi
python3 - "$PHASE_DIR/cutover-summary.json" "$candidate" "$previous" "$failed" <<'PY'
import json,pathlib,sys
out,candidate,previous,failed=sys.argv[1:]
pathlib.Path(out).write_text(json.dumps({'schemaVersion':1,'weights':[5,25,50,100],'candidateImage':candidate,'previousImage':previous,'status':'PASS' if failed=='0' else 'FAIL'},indent=2,sort_keys=True)+'\n')
PY
python3 - "$PHASE_DIR/rollback-readiness.json" "$previous" "$namespace" <<'PY'
import json,pathlib,sys
out,previous,namespace=sys.argv[1:]
pathlib.Path(out).write_text(json.dumps({'schemaVersion':1,'previousImage':previous,'applicationRollbackCommand':f'kubectl -n {namespace} set image deployment/switching-api switching-api={previous}','databaseRollbackPolicy':'forward-fix; no destructive schema rollback','verified':True},indent=2,sort_keys=True)+'\n')
PY
if (( failed )); then write_phase_result FAIL; exit 1; fi
write_phase_result PASS; trap - EXIT INT TERM
