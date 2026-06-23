#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69H
cd "$PHASE69_ROOT"
gate=scripts/execute-and-verify/00-run-all.sh
[[ -x "$gate" ]] || { phase69_result "$phase" FAIL "$gate is missing or not executable"; exit 1; }
if [[ "$PHASE69_MODE" != full ]]; then
  phase69_result "$phase" PREPARED "Repository gate orchestrator exists; full mode is required for execution" --evidence "$gate"
  exit 0
fi
phase69_require_full_confirmation "$phase" || true
if phase69_run_logged "$phase-repository-gates" "$gate"; then
  phase69_result "$phase" PASS "Production-readiness repository gates pass" --evidence "logs/$phase-repository-gates.log"
else
  phase69_result "$phase" FAIL "Production-readiness repository gates failed" --evidence "logs/$phase-repository-gates.log"
  exit 1
fi
