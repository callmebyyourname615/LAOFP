#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
mode=preflight
case "${1:---preflight}" in
  --preflight) mode=preflight ;;
  --full) mode=full ;;
  *) echo "usage: $0 [--preflight|--full]" >&2; exit 64 ;;
esac
export PHASE69_MODE="$mode"
export PHASE69_GIT_SHA="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
source "$ROOT/scripts/phase69/common.sh"
phases=(
  69A-phase68-handoff-collision-guard.sh
  69B-webhook-objectmapper-closure.sh
  69C-operations-fk-cleanup-closure.sh
  69D-crossborder-temporal-binding-closure.sh
  69E-regression-test-certification.sh
  69F-targeted-test-evidence.sh
  69G-full-maven-verification.sh
  69H-repository-gate-verification.sh
  69I-migration-config-regression.sh
  69J-build-verification-bundle.sh
)
for script in "${phases[@]}"; do
  echo "=== Phase ${script%%-*} ==="
  "$ROOT/scripts/phase69/$script"
done
echo "Phase 69 $mode complete: $PHASE69_EVIDENCE_ROOT"
