#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73G" "DNS dependency failure resilience"
STATUS=FAIL; MESSAGE="dns-error experiment failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
phase73_require_file "chaos/phase73/experiments/73G-dns-error.yaml"
if phase73_is_preflight; then
  phase73_run "capture dns-error manifest contract" cp "chaos/phase73/experiments/73G-dns-error.yaml" "$PHASE73_PHASE_DIR/manifest-template.yaml"
  STATUS=PREPARED; MESSAGE="dns-error experiment, cleanup and evidence contract are ready; rendering certified by 73A"; exit 0
fi
phase73_run "dns-error experiment" scripts/phase73/execute_experiment.sh "dns-error" "chaos/phase73/experiments/73G-dns-error.yaml"
STATUS=PASS; MESSAGE="dns-error recovered within policy with cleanup and financial integrity evidence"
