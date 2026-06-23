#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
case "${1:---preflight}" in --preflight) mode=preflight;; --full) mode=full;; *) echo "usage: $0 [--preflight|--full]" >&2; exit 64;; esac
export PHASE72_MODE="$mode"
export PHASE72_RUN_ID="${PHASE72_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE72_GIT_SHA="${PHASE72_GIT_SHA:-$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)}"
source "$ROOT/scripts/phase72/common.sh"
phases=(
  72A-phase71-handoff-collision-guard.sh
  72B-crossborder-temporal-binding-closure.sh
  72C-full-maven-verification-closure.sh
  72D-repository-verification-gate.sh
  72E-uat-environment-activation.sh
  72F-performance-evidence-campaign.sh
  72G-backup-pitr-dr-certification.sh
  72H-secret-rotation-purge-ceremony.sh
  72I-runtime-security-alert-certification.sh
)
rc=0
for script in "${phases[@]}"; do
  echo "=== ${script%%-*} ==="
  if ! "$ROOT/scripts/phase72/$script"; then rc=1; [[ "$mode" == full ]] && break; fi
done
"$ROOT/scripts/phase72/72J-build-phase54-go-no-go-bundle.sh" || rc=1
echo "Phase 72 $mode evidence: $PHASE72_EVIDENCE_ROOT"
exit "$rc"
