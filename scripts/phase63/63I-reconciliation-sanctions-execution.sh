#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63I 'Settlement, reconciliation and sanctions runtime evidence'
STATUS=FAIL; MESSAGE='reconciliation/sanctions execution failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file scripts/phase61/61H-settlement-reconciliation-scale.sh
phase63_require_file scripts/phase63/verify_sanctions_attestation.py
phase63_require_file src/main/java/com/example/switching/aml/sanctions/SanctionsNameNormalizer.java
if ! phase63_is_full; then
  export PHASE61_RUN_ID="${PHASE63_RUN_ID}-63I-preflight"
  export PHASE61_EVIDENCE_ROOT="$PHASE63_PHASE_DIR/phase61-preflight"
  export PHASE61_PREFLIGHT_ONLY=true
  phase63_run 'Phase 61H settlement/reconciliation contract' scripts/phase61/61H-settlement-reconciliation-scale.sh
  STATUS=PREPARED; MESSAGE='500K settlement, financial reconciliation and sanctions-sync evidence contracts are ready'; exit 0
fi
phase63_require_uat_confirmation
: "${PHASE63_SETTLEMENT_ATTESTATION:?PHASE63_SETTLEMENT_ATTESTATION is required}"
: "${PHASE63_SANCTIONS_ATTESTATION:?PHASE63_SANCTIONS_ATTESTATION is required}"
phase63_require_attestation "$PHASE63_SETTLEMENT_ATTESTATION"
phase63_require_attestation "$PHASE63_SANCTIONS_ATTESTATION"
export PHASE61_RUN_ID="${PHASE63_RUN_ID}-63I"
export PHASE61_EVIDENCE_ROOT="$PHASE63_PHASE_DIR/phase61-runtime"
export PHASE61_PREFLIGHT_ONLY=false
export PHASE61_EXECUTE_RUNTIME=true
export SETTLEMENT_ATTESTATION="$PHASE63_SETTLEMENT_ATTESTATION"
phase63_run 'Phase 61H full settlement/reconciliation certification' scripts/phase61/61H-settlement-reconciliation-scale.sh
phase63_controlled_command sanctions-sync "${PHASE63_SANCTIONS_SYNC_COMMAND:-}" "$PHASE63_PHASE_DIR/sanctions-sync.log"
phase63_run 'sanctions runtime evidence verification' python3 scripts/phase63/verify_sanctions_attestation.py \
  --attestation "$PHASE63_SANCTIONS_ATTESTATION" --runtime-log "$PHASE63_PHASE_DIR/sanctions-sync.log" \
  --output "$PHASE63_PHASE_DIR/sanctions-verification.json"
cp "$PHASE61_EVIDENCE_ROOT/$PHASE61_RUN_ID/61H/result.json" "$PHASE63_PHASE_DIR/phase61-61H-result.json"
STATUS=PASS; MESSAGE='500K settlement balanced with zero duplicates/loss and sanctions sync/normalization evidence passed'
