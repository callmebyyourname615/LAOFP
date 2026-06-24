#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in --preflight) export PHASE79_MODE=preflight;; --repo) export PHASE79_MODE=repo;; --full) export PHASE79_MODE=full;; *) echo 'Usage: run_phase79.sh [--preflight|--repo|--full]' >&2; exit 64;; esac
export PHASE79_RUN_ID="${PHASE79_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"; export PHASE79_COMMIT="${PHASE79_COMMIT:-$(timeout 2 git rev-parse HEAD 2>/dev/null || printf unknown)}"
for script in scripts/phase79/79{A,B,C,D,E,F,G,H,I,J}-*.sh; do echo; echo "=== $(basename "$script") ==="; "$script"; done
echo "Phase 79 complete: $PHASE79_MODE — scripts/phase79/evidence/$PHASE79_RUN_ID"
