#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63J 'UAT evidence bundle, integrity manifest and entry gate'
STATUS=FAIL; MESSAGE='Phase 63 UAT entry gate failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file scripts/phase63/build_evidence_manifest.py
phase63_require_file scripts/phase63/verify_evidence_manifest.py
attestation_args=()
if phase63_is_full; then
  : "${PHASE63_UAT_ENTRY_ATTESTATION:?PHASE63_UAT_ENTRY_ATTESTATION is required}"
  phase63_require_attestation "$PHASE63_UAT_ENTRY_ATTESTATION"
  attestation_args=(--attestation "$PHASE63_UAT_ENTRY_ATTESTATION")
fi
phase63_run 'build immutable Phase 63 evidence manifest' python3 scripts/phase63/build_evidence_manifest.py \
  --run-dir "$PHASE63_RUN_DIR" --mode "$PHASE63_MODE" "${attestation_args[@]}" \
  --output "$PHASE63_RUN_DIR/manifest.json" --checksums "$PHASE63_RUN_DIR/SHA256SUMS"
phase63_run 'verify Phase 63 evidence manifest' python3 scripts/phase63/verify_evidence_manifest.py \
  --manifest "$PHASE63_RUN_DIR/manifest.json" --run-dir "$PHASE63_RUN_DIR" --mode "$PHASE63_MODE"
if phase63_is_full; then
  STATUS=PASS; MESSAGE='all 63A-63I runtime gates PASS and signed UAT entry evidence is integrity-verified'
else
  STATUS=PREPARED; MESSAGE='draft evidence manifest is valid; full UAT execution and signatures remain required'
fi
