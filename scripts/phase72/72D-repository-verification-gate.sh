#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72D
if ! phase72_run_logged 72D-static python3 "$PHASE72_ROOT/scripts/verify_phase72_static.py" --root "$PHASE72_ROOT"; then
  phase72_result "$phase" FAIL "Phase 72 static contract failed"; exit 1
fi
[[ -x "$PHASE72_ROOT/scripts/execute-and-verify/00-run-all.sh" ]] || { phase72_result "$phase" BLOCKED "Repository verification orchestrator is missing"; exit 2; }
if [[ "$PHASE72_MODE" != full ]]; then
  phase72_result "$phase" PREPARED "Static contract passed; repository runtime gate is deferred to full mode"; exit 0
fi
phase72_require_full "$phase" PHASE72_CONFIRM_FULL
if phase72_run_logged 72D-repository-gate "$PHASE72_ROOT/scripts/execute-and-verify/00-run-all.sh"; then
  phase72_result "$phase" PASS "Repository execute-and-verify gate passed"
else
  phase72_result "$phase" FAIL "Repository execute-and-verify gate failed"; exit 1
fi
