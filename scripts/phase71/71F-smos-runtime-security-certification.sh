#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71F 'SMOS runtime security certification'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'SMOS security suite and provisioning contract ready'; exit 0; fi
require_uat
: "${PHASE71_SMOS_ATTESTATION:?PHASE71_SMOS_ATTESTATION is required}"
phase_run 'run SMOS security integration suite' ./mvnw -B -Dtest=SmosUserManagementIntegrationTest,SmosSecurityCertificationIntegrationTest,SmosSessionSecurityIntegrationTest,PasswordPolicyServiceTest test
phase_run 'verify SMOS runtime attestation' python3 scripts/phase71/verify_attestation.py --kind smos-runtime --file "$PHASE71_SMOS_ATTESTATION" --output "$PHASE71_DIR/smos-attestation.json"
phase_finalize PASS 0 'initial operators, TOTP, RBAC, sessions and maker-checker certified'
