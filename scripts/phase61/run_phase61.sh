#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
mode="preflight"
case "${1:-}" in
  ""|--preflight) mode="preflight" ;;
  --repo) mode="repo" ;;
  --full) mode="full" ;;
  --help|-h) cat <<'USAGE'
Usage: scripts/phase61/run_phase61.sh [--preflight|--repo|--full]

--preflight  Validate contracts only; no Maven, remote calls, load, rotation or fault injection.
--repo       Execute repository phases 61A, 61B, 61D and 61E; prepare 61C and 61F-61J.
--full       Execute all phases against an explicitly confirmed UAT environment.
USAGE
    exit 0 ;;
  *) echo "Unknown mode: $1" >&2; exit 64 ;;
esac
export PHASE61_RUN_ID="${PHASE61_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE61_EVIDENCE_ROOT="${PHASE61_EVIDENCE_ROOT:-$PWD/scripts/phase61/evidence}"
phases=(
  61A-build-test-green-closure.sh
  61B-migration-data-integrity.sh
  61C-uat-deployment-contract.sh
  61D-smos-security-hardening.sh
  61E-dashboard-promotion-readiness.sh
  61F-secret-supply-chain-closure.sh
  61G-performance-capacity-certification.sh
  61H-settlement-reconciliation-scale.sh
  61I-resilience-alert-drills.sh
  61J-uat-evidence-rc-gate.sh
)
for script in "${phases[@]}"; do
  phase="${script%%-*}"
  case "$mode:$phase" in
    preflight:*) export PHASE61_PREFLIGHT_ONLY=true ;;
    repo:61A|repo:61B|repo:61D|repo:61E) export PHASE61_PREFLIGHT_ONLY=false ;;
    repo:*) export PHASE61_PREFLIGHT_ONLY=true ;;
    full:*) export PHASE61_PREFLIGHT_ONLY=false ;;
  esac
  echo; echo "=== $phase ($mode) ==="
  "scripts/phase61/$script"
done
printf '\nPhase 61 run complete: %s\nEvidence: %s/%s\n' "$mode" "$PHASE61_EVIDENCE_ROOT" "$PHASE61_RUN_ID"
