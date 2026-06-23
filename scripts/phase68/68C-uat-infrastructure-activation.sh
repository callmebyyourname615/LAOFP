#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68C 'UAT infrastructure activation'
phase_run 'validate UAT contract syntax' python3 -c 'import yaml; yaml.safe_load(open("config/phase68-uat-activation.yaml")); print("PASS")'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'UAT activation contract ready; 24-hour stability and live probes pending'; exit 0; fi
require_uat
: "${PHASE68_UAT_ATTESTATION:?PHASE68_UAT_ATTESTATION is required}"
phase_run 'run live UAT dependency probes' env PHASE68_UAT_PROBE_OUTPUT="$PHASE68_DIR/uat-probes.json" python3 scripts/phase68/probe_uat_runtime.py
phase_run 'verify UAT attestation' python3 scripts/phase68/verify_attestation.py --kind uat --file "$PHASE68_UAT_ATTESTATION" --output "$PHASE68_DIR/uat-attestation-verification.json"
phase_finalize PASS 0 'UAT dependencies, TLS, replica and 24-hour stability attested'
