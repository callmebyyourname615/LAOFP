#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68E 'SMOS runtime provisioning and security audit'
phase_run 'audit admin endpoint authorization' python3 scripts/phase68/audit_admin_authorization.py --output "$PHASE68_DIR/authorization-audit.json"
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'authorization code audit passed; UAT operators, MFA and runtime tests pending'; exit 0; fi
require_uat
: "${PHASE68_SMOS_ATTESTATION:?PHASE68_SMOS_ATTESTATION is required}"
if [[ -n "${PHASE68_SMOS_PROVISIONING_PLAN:-}" ]]; then
  phase_run 'provision SMOS operators' python3 scripts/phase65/provision_smos_operators.py --plan "$PHASE68_SMOS_PROVISIONING_PLAN"
fi
phase_run 'SMOS security integration tests' ./mvnw -B -Dtest=SmosUserManagementIntegrationTest,SmosSecurityCertificationIntegrationTest,SmosSessionSecurityIntegrationTest test
phase_run 'verify SMOS runtime attestation' python3 scripts/phase68/verify_attestation.py --kind smos --file "$PHASE68_SMOS_ATTESTATION" --output "$PHASE68_DIR/smos-attestation-verification.json"
phase_finalize PASS 0 'SMOS operators provisioned; MFA, RBAC, maker-checker and participant isolation passed'
