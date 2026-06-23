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

echo
echo "=========================================="
echo "  ALL CHECKS PASS"
echo "  Evidence bundle: $EVIDENCE_DIR"
echo "=========================================="
