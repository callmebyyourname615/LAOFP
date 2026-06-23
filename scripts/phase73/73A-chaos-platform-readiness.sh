#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73A" "Chaos platform and UAT safety readiness"
STATUS=FAIL; MESSAGE="Chaos platform readiness failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_run "Phase 73 static contract" python3 scripts/verify_phase73_static.py
if phase73_is_preflight; then
  STATUS=PREPARED; MESSAGE="Chaos Mesh contracts, safety controls and experiment manifests are ready"; exit 0
fi
phase73_require_command kubectl
phase73_require_command curl
phase73_require_command psql
phase73_require_execution_approval
phase73_run "target namespace" kubectl get namespace "$PHASE73_NAMESPACE"
phase73_run "application deployment" kubectl -n "$PHASE73_NAMESPACE" get deployment "$PHASE73_DEPLOYMENT_NAME"
while read -r crd; do
  phase73_run "required CRD $crd" kubectl get crd "$crd"
done < <(python3 - "$PHASE73_POLICY" <<'PY'
import pathlib, sys, yaml
for item in yaml.safe_load(pathlib.Path(sys.argv[1]).read_text())['chaosMesh']['requiredCrds']:
    print(item)
PY
)
phase73_run "no active Phase 73 experiments" bash -c '
  count=$(kubectl -n "$PHASE73_NAMESPACE" get podchaos,networkchaos,dnschaos,stresschaos -l switching.run-id -o name 2>/dev/null | wc -l | tr -d " ")
  test "$count" -eq 0
'
phase73_run "application health" curl --fail-with-body --silent --show-error "$PHASE73_HEALTH_URL"
STATUS=PASS; MESSAGE="Chaos Mesh CRDs, UAT target, approval and application health are ready"
