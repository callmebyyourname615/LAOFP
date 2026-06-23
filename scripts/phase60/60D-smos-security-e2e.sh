#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60D" "SMOS security end-to-end certification"
PHASE_STATUS="FAIL"
PHASE_MESSAGE="SMOS security certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT

phase_run "SMOS static contract" python3 scripts/verify_smos_user_management_static.py

if phase_is_preflight; then
  PHASE_STATUS="PREPARED"
  PHASE_MESSAGE="SMOS static contract and certification tests are present; execution requires Maven/Testcontainers"
  exit 0
fi

phase_run "SMOS service and security integration tests" ./mvnw -B \
  -Dtest=TotpServiceTest,SmosTokenServiceTest,SmosUserManagementIntegrationTest,SmosSecurityCertificationIntegrationTest \
  test

PHASE_STATUS="PASS"
PHASE_MESSAGE="SMOS login, MFA, token rotation, lockout, RBAC, maker-checker integrity and audit tests passed"
