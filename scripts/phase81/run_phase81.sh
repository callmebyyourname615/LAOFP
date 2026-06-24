#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PHASE81_MODE="${1:-${PHASE81_MODE:-preflight}}"
export PHASE81_RUN_ID="${PHASE81_RUN_ID:-phase81-$(date -u +%Y%m%dT%H%M%SZ)}"
for step in A B C D E F G H I J; do "$ROOT/scripts/phase81/81${step}-"*.sh; done
printf 'Phase 81 run: %s\n' "$ROOT/evidence/phase81/$PHASE81_RUN_ID"
