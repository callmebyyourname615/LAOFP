#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80H
if phase80_full; then
  [[ "${PHASE80_ALLOW_HISTORY_REWRITE:-false}" == true ]] || { phase80_emit BLOCKED 'history rewrite confirmation missing'; exit 1; }
  [[ -f "${PHASE80_SECRET_ROTATION_ATTESTATION:-}" ]] || { phase80_emit BLOCKED 'secret attestation missing'; exit 1; }
  python3 scripts/phase80/validate_attestation.py "$PHASE80_SECRET_ROTATION_ATTESTATION" secret "$PHASE80_EVIDENCE_ROOT/artifacts/secret-attestation.json"
  security/scripts/scan-git-history.sh > "$PHASE80_EVIDENCE_ROOT/artifacts/history-scan.log" 2>&1
  [[ -z "${PHASE80_REPOSITORY_PURGE_COMMAND:-}" ]] || bash -lc "$PHASE80_REPOSITORY_PURGE_COMMAND" > "$PHASE80_EVIDENCE_ROOT/artifacts/repository-purge.log" 2>&1
  phase80_emit PASS 'secret rotation and repository purge attested without secret values'
else phase80_emit PREPARED 'secret ceremony guardrails ready; no credentials/history changed'; fi
