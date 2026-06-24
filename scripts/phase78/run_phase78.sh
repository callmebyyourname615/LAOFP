#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in
 --preflight) export PHASE78_MODE=preflight ;;
 --repo) export PHASE78_MODE=repo ;;
 --full) export PHASE78_MODE=full ;;
 *) echo 'Usage: run_phase78.sh [--preflight|--repo|--full]' >&2; exit 64 ;;
esac
export PHASE78_RUN_ID="${PHASE78_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE78_COMMIT="${PHASE78_COMMIT:-$(timeout 2 git rev-parse HEAD 2>/dev/null || printf unknown)}"
for script in scripts/phase78/78{A,B,C,D,E,F,G,H,I,J}-*.sh; do echo; echo "=== $(basename "$script") ==="; "$script"; done
echo "Phase 78 complete: $PHASE78_MODE — scripts/phase78/evidence/$PHASE78_RUN_ID"
