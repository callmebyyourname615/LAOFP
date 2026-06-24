#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
mode="${1:---preflight}"; [[ "$mode" == --full ]] && export PHASE76_MODE=full || export PHASE76_MODE=preflight
export RUN_ID="${RUN_ID:-phase76-$(date -u +%Y%m%dT%H%M%SZ)}"
for p in A B C D E F G H I J; do
  script=$(find scripts/phase76 -maxdepth 1 -type f -name "76${p}-*.sh" | head -1)
  echo "==> $script"; bash "$script"
done
cat "evidence/phase76/$RUN_ID/release-readiness-manifest.json"
