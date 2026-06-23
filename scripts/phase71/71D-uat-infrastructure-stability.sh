#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71D 'UAT infrastructure provisioning and stability'
require_file config/phase71-uat-certification.yaml
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'UAT contract and live probes ready; 24-hour stability evidence pending'; exit 0; fi
require_uat
: "${PHASE71_UAT_ATTESTATION:?PHASE71_UAT_ATTESTATION is required}"
phase_run 'probe live UAT dependencies' python3 scripts/phase71/probe_uat.py --output "$PHASE71_DIR/uat-probes.json"
phase_run 'verify UAT attestation' python3 scripts/phase71/verify_attestation.py --kind uat-infrastructure --file "$PHASE71_UAT_ATTESTATION" --output "$PHASE71_DIR/uat-attestation.json"
phase_finalize PASS 0 'UAT dependencies healthy and stability attestation verified'
