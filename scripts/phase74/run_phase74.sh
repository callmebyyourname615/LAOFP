#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in
  --preflight) export PHASE74_MODE=preflight ;;
  --repo) export PHASE74_MODE=repo ;;
  --full) export PHASE74_MODE=full ;;
  *) echo 'Usage: run_phase74.sh [--preflight|--repo|--full]' >&2; exit 64 ;;
esac
export PHASE74_RUN_ID="${PHASE74_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE74_COMMIT="${PHASE74_COMMIT:-$(timeout 2 git rev-parse HEAD 2>/dev/null || printf unknown)}"
for script in scripts/phase74/74{A,B,C,D,E,F,G,H,I,J}-*.sh; do echo; echo "=== $(basename "$script") ==="; "$script"; done
echo "Phase 74 complete: $PHASE74_MODE — scripts/phase74/evidence/$PHASE74_RUN_ID"
