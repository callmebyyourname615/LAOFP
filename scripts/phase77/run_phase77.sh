#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"; cd "$ROOT"
export RUN_ID="${RUN_ID:-phase77-$(date -u +%Y%m%dT%H%M%SZ)}"
export GIT_COMMIT="${GIT_COMMIT:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
for p in A B C D E F G H I J; do
  script=$(find scripts/phase77 -maxdepth 1 -type f -name "77${p}-*.sh" | head -1)
  echo "==> $script"; bash "$script"
done
python3 scripts/phase77/build_compliance_export.py --source "evidence/phase77/$RUN_ID/results" --output "evidence/phase77/$RUN_ID/compliance-export.json" --release "$GIT_COMMIT"
cat "evidence/phase77/$RUN_ID/compliance-export.json"
