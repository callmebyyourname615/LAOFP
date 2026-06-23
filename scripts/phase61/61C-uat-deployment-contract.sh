#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61C" "UAT deployment and infrastructure contract"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="UAT infrastructure contract failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_run "UAT contract static validation" python3 scripts/phase61/probe_uat_contract.py \
  --contract config/phase61-uat-infrastructure-contract.yaml --preflight \
  --output "$PHASE61_PHASE_DIR/uat-contract-preflight.json"
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="UAT dependency, TLS, time-sync and immutable-deployment probes are ready"; exit 0
fi
phase_require_uat_confirmation
: "${UAT_ENV_FILE:?UAT_ENV_FILE is required}"
: "${UAT_BASE_URL:?UAT_BASE_URL is required}"
phase_run "live UAT contract probes" python3 scripts/phase61/probe_uat_contract.py \
  --contract config/phase61-uat-infrastructure-contract.yaml --env-file "$UAT_ENV_FILE" \
  --base-url "$UAT_BASE_URL" --output "$PHASE61_PHASE_DIR/uat-contract-live.json"
PHASE_STATUS="PASS"; PHASE_MESSAGE="UAT deployment, dependencies, TLS, time synchronization and immutable image contract passed"
