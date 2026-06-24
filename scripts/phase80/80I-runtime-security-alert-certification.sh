#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/lib.sh"

phase80_init 80I
if phase80_full; then
  [[ -n "${PHASE80_SMOS_SECURITY_COMMAND:-}" && -n "${PHASE80_ALERT_LIFECYCLE_COMMAND:-}" ]] || { phase80_emit BLOCKED 'SMOS/alert commands missing'; exit 1; }
  bash -lc "$PHASE80_SMOS_SECURITY_COMMAND" > "$PHASE80_EVIDENCE_ROOT/artifacts/smos-security.log" 2>&1
  bash -lc "$PHASE80_ALERT_LIFECYCLE_COMMAND" > "$PHASE80_EVIDENCE_ROOT/artifacts/alert-lifecycle.log" 2>&1
  [[ -f "${PHASE80_SECURITY_ATTESTATION:-}" ]] || { phase80_emit BLOCKED 'security attestation missing'; exit 1; }
  python3 scripts/phase80/validate_attestation.py "$PHASE80_SECURITY_ATTESTATION" security "$PHASE80_EVIDENCE_ROOT/artifacts/security-attestation.json"
  phase80_emit PASS 'SMOS security, RBAC and alert lifecycle certified'
else phase80_emit PREPARED 'security and alert hooks ready; no live identities changed'; fi
