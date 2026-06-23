#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in
  --preflight) export PHASE68_MODE=preflight ;;
  --repo) export PHASE68_MODE=repo ;;
  --full) export PHASE68_MODE=full ;;
  *) echo 'Usage: run_phase68.sh [--preflight|--repo|--full]' >&2; exit 64 ;;
esac
export PHASE68_RUN_ID="${PHASE68_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
for s in scripts/phase68/68{A,B,C,D,E,F,G,H,I,J}-*.sh; do
  echo
  echo "=== $(basename "$s") ==="
  "$s"
done
echo "Phase 68 complete: $PHASE68_MODE — scripts/phase68/evidence/$PHASE68_RUN_ID"
