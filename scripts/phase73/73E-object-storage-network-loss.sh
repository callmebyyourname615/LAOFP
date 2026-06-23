#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73E" "Object storage outage resilience"
STATUS=FAIL; MESSAGE="object-storage-network-loss experiment failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file "chaos/phase73/experiments/73E-object-storage-network-loss.yaml"
if phase73_is_preflight; then
  phase73_run "capture object-storage-network-loss manifest contract" cp "chaos/phase73/experiments/73E-object-storage-network-loss.yaml" "$PHASE73_PHASE_DIR/manifest-template.yaml"
  STATUS=PREPARED; MESSAGE="object-storage-network-loss experiment, cleanup and evidence contract are ready; rendering certified by 73A"; exit 0
fi
phase73_run "object-storage-network-loss experiment" scripts/phase73/execute_experiment.sh "object-storage-network-loss" "chaos/phase73/experiments/73E-object-storage-network-loss.yaml"
STATUS=PASS; MESSAGE="object-storage-network-loss recovered within policy with cleanup and financial integrity evidence"
