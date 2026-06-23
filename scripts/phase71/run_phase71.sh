#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in
  --preflight) export PHASE71_MODE=preflight ;;
  --repo) export PHASE71_MODE=repo ;;
  --full) export PHASE71_MODE=full ;;
  *) echo 'Usage: run_phase71.sh [--preflight|--repo|--full]' >&2; exit 64 ;;
esac
export PHASE71_RUN_ID="${PHASE71_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE71_COMMIT="${PHASE71_COMMIT:-$(timeout 2 git rev-parse HEAD 2>/dev/null || printf unknown)}"
for script in scripts/phase71/71{A,B,C,D,E,F,G,H,I,J}-*.sh; do
  echo; echo "=== $(basename "$script") ==="; "$script"
done
echo "Phase 71 complete: $PHASE71_MODE — scripts/phase71/evidence/$PHASE71_RUN_ID"
