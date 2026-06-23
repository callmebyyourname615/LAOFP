#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE65_ROOT"; phase_setup 65D 'Secret rotation and repository history purge'
for f in security/scripts/generate_phase65_rotated_secrets.sh security/scripts/purge-sensitive-history.sh docs/security/SECRET_ROTATION_CHECKLIST.md; do [[ -f "$f" ]] || { phase_log "missing $f"; exit 1; }; done
if phase_preflight; then phase_finalize PREPARED 0 'rotation generator, purge procedure and attestation gate are ready'; exit 0; fi
require_operator; : "${PHASE65_SECRET_ROTATION_ATTESTATION:?attestation path required}"
phase_run 'signed rotation verification' python3 scripts/phase65/verify_secret_rotation_attestation.py --attestation "$PHASE65_SECRET_ROTATION_ATTESTATION" --output "$PHASE65_DIR/secret-rotation-certification.json"
phase_finalize PASS 0 'six credentials rotated, old values disabled and repository history/caches invalidated'
