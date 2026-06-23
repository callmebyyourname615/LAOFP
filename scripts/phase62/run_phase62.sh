#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
mode="preflight"
case "${1:-}" in
  ""|--preflight) mode=preflight ;;
  --repo) mode=repo ;;
  --full) mode=full ;;
  --help|-h) echo 'Usage: scripts/phase62/run_phase62.sh [--preflight|--repo|--full]'; exit 0 ;;
  *) echo "Unknown mode: $1" >&2; exit 64 ;;
esac
export PHASE62_RUN_ID="${PHASE62_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE62_EVIDENCE_ROOT="${PHASE62_EVIDENCE_ROOT:-$PWD/scripts/phase62/evidence}"
export PHASE62_MODE="$mode"
phases=(
  62A-test-blocker-regression.sh 62B-full-verification-closure.sh 62C-smos-completion.sh
  62D-read-replica-routing.sh 62E-financial-precision.sh 62F-hikari-monitoring.sh
  62G-dashboard-hardening.sh 62H-promotion-integrity.sh 62I-nplus1-pagination.sh
  62J-distributed-tracing.sh)
for script in "${phases[@]}"; do
  phase="${script%%-*}"
  case "$mode" in
    preflight) export PHASE62_PREFLIGHT_ONLY=true ;;
    repo|full) export PHASE62_PREFLIGHT_ONLY=false ;;
  esac
  echo; echo "=== $phase ($mode) ==="
  "scripts/phase62/$script"
done
printf '\nPhase 62 run complete: %s\nEvidence: %s/%s\n' "$mode" "$PHASE62_EVIDENCE_ROOT" "$PHASE62_RUN_ID"
