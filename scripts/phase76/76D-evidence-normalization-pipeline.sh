#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
tmp="$PHASE76_EVIDENCE_DIR/normalizer-selftest"; mkdir -p "$tmp"
cat > "$tmp/junit.xml" <<'XML'
<testsuite tests="2" failures="0" errors="0" skipped="0"></testsuite>
XML
python3 "$ROOT/scripts/phase76/normalize_evidence.py" --type junit --input "$tmp/junit.xml" --output "$tmp/result.json" --control-id NORMALIZER-SELFTEST --git-commit "$GIT_COMMIT" --synthetic
python3 -c 'import json,sys; assert json.load(open(sys.argv[1]))["status"]=="PASS"' "$tmp/result.json"
write_result 76D PASS "Normalizer self-test passed"
