#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase81_init 81I
if phase81_full; then
  [[ -f "${PHASE81_OFFSITE_ATTESTATION:-}" ]] || { phase81_emit BLOCKED 'off-site resilience attestation missing'; exit 1; }
  python3 scripts/phase81/validate_offsite_attestation.py "$PHASE81_OFFSITE_ATTESTATION" "$PHASE81_EVIDENCE_ROOT/artifacts/offsite-attestation.json"
  phase81_emit PASS 'runbooks, off-site vault backup and knowledge transfer attested'
else phase81_emit PREPARED 'off-site resilience ceremony checklist ready'; fi
