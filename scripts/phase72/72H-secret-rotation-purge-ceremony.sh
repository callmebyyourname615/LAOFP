#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72H
if [[ "$PHASE72_MODE" != full ]]; then phase72_result "$phase" PREPARED "Secret rotation ceremony requires signed operator evidence in full mode"; exit 0; fi
phase72_require_full "$phase" PHASE72_CONFIRM_SECRET_ROTATION
att="${PHASE72_SECRET_ROTATION_ATTESTATION:-}"
[[ -f "$att" ]] || { phase72_result "$phase" BLOCKED "PHASE72_SECRET_ROTATION_ATTESTATION is required"; exit 2; }
if [[ -n "${PHASE72_SECRET_ROTATION_COMMAND:-}" ]] && ! phase72_run_logged 72H-operator-command bash -lc "$PHASE72_SECRET_ROTATION_COMMAND"; then
  phase72_result "$phase" FAIL "Secret rotation operator command failed"; exit 1
fi
if python3 "$PHASE72_ROOT/scripts/phase72/validate_attestation.py" --type secret --path "$att" --git-commit "${PHASE72_GIT_SHA:-$(phase72_git_sha)}" | tee "$PHASE72_LOG_DIR/72H-attestation.log"; then
  cp "$att" "$PHASE72_ARTIFACT_DIR/secret-rotation-attestation.json"
  phase72_result "$phase" PASS "Secret rotation and repository purge ceremony is signed and commit-matched"
else phase72_result "$phase" FAIL "Secret rotation attestation is incomplete, unsafe or commit-mismatched"; exit 1; fi
