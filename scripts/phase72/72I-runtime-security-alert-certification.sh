#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72I
if [[ "$PHASE72_MODE" != full ]]; then phase72_result "$phase" PREPARED "SMOS and alert certification requires real runtime evidence in full mode"; exit 0; fi
phase72_require_full "$phase" PHASE72_CONFIRM_RUNTIME_SECURITY
att="${PHASE72_RUNTIME_SECURITY_ATTESTATION:-}"
[[ -f "$att" ]] || { phase72_result "$phase" BLOCKED "PHASE72_RUNTIME_SECURITY_ATTESTATION is required"; exit 2; }
for item in smos:PHASE72_SMOS_CHECK_CMD alerts:PHASE72_ALERT_CHECK_CMD; do
  name=${item%%:*}; var=${item##*:}; cmd=${!var:-}
  [[ -n "$cmd" ]] || { phase72_result "$phase" BLOCKED "$var is required"; exit 2; }
  if ! phase72_run_logged "72I-$name" bash -lc "$cmd"; then phase72_result "$phase" FAIL "Runtime check failed: $name"; exit 1; fi
done
if python3 "$PHASE72_ROOT/scripts/phase72/validate_attestation.py" --type runtime --path "$att" --git-commit "${PHASE72_GIT_SHA:-$(phase72_git_sha)}" | tee "$PHASE72_LOG_DIR/72I-attestation.log"; then
  cp "$att" "$PHASE72_ARTIFACT_DIR/runtime-security-attestation.json"
  phase72_result "$phase" PASS "SMOS runtime security and alert lifecycle are signed and commit-matched"
else phase72_result "$phase" FAIL "Runtime security/alert attestation is incomplete or commit-mismatched"; exit 1; fi
