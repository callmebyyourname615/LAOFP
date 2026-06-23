#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
case "${1:---preflight}" in --preflight) export PHASE65_MODE=preflight;; --repo) export PHASE65_MODE=repo;; --full) export PHASE65_MODE=full;; *) echo 'Usage: run_phase65.sh [--preflight|--repo|--full]' >&2; exit 64;; esac
export PHASE65_RUN_ID="${PHASE65_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
for s in scripts/phase65/65{A,B,C,D,E,F,G,H,I,J}-*.sh; do echo; echo "=== $(basename "$s") ==="; "$s"; done
echo "Phase 65 complete: $PHASE65_MODE — scripts/phase65/evidence/$PHASE65_RUN_ID"
