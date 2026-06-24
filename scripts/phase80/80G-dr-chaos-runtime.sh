#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80G
if phase80_full; then
  [[ "${PHASE80_ALLOW_CHAOS:-false}" == true ]] || { phase80_emit BLOCKED 'chaos confirmation missing'; exit 1; }
  if [[ -n "${PHASE80_DR_CHAOS_COMMAND:-}" ]]; then bash -lc "$PHASE80_DR_CHAOS_COMMAND" > "$PHASE80_EVIDENCE_ROOT/artifacts/dr-chaos.log" 2>&1
  else phase80_run dr-suite dr/scripts/run-dr-suite.sh; fi
  [[ -f "${PHASE80_DR_ATTESTATION:-}" ]] || { phase80_emit BLOCKED 'DR attestation missing'; exit 1; }
  python3 scripts/phase80/validate_attestation.py "$PHASE80_DR_ATTESTATION" dr "$PHASE80_EVIDENCE_ROOT/artifacts/dr-attestation.json"
  phase80_emit PASS 'DR and chaos campaign attested'
else phase80_emit PREPARED 'DR/chaos campaign ready; blast-radius actions not started'; fi
