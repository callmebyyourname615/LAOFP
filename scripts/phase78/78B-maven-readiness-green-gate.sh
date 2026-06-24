#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE78_ROOT"; phase_setup 78B 'Maven and readiness green gate'
if phase_preflight || phase_repo; then [[ -x ./mvnw && -f pom.xml ]] || { phase_finalize BLOCKED 2 'Maven wrapper or pom missing'; exit 0; }; phase_finalize PREPARED 0 'Maven and readiness execution ready'; exit 0; fi
require_flag PHASE78_EXECUTE_MAVEN
phase_run 'Maven clean verify' ./mvnw --batch-mode --no-transfer-progress clean verify
phase_run 'readiness orchestrator' ./scripts/execute-and-verify/00-run-all.sh
phase_finalize PASS 0 'Maven and readiness checks passed'
