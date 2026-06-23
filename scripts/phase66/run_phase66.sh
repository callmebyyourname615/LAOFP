#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
mode="preflight"
case "${1:-}" in
  ""|--preflight) mode="preflight" ;;
  --repo) mode="repo" ;;
  --full) mode="full" ;;
  -h|--help) cat <<'USAGE'
Usage: scripts/phase66/run_phase66.sh [--preflight|--repo|--full]

--preflight  Validate contracts and generate PREPARED evidence; no network, Maven, load, backup, rotation or fault injection.
--repo       Run repository-safe checks (66A/66C/66D) and prepare all runtime phases.
--full       Execute the complete UAT campaign. Explicit UAT/load/destructive/rotation confirmations are mandatory.
USAGE
    exit 0 ;;
  *) echo "Unknown mode: $1" >&2; exit 64 ;;
esac
export PHASE66_MODE="$mode"
export PHASE66_RUN_ID="${PHASE66_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE66_EVIDENCE_ROOT="${PHASE66_EVIDENCE_ROOT:-$PWD/build/phase66-evidence}"
phases=(
  66A-phase65-merge-collision-guard.sh
  66B-uat-connectivity-preflight.sh
  66C-build-test-runtime-closure.sh
  66D-migration-data-certification.sh
  66E-performance-campaign.sh
  66F-backup-pitr-restore.sh
  66G-dr-failure-campaign.sh
  66H-secret-rotation-ceremony.sh
  66I-smos-live-security.sh
  66J-build-runtime-closure-bundle.sh
)
failures=0
for script in "${phases[@]}"; do
  echo; echo "=== ${script%%-*} ($mode) ==="
  if ! "scripts/phase66/$script"; then failures=$((failures+1)); fi
done
printf '\nPhase 66 run complete: mode=%s failures=%s evidence=%s/%s\n' "$mode" "$failures" "$PHASE66_EVIDENCE_ROOT" "$PHASE66_RUN_ID"
[[ "$failures" -eq 0 ]]
