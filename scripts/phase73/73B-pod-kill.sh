#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73B" "Application pod-kill resilience"
STATUS=FAIL; MESSAGE="pod-kill experiment failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file "chaos/phase73/experiments/73B-pod-kill.yaml"
if phase73_is_preflight; then
  phase73_run "capture pod-kill manifest contract" cp "chaos/phase73/experiments/73B-pod-kill.yaml" "$PHASE73_PHASE_DIR/manifest-template.yaml"
  STATUS=PREPARED; MESSAGE="pod-kill experiment, cleanup and evidence contract are ready; rendering certified by 73A"; exit 0
fi
phase73_run "pod-kill experiment" scripts/phase73/execute_experiment.sh "pod-kill" "chaos/phase73/experiments/73B-pod-kill.yaml"
STATUS=PASS; MESSAGE="pod-kill recovered within policy with cleanup and financial integrity evidence"
