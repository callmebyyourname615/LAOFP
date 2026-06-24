#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE74_ROOT"; phase_setup 74C 'UAT infrastructure activation and stability'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'UAT probes and 24-hour stability attestation ready'; exit 0; fi
require_uat; require_identity
: "${PHASE74_UAT_INFRA_ATTESTATION:?PHASE74_UAT_INFRA_ATTESTATION required}"
phase_run 'live dependency probes' python3 scripts/phase74/probe_uat.py --output "$PHASE74_DIR/uat-probes.json"
phase_run 'UAT infrastructure attestation' python3 scripts/phase74/verify_attestation.py --kind uat-infra --file "$PHASE74_UAT_INFRA_ATTESTATION" --output "$PHASE74_DIR/uat-attestation.json" --commit "$PHASE74_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'UAT dependency health, image identity and stability evidence passed'
