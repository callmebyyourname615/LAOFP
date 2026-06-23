#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE71_ROOT"; phase_setup 71E 'Secret rotation and repository purge ceremony'
for f in security/scripts/purge-sensitive-history.sh docs/security/SECRET_ROTATION_CHECKLIST.md; do require_file "$f"; done
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'rotation and purge controls present; SecOps ceremony pending'; exit 0; fi
require_uat; require_flag PHASE71_EXECUTE_OPERATOR_ACTIONS
: "${PHASE71_SECRET_ROTATION_ATTESTATION:?PHASE71_SECRET_ROTATION_ATTESTATION is required}"
phase_run 'verify signed secret-rotation attestation' python3 scripts/phase71/verify_attestation.py --kind secret-rotation --file "$PHASE71_SECRET_ROTATION_ATTESTATION" --output "$PHASE71_DIR/secret-rotation-attestation.json"
phase_run 'scan current repository with gitleaks' gitleaks git --redact --report-format json --report-path "$PHASE71_DIR/gitleaks.json" .
if git log --all --grep='change_me' --oneline | grep -q .; then phase_log 'change_me remains in Git history'; exit 1; fi
phase_finalize PASS 0 'credentials rotated, old credentials disabled and repository history scan passed'
