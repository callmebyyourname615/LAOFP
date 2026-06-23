#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR/../.."
mode=preflight
case "${1:-}" in
  ''|--preflight) mode=preflight ;;
  --repo) mode=repo ;;
  --full) mode=full ;;
  -h|--help)
    cat <<'USAGE'
Usage: scripts/phase63/run_phase63.sh [--preflight|--repo|--full]

--preflight  Validate Phase 63 contracts only. No Maven, remote calls, load or fault injection.
--repo       Execute non-destructive repository checks and Phase 61 repo certification.
--full       Execute the signed Phase 63 UAT evidence plan. Requires explicit UAT confirmations.
USAGE
    exit 0 ;;
  *) printf 'Unknown mode: %s\n' "$1" >&2; exit 64 ;;
esac
export PHASE63_MODE="$mode"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"
phase63_load_env
if phase63_is_full; then phase63_require_uat_confirmation; fi
phase63_write_context

phases=(
  63A-uat-environment-inventory.sh
  63B-phase61-preflight-execution.sh
  63C-secret-rotation-ceremony.sh
  63D-performance-capacity-execution.sh
  63E-backup-pitr-execution.sh
  63F-dr-scenario-execution.sh
  63G-observability-alert-validation.sh
  63H-smos-rbac-audit.sh
  63I-reconciliation-sanctions-execution.sh
  63J-uat-evidence-entry-gate.sh
)
failures=0
for script in "${phases[@]}"; do
  printf '\n=== %s (%s) ===\n' "${script%%-*}" "$mode"
  if "$SCRIPT_DIR/$script"; then
    :
  else
    failures=$((failures + 1))
    [[ "$PHASE63_CONTINUE_ON_FAILURE" == true ]] || exit 1
  fi
done
printf '\nPhase 63 mode: %s\nEvidence: %s\n' "$mode" "$PHASE63_RUN_DIR"
if (( failures > 0 )); then
  printf 'Phase 63 completed with %d failed phase(s).\n' "$failures" >&2
  exit 1
fi
