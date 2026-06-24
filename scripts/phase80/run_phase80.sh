#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PHASE80_MODE="${1:-${PHASE80_MODE:-preflight}}"
export PHASE80_RUN_ID="${PHASE80_RUN_ID:-phase80-$(date -u +%Y%m%dT%H%M%SZ)}"
for step in A B C D E F G H I J; do "$ROOT/scripts/phase80/80${step}-"*.sh; done
printf 'Phase 80 run: %s\n' "$ROOT/evidence/phase80/$PHASE80_RUN_ID"
