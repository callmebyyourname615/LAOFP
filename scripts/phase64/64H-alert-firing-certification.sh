#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64H" "Alert firing, routing and resolution certification"
STATUS=FAIL; MESSAGE="alert certification failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/phase64/validate_alert_attestation.py
phase64_require_file scripts/monitoring/verify_alert_runbooks.py
phase64_require_file scripts/monitoring/build_alert_test_matrix.py
phase64_require_file scripts/phase64/generate_alert_attestation_template.py
if phase64_is_preflight; then
  set +e
  python3 scripts/monitoring/verify_alert_runbooks.py > "$PHASE64_PHASE_DIR/alert-runbook-baseline.log" 2>&1
  baseline_rc=$?
  set -e
  head -n 1 "$PHASE64_PHASE_DIR/alert-runbook-baseline.log" | tee -a "$PHASE64_PHASE_LOG"
  python3 - "$baseline_rc" "$PHASE64_PHASE_DIR/alert-runbook-baseline.json" <<'PYBASE'
import json,pathlib,sys
log=pathlib.Path(sys.argv[2]).with_suffix('.log').read_text(encoding='utf-8',errors='replace')
pathlib.Path(sys.argv[2]).write_text(json.dumps({"schemaVersion":1,"exitCode":int(sys.argv[1]),"currentRepositoryPassed":int(sys.argv[1])==0,"requiresClosureBeforeFullGate":int(sys.argv[1])!=0,"log":"alert-runbook-baseline.log"},indent=2,sort_keys=True)+"\n",encoding='utf-8')
PYBASE
  phase64_run "generate current alert test matrix" python3 scripts/monitoring/build_alert_test_matrix.py \
    --output "$PHASE64_PHASE_DIR/ALERT_TEST_MATRIX.md"
  phase64_run "generate current alert attestation skeleton" python3 scripts/phase64/generate_alert_attestation_template.py \
    --root "$PHASE64_ROOT" --output "$PHASE64_PHASE_DIR/alert-attestation.skeleton.json"
  STATUS=PREPARED
  if (( baseline_rc == 0 )); then
    MESSAGE="alert matrix and current runbook contracts are ready"
  else
    MESSAGE="alert tooling is ready; current repository runbook gaps are recorded and remain a full-gate blocker"
  fi
  exit 0
fi
phase64_require_release_identity
: "${ALERT_ATTESTATION:?ALERT_ATTESTATION is required}"
phase64_require_file "$ALERT_ATTESTATION"
manifest="$PHASE64_RUN_DIR/64C/runtime/manifest.json"
phase64_run "certify runtime alert-delivery control" python3 scripts/phase64/extract_runtime_controls.py \
  --manifest "$manifest" --category observability-alerts --required alert-delivery-drill \
  --output "$PHASE64_PHASE_DIR/runtime-alert-control.json"
phase64_run "verify alert and runbook contracts" python3 scripts/monitoring/verify_alert_runbooks.py
phase64_run "generate current alert test matrix" python3 scripts/monitoring/build_alert_test_matrix.py \
  --output "$PHASE64_PHASE_DIR/ALERT_TEST_MATRIX.md"
phase64_run "generate expected alert attestation skeleton" python3 scripts/phase64/generate_alert_attestation_template.py \
  --root "$PHASE64_ROOT" --output "$PHASE64_PHASE_DIR/alert-attestation.skeleton.json" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST"
cp "$ALERT_ATTESTATION" "$PHASE64_PHASE_DIR/alert-attestation.json"
phase64_run "validate every current alert fired, routed and resolved" python3 scripts/phase64/validate_alert_attestation.py \
  --root "$PHASE64_ROOT" --config "$PHASE64_CONFIG" --attestation "$PHASE64_PHASE_DIR/alert-attestation.json" \
  --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST" --output "$PHASE64_PHASE_DIR/alert-summary.json"
STATUS=PASS; MESSAGE="all current repository alerts have fired, routed and resolved evidence"
