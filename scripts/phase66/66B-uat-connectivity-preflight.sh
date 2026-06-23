#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66B" "UAT connectivity and dependency preflight"
STATUS="FAIL"; MESSAGE="UAT dependency preflight failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_require_file config/phase66/uat-dependencies.yaml
phase66_require_file schemas/phase66/dependency-result.schema.json
if ! phase66_is_full; then
  phase66_run "validate dependency contract" python3 scripts/phase66/probe_dependencies.py \
    --config config/phase66/uat-dependencies.yaml --output "$PHASE66_PHASE_DIR/dependencies.json" --contract-only
  STATUS="PREPARED"; MESSAGE="dependency probes are configured; no network calls were made"; exit 0
fi
phase66_require_uat
phase66_run "probe UAT dependencies" python3 scripts/phase66/probe_dependencies.py \
  --config config/phase66/uat-dependencies.yaml --output "$PHASE66_PHASE_DIR/dependencies.json"
STATUS="PASS"; MESSAGE="all required UAT dependencies are reachable and role checks passed"
