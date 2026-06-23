#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE61_ROOT"
phase_setup "61F" "Secret rotation and software supply-chain closure"
PHASE_STATUS="FAIL"; PHASE_MESSAGE="secret/supply-chain closure failed"
trap 'code=$?; phase_finalize "$PHASE_STATUS" "$code" "$PHASE_MESSAGE"' EXIT
if phase_is_preflight; then
  phase_run "repository hygiene (pending deletions allowed)" python3 security/scripts/verify_repository_hygiene.py --allow-pending-deletions
else
  phase_run "repository hygiene" python3 security/scripts/verify_repository_hygiene.py
fi
phase_require_file security/rotation/phase61-supply-chain-inventory.yaml
phase_require_file scripts/phase61/verify_supply_chain_attestation.py
if phase_is_preflight; then
  PHASE_STATUS="PREPARED"; PHASE_MESSAGE="six-secret rotation, history purge, SBOM, scanning, signing and provenance evidence contract is ready"; exit 0
fi
phase_require_uat_confirmation
: "${SUPPLY_CHAIN_ATTESTATION:?SUPPLY_CHAIN_ATTESTATION is required}"
phase_require_operator_attestation "$SUPPLY_CHAIN_ATTESTATION"
phase_run "supply-chain and rotation attestation" python3 scripts/phase61/verify_supply_chain_attestation.py \
  --attestation "$SUPPLY_CHAIN_ATTESTATION" --inventory security/rotation/phase61-supply-chain-inventory.yaml \
  --output "$PHASE61_PHASE_DIR/supply-chain-verification.json"
PHASE_STATUS="PASS"; PHASE_MESSAGE="credentials rotated, history purged, artifacts scanned, signed, provenance-verified and digest-pinned"
