#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62C "SMOS RBAC OpenAPI and endpoint security completion"
phase_run "SMOS endpoint contract" python3 scripts/phase62/verify_smos_endpoint_security.py
if phase_is_preflight; then phase_finalize PREPARED 0 "permission matrix, OpenAPI and endpoint guards are present"; exit 0; fi
phase_run "SMOS integration tests" ./mvnw -B -Dtest='*Smos*Test,*Smos*IntegrationTest' test
phase_finalize PASS 0 "SMOS endpoint and integration certification passed"
