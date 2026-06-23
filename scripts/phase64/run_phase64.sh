#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
mode="preflight"
case "${1:-}" in
  ""|--preflight) mode="preflight" ;;
  --full) mode="full" ;;
  --help|-h)
    cat <<'USAGE'
Usage: scripts/phase64/run_phase64.sh [--preflight|--full]

--preflight  Validate Phase 64 contracts without UAT traffic, faults, evidence import or signing.
--full       Acquire verified Phase 61/runtime evidence, certify all controls and create a signed Phase 54 handoff.

Full mode safety requirements:
  TARGET_ENVIRONMENT=uat
  PHASE64_EXECUTE_RUNTIME=true
  CONFIRM_UAT_EVIDENCE=yes
  RELEASE_REFERENCE, RELEASE_GIT_COMMIT, APPLICATION_IMAGE_DIGEST, MIGRATION_IMAGE_DIGEST

Evidence can be imported or executed:
  PHASE64_PHASE61_MODE=import|execute
  PHASE64_RUNTIME_MODE=import|execute
USAGE
    exit 0 ;;
  *) echo "Unknown mode: $1" >&2; exit 64 ;;
esac
export PHASE64_RUN_ID="${PHASE64_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE64_EVIDENCE_ROOT="${PHASE64_EVIDENCE_ROOT:-$PWD/build/phase64-evidence}"
if [[ "$mode" == preflight ]]; then
  export PHASE64_PREFLIGHT_ONLY=true
else
  export PHASE64_PREFLIGHT_ONLY=false
fi
phases=(
  64A-uat-environment-readiness.sh
  64B-phase61-evidence-acquisition.sh
  64C-runtime-evidence-acquisition.sh
  64D-test-evidence-certification.sh
  64E-performance-capacity-evidence.sh
  64F-backup-pitr-evidence.sh
  64G-dr-recovery-evidence.sh
  64H-alert-firing-certification.sh
  64I-phase54-entry-gate.sh
  64J-signed-uat-handoff-bundle.sh
)
for script in "${phases[@]}"; do
  phase="${script%%-*}"
  printf '\n=== %s (%s) ===\n' "$phase" "$mode"
  "scripts/phase64/$script"
done
printf '\nPhase 64 run complete: %s\nEvidence: %s/%s\n' "$mode" "$PHASE64_EVIDENCE_ROOT" "$PHASE64_RUN_ID"
