#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in
 --preflight) export PHASE75_MODE=preflight ;;
 --repo) export PHASE75_MODE=repo ;;
 --full) export PHASE75_MODE=full ;;
 *) echo 'Usage: run_phase75.sh [--preflight|--repo|--full]' >&2; exit 64 ;;
esac
export PHASE75_RUN_ID="${PHASE75_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE75_COMMIT="${PHASE75_COMMIT:-$(timeout 2 git rev-parse HEAD 2>/dev/null || printf unknown)}"
for script in scripts/phase75/75{A,B,C,D,E,F,G,H,I,J}-*.sh; do echo; echo "=== $(basename "$script") ==="; "$script"; done
echo "Phase 75 complete: $PHASE75_MODE — scripts/phase75/evidence/$PHASE75_RUN_ID"
