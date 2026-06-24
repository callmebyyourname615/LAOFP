#!/usr/bin/env bash
# Orchestrator: runs production-readiness execute & verify in order.
# Each step is independently runnable (see scripts/execute-and-verify/0X-*.sh).
# Fail-fast: stops at first failing step so you fix-and-resume.
set -euo pipefail

cd "$(dirname "$0")/../.."
EV_DIR="scripts/execute-and-verify"
EVIDENCE_DIR="$EV_DIR/evidence/$(date +%Y%m%d_%H%M%S)"
mkdir -p "$EVIDENCE_DIR"
export EVIDENCE_DIR

echo "=========================================="
echo "  Production Readiness — Execute & Verify"
echo "  Evidence: $EVIDENCE_DIR"
echo "=========================================="

run() {
  local step="$1"; shift
  local label="$1"; shift
  echo
  echo ">>> [$step] $label"
  if "$EV_DIR/$step.sh" 2>&1 | tee "$EVIDENCE_DIR/$step.log"; then
    echo ">>> [$step] PASS"
  else
    echo ">>> [$step] FAIL — see $EVIDENCE_DIR/$step.log"
    exit 1
  fi
}

run 01-verify-schema-v83          "Action #1 — V83 schema alignment + Flyway clean-run"
run 02-verify-metrics-activation  "Action #2 — OperationalMetrics profile activation"
run 03-run-static-and-tests       "Action #3 — Static verifiers + Maven test suite"
run 04-credential-rotation-check  "Action #4 — Credential rotation checklist (read-only)"
run 05-runtime-evidence-check     "Action #5 — Runtime evidence inventory (read-only)"
run 06-phase60-preflight          "Action #6 — Phase 60A-60J repository preflight"
run 07-phase61-preflight          "Action #7 — Phase 61A-61J certification preflight"
run 08-phase62-preflight          "Action #8 — Phase 62A-62J implementation preflight"
run 09-phase65-preflight          "Action #9 — Phase 65A-65J execution and handoff preflight"
run 10-phase68-preflight          "Action #10 — Phase 68A-68J UAT activation and Phase 54 kickoff preflight"
run 11-phase71-preflight          "Action #11 — Phase 71A-71J UAT certification closure preflight"
run 14-phase74-uat-runtime-closure "Action #14 — Phase 74 UAT runtime certification closure preflight"
run 15-phase75-production-handoff "Action #15 — Phase 75 Phase 54 acceptance and production handoff preflight"
run 16-phase78-final-uat-execution "Action #16 — Phase 78 final UAT execution closure preflight"
run 17-phase79-production-golive "Action #17 — Phase 79 production Go-Live preflight"

echo
echo "=========================================="
echo "  ALL CHECKS PASS"
echo "  Evidence bundle: $EVIDENCE_DIR"
echo "=========================================="
