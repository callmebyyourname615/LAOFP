#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65C 'SMOS runtime provisioning and security audit'
phase_run 'SMOS static readiness' python3 scripts/phase65/verify_smos_runtime_readiness.py --static-only --output "$PHASE65_DIR/smos-static.json"
if phase_preflight; then phase_finalize PREPARED 0 'SMOS static controls passed; UAT provisioning and signed attestation pending'; exit 0; fi
require_uat
: "${SMOS_PROVISIONING_PLAN:?SMOS_PROVISIONING_PLAN is required}"; : "${SMOS_ATTESTATION:?SMOS_ATTESTATION is required}"
phase_run 'provision five UAT operators' python3 scripts/phase65/provision_smos_operators.py --plan "$SMOS_PROVISIONING_PLAN" --output "$PHASE65_DIR/provisioning-result.json"
phase_run 'SMOS signed runtime certification' python3 scripts/phase65/verify_smos_runtime_readiness.py --attestation "$SMOS_ATTESTATION" --output "$PHASE65_DIR/smos-certification.json"
phase_finalize PASS 0 'SMOS operators, MFA, RBAC, maker-checker and endpoint controls certified'
