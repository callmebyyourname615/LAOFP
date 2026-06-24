#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(cd "$(dirname "$0")/../.." && pwd)"
source "$(dirname "$0")/common.sh"
# Preliminary integrity check before the phase marks itself complete.
python3 "$ROOT/scripts/phase76/build_evidence_ledger.py" --input-dir "$PHASE76_EVIDENCE_DIR/results" --output "$PHASE76_EVIDENCE_DIR/evidence-ledger.json" --git-commit "$GIT_COMMIT"
python3 "$ROOT/scripts/phase76/verify_evidence_ledger.py" --ledger "$PHASE76_EVIDENCE_DIR/evidence-ledger.json" --root "$PHASE76_EVIDENCE_DIR/results" > "$PHASE76_EVIDENCE_DIR/ledger-validation.json"
python3 "$ROOT/scripts/phase76/build_release_bundle.py" --evidence-dir "$PHASE76_EVIDENCE_DIR" --output "$PHASE76_EVIDENCE_DIR/release-readiness-manifest.json" --git-commit "$GIT_COMMIT"
write_result 76J PASS "Release-readiness bundle prepared; human signatures not asserted"
# Final ledger and bundle include the 76J result itself.
python3 "$ROOT/scripts/phase76/build_evidence_ledger.py" --input-dir "$PHASE76_EVIDENCE_DIR/results" --output "$PHASE76_EVIDENCE_DIR/evidence-ledger.json" --git-commit "$GIT_COMMIT"
python3 "$ROOT/scripts/phase76/verify_evidence_ledger.py" --ledger "$PHASE76_EVIDENCE_DIR/evidence-ledger.json" --root "$PHASE76_EVIDENCE_DIR/results" > "$PHASE76_EVIDENCE_DIR/ledger-validation.json"
python3 "$ROOT/scripts/phase76/build_release_bundle.py" --evidence-dir "$PHASE76_EVIDENCE_DIR" --output "$PHASE76_EVIDENCE_DIR/release-readiness-manifest.json" --git-commit "$GIT_COMMIT"
