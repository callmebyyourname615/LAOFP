#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."

mode="preflight"
case "${1:-}" in
  ""|--preflight) mode="preflight" ;;
  --repo) mode="repo" ;;
  --full) mode="full" ;;
  --help|-h)
    cat <<'USAGE'
Usage: scripts/phase60/run_phase60.sh [--preflight|--repo|--full]

--preflight  Validate files/contracts only. No Maven, load, rotation or failure injection.
--repo       Execute 60A-60E; preflight 60F-60J.
--full       Execute all phases. Requires UAT confirmations, operator attestations and image digests.
USAGE
    exit 0 ;;
  *) echo "Unknown mode: $1" >&2; exit 64 ;;
esac

export PHASE60_RUN_ID="${PHASE60_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE60_EVIDENCE_ROOT="${PHASE60_EVIDENCE_ROOT:-$PWD/scripts/phase60/evidence}"

phases=(
  60A-repository-baseline.sh
  60B-build-test-closure.sh
  60C-migration-certification.sh
  60D-smos-security-e2e.sh
  60E-dashboard-promotion-acceptance.sh
  60F-secret-rotation-readiness.sh
  60G-uat-infrastructure-contract.sh
  60H-performance-capacity.sh
  60I-resilience-evidence.sh
  60J-assemble-evidence-bundle.sh
)

for script in "${phases[@]}"; do
  phase="${script%%-*}"
  case "$mode:$phase" in
    preflight:*) export PHASE60_PREFLIGHT_ONLY=true ;;
    repo:60A|repo:60B|repo:60C|repo:60D|repo:60E) export PHASE60_PREFLIGHT_ONLY=false ;;
    repo:*) export PHASE60_PREFLIGHT_ONLY=true ;;
    full:*) export PHASE60_PREFLIGHT_ONLY=false ;;
  esac
  echo
  echo "=== $phase ($mode) ==="
  "scripts/phase60/$script"
done

echo
printf 'Phase 60 run complete: %s\nEvidence root: %s/%s\n' "$mode" "$PHASE60_EVIDENCE_ROOT" "$PHASE60_RUN_ID"
