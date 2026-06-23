#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73H" "CPU and memory resource exhaustion resilience"
STATUS=FAIL; MESSAGE="resource exhaustion experiments failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
for manifest in chaos/phase73/experiments/73H-cpu-stress.yaml chaos/phase73/experiments/73H-memory-stress.yaml; do
  phase73_require_file "$manifest"
done
if phase73_is_preflight; then
  phase73_run "capture cpu stress contract" cp chaos/phase73/experiments/73H-cpu-stress.yaml "$PHASE73_PHASE_DIR/cpu-template.yaml"
  phase73_run "capture memory stress contract" cp chaos/phase73/experiments/73H-memory-stress.yaml "$PHASE73_PHASE_DIR/memory-template.yaml"
  STATUS=PREPARED; MESSAGE="CPU and memory stress experiments are ready; rendering certified by 73A"; exit 0
fi
phase73_run "cpu stress experiment" scripts/phase73/execute_experiment.sh cpu-stress chaos/phase73/experiments/73H-cpu-stress.yaml
phase73_run "memory stress experiment" scripts/phase73/execute_experiment.sh memory-stress chaos/phase73/experiments/73H-memory-stress.yaml
STATUS=PASS; MESSAGE="CPU and memory stress recovered within policy with integrity preserved"
