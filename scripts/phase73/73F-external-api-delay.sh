#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73F" "External API timeout resilience"
STATUS=FAIL; MESSAGE="external-api-delay experiment failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file "chaos/phase73/experiments/73F-external-api-delay.yaml"
if phase73_is_preflight; then
  phase73_run "capture external-api-delay manifest contract" cp "chaos/phase73/experiments/73F-external-api-delay.yaml" "$PHASE73_PHASE_DIR/manifest-template.yaml"
  STATUS=PREPARED; MESSAGE="external-api-delay experiment, cleanup and evidence contract are ready; rendering certified by 73A"; exit 0
fi
phase73_run "external-api-delay experiment" scripts/phase73/execute_experiment.sh "external-api-delay" "chaos/phase73/experiments/73F-external-api-delay.yaml"
STATUS=PASS; MESSAGE="external-api-delay recovered within policy with cleanup and financial integrity evidence"
