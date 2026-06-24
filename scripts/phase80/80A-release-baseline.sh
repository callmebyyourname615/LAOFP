#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80A
latest="$(find src/main/resources/db/migration -type f -name 'V*__*.sql' -printf '%f\n' | sed -E 's/^V([0-9]+)__.*/\1 &/' | sort -n | tail -1)"
count="$(find src/main/resources/db/migration -type f -name 'V*__*.sql' | wc -l | tr -d ' ')"
printf '{"migrationCount":%s,"latestMigration":"%s","gitCommit":"%s"}\n' "$count" "${latest#* }" "$(git rev-parse HEAD 2>/dev/null || echo unknown)" > "$PHASE80_EVIDENCE_ROOT/artifacts/release-baseline.json"
if phase80_full; then
  [[ -d scripts/phase78 && -d scripts/phase79 ]] || { phase80_emit BLOCKED 'Phase 78/79 baseline not present'; exit 1; }
  [[ -z "$(git status --porcelain --untracked-files=no)" ]] || { phase80_emit BLOCKED 'tracked working tree is dirty'; exit 1; }
  phase80_require_env PHASE80_RELEASE_IMAGE_DIGEST || { phase80_emit BLOCKED 'release image digest missing'; exit 1; }
  phase80_emit PASS "authoritative baseline captured: ${latest#* } / $count migrations"
else
  phase80_emit PREPARED "baseline scanner ready; current archive has ${latest#* } / $count migrations"
fi
