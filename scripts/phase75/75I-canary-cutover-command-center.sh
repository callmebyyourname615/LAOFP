#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE75_ROOT"; phase_setup 75I 'Canary, cutover and rollback command-center readiness'
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'named command-center roles, canary gates and abort criteria ready'; exit 0; fi
require_prod; require_identity
: "${PHASE75_COMMAND_CENTER_ATTESTATION:?PHASE75_COMMAND_CENTER_ATTESTATION required}"
phase_run 'command-center attestation' python3 scripts/phase75/verify_production_attestation.py --kind command-center --file "$PHASE75_COMMAND_CENTER_ATTESTATION" --output "$PHASE75_DIR/command-center.json" --commit "$PHASE75_COMMIT" --application-digest "$APPLICATION_IMAGE_DIGEST" --migration-digest "$MIGRATION_IMAGE_DIGEST"
phase_finalize PASS 0 'command center staffed, rollback rehearsed and canary gates approved'
