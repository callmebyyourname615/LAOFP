#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73C" "Database connectivity interruption resilience"
STATUS=FAIL; MESSAGE="database-network-loss experiment failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file "chaos/phase73/experiments/73C-database-network-loss.yaml"
if phase73_is_preflight; then
  phase73_run "capture database-network-loss manifest contract" cp "chaos/phase73/experiments/73C-database-network-loss.yaml" "$PHASE73_PHASE_DIR/manifest-template.yaml"
  STATUS=PREPARED; MESSAGE="database-network-loss experiment, cleanup and evidence contract are ready; rendering certified by 73A"; exit 0
fi
phase73_run "database-network-loss experiment" scripts/phase73/execute_experiment.sh "database-network-loss" "chaos/phase73/experiments/73C-database-network-loss.yaml"
STATUS=PASS; MESSAGE="database-network-loss recovered within policy with cleanup and financial integrity evidence"
