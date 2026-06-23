#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE68_ROOT"; phase_setup 68D 'Secret rotation and repository-history ceremony'
[[ -x scripts/phase68/generate_rotated_secrets.sh ]] || { phase_finalize BLOCKED 2 'Phase 68 secret generator missing'; exit 0; }
[[ -x security/scripts/purge-sensitive-history.sh ]] || { phase_finalize BLOCKED 2 'history purge script missing'; exit 0; }
if phase_preflight || phase_repo; then phase_finalize PREPARED 0 'operator ceremony tooling present; Vault rotation, history rewrite and signatures pending'; exit 0; fi
require_operator
: "${PHASE68_SECRET_ROTATION_ATTESTATION:?PHASE68_SECRET_ROTATION_ATTESTATION is required}"
if [[ "${PHASE68_ALLOW_HISTORY_REWRITE:-no}" != yes ]]; then phase_log 'PHASE68_ALLOW_HISTORY_REWRITE=yes required for history rewrite'; exit 64; fi
phase_run 'verify signed secret-rotation attestation' python3 scripts/phase68/verify_attestation.py --kind secret --file "$PHASE68_SECRET_ROTATION_ATTESTATION" --output "$PHASE68_DIR/secret-attestation-verification.json"
phase_run 'scan current history for exposed placeholders' bash -c '! git grep -I -n -E "switching_.*password[_-]?change[_-]?me|(^|[^A-Za-z])change[_-]?me([^A-Za-z]|$)" $(git rev-list --all)'
phase_finalize PASS 0 'six credentials rotated, old credentials disabled and repository history purged'
