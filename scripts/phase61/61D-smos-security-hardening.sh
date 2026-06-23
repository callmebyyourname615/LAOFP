#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61D" "SMOS production security hardening"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="SMOS hardening certification failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
phase_run "SMOS static contract" python3 scripts/verify_smos_user_management_static.py
phase_run "Phase 61 SMOS hardening contract" python3 scripts/phase61/verify_smos_hardening.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="password policy, participant scope, token hardening, session inventory and reuse detection tests are ready"; exit 0
fi
phase_run "SMOS hardened security suite" ./mvnw -B \
  -Dtest=PasswordPolicyServiceTest,TotpServiceTest,SmosTokenServiceTest,SmosUserManagementIntegrationTest,SmosSecurityCertificationIntegrationTest,SmosSessionSecurityIntegrationTest test
PHASE_STATUS="PASS"; PHASE_MESSAGE="SMOS password/MFA/token/session/RBAC/maker-checker security hardening tests passed"
