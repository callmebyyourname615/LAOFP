#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
tmp="$PHASE76_EVIDENCE_DIR/ledger-selftest"; mkdir -p "$tmp/input"
printf one > "$tmp/input/a"; printf two > "$tmp/input/b"
python3 "$ROOT/scripts/phase76/build_evidence_ledger.py" --input-dir "$tmp/input" --output "$tmp/ledger.json" --git-commit "$GIT_COMMIT"
python3 "$ROOT/scripts/phase76/verify_evidence_ledger.py" --ledger "$tmp/ledger.json" --root "$tmp/input" > "$tmp/verify.json"
write_result 76E PASS "Evidence ledger hash-chain self-test passed"
