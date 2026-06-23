#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63A 'UAT environment inventory and infrastructure gap analysis'
STATUS=FAIL; MESSAGE='UAT environment inventory failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file config/phase61-uat-infrastructure-contract.yaml
phase63_require_file scripts/phase61/probe_uat_contract.py
phase63_run 'static UAT contract validation' python3 scripts/phase61/probe_uat_contract.py \
  --contract config/phase61-uat-infrastructure-contract.yaml --preflight \
  --output "$PHASE63_PHASE_DIR/uat-contract-preflight.json"
find . -maxdepth 3 -type f \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' -o -name '*infrastructure*contract*.yaml' -o -name '*infrastructure*contract*.yml' \) \
  -not -path './.git/*' -print | sort > "$PHASE63_PHASE_DIR/infrastructure-files.txt"
if ! phase63_is_full; then
  STATUS=PREPARED; MESSAGE='UAT inventory and live dependency probes are ready; no remote environment was queried'; exit 0
fi
phase63_require_uat_confirmation
: "${UAT_ENV_FILE:?UAT_ENV_FILE is required}"
: "${UAT_BASE_URL:?UAT_BASE_URL is required}"
phase63_require_file "$UAT_ENV_FILE"
phase63_run 'live UAT dependency contract' python3 scripts/phase61/probe_uat_contract.py \
  --contract config/phase61-uat-infrastructure-contract.yaml --env-file "$UAT_ENV_FILE" \
  --base-url "$UAT_BASE_URL" --output "$PHASE63_PHASE_DIR/uat-contract-live.json"
platform="${PHASE63_PLATFORM:-kubernetes}"
case "$platform" in
  kubernetes)
    phase63_require_command kubectl
    namespace="${PHASE63_NAMESPACE:-switching}"
    phase63_capture 'cluster context' "$PHASE63_PHASE_DIR/kubectl-context.txt" kubectl config current-context
    phase63_capture 'nodes' "$PHASE63_PHASE_DIR/kubectl-nodes.txt" kubectl get nodes -o wide
    phase63_capture 'workloads' "$PHASE63_PHASE_DIR/kubectl-workloads.txt" kubectl -n "$namespace" get deploy,statefulset,pod,service,hpa,pdb -o wide
    phase63_capture 'resource usage' "$PHASE63_PHASE_DIR/kubectl-top.txt" kubectl -n "$namespace" top pods --containers
    ;;
  docker)
    phase63_require_command docker
    phase63_capture 'docker version' "$PHASE63_PHASE_DIR/docker-version.txt" docker version
    phase63_capture 'docker containers' "$PHASE63_PHASE_DIR/docker-containers.txt" docker ps --all --no-trunc
    phase63_capture 'docker resource usage' "$PHASE63_PHASE_DIR/docker-stats.txt" docker stats --no-stream
    ;;
  *) phase63_log "ERROR PHASE63_PLATFORM must be kubernetes or docker"; exit 64 ;;
esac
python3 scripts/phase63/build_environment_gap_report.py \
  --contract config/phase61-uat-infrastructure-contract.yaml \
  --probe "$PHASE63_PHASE_DIR/uat-contract-live.json" \
  --platform "$platform" --output "$PHASE63_PHASE_DIR/infrastructure-gap-report.json"
STATUS=PASS; MESSAGE='live UAT dependencies, immutable deployment contract and platform inventory captured'
