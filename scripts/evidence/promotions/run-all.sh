#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../../.."

STAMP="${STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
export STAMP
export EVDIR="${EVDIR:-runtime-evidence/promotion-scenarios-$STAMP}"

scripts/evidence/promotions/00-smoke-list.sh
scripts/evidence/promotions/01-create-delete-draft.sh
scripts/evidence/promotions/02-maker-checker-activate-suspend.sh
scripts/evidence/promotions/03-delete-non-draft-should-fail.sh
scripts/evidence/promotions/04-report-extend.sh

cat > "$EVDIR/SUMMARY.md" <<EOF
# Promotion Scenario Test Summary

Status: PASS
Target: ${TARGET:-https://175.11.0.200}
Generated at: $(date -u +%Y-%m-%dT%H:%M:%SZ)

Scenarios:
- 00 smoke/list
- 01 create/delete draft
- 02 maker-checker activate/suspend
- 03 delete non-draft rejection
- 04 report/extend
EOF

find "$EVDIR" -type f ! -name SHA256SUMS -print0 \
  | sort -z \
  | xargs -0 shasum -a 256 \
  > "$EVDIR/SHA256SUMS"

echo "EVDIR=$EVDIR"
