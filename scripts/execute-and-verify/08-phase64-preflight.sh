#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
python3 scripts/verify_phase64_static.py
python3 -m unittest discover -s scripts/phase64/tests -p 'test_*.py' -v
PHASE64_RUN_ID="verify-phase64-$(date -u +%Y%m%dT%H%M%SZ)" \
PHASE64_EVIDENCE_ROOT="${PHASE64_EVIDENCE_ROOT:-build/phase64-preflight}" \
  scripts/phase64/run_phase64.sh --preflight
