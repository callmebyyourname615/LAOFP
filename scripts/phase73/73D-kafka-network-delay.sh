#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73D" "Kafka network latency resilience"
STATUS=FAIL; MESSAGE="kafka-network-delay experiment failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file "chaos/phase73/experiments/73D-kafka-network-delay.yaml"
if phase73_is_preflight; then
  phase73_run "capture kafka-network-delay manifest contract" cp "chaos/phase73/experiments/73D-kafka-network-delay.yaml" "$PHASE73_PHASE_DIR/manifest-template.yaml"
  STATUS=PREPARED; MESSAGE="kafka-network-delay experiment, cleanup and evidence contract are ready; rendering certified by 73A"; exit 0
fi
phase73_run "kafka-network-delay experiment" scripts/phase73/execute_experiment.sh "kafka-network-delay" "chaos/phase73/experiments/73D-kafka-network-delay.yaml"
STATUS=PASS; MESSAGE="kafka-network-delay recovered within policy with cleanup and financial integrity evidence"
