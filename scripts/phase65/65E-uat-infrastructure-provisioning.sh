#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65E 'UAT infrastructure provisioning and contract'
[[ -f config/phase65-uat-infrastructure-contract.yaml ]] || exit 1
if phase_preflight; then phase_finalize PREPARED 0 'UAT contract and executable probes are ready'; exit 0; fi
require_uat; : "${PHASE65_UAT_INFRA_ATTESTATION:?attestation required}"
phase_run 'UAT infrastructure probes' python3 scripts/phase65/probe_uat_environment.py --execute --attestation "$PHASE65_UAT_INFRA_ATTESTATION" --output "$PHASE65_DIR/uat-infrastructure.json"
phase_finalize PASS 0 'UAT infrastructure, TLS, digests, dependency health and replica topology certified'
