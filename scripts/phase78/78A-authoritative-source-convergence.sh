#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78A 'Authoritative source convergence'
if phase_preflight || phase_repo; then
 set +e; python3 scripts/phase78/verify_source_convergence.py --root . --output "$PHASE78_DIR/source-convergence.json" ${PHASE78_EXPECTED_COMMIT:+--expected-commit "$PHASE78_EXPECTED_COMMIT"}; rc=$?; set -e
 if ((rc==0)); then phase_finalize PREPARED 0 'source baseline converged; identity freeze pending'; else phase_finalize BLOCKED 2 'source convergence prerequisites incomplete'; fi; exit 0
fi
require_identity
phase_run 'source convergence' python3 scripts/phase78/verify_source_convergence.py --root . --output "$PHASE78_DIR/source-convergence.json" ${PHASE78_EXPECTED_COMMIT:+--expected-commit "$PHASE78_EXPECTED_COMMIT"}
phase_finalize PASS 0 'authoritative source and migrations converged'
