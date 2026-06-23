#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/common.sh"
phase=69J
cd "$PHASE69_ROOT"
attestation="${PHASE69_ATTESTATION:-$PHASE69_ROOT/docs/templates/PHASE69_RELEASE_VERIFICATION_ATTESTATION.example.json}"
manifest="$PHASE69_EVIDENCE_ROOT/phase69-verification-manifest.json"
decision=$(python3 scripts/phase69/build_verification_manifest.py \
  --evidence-root "$PHASE69_EVIDENCE_ROOT" --attestation "$attestation" --output "$manifest")
case "$decision" in
  VERIFIED) status=PASS; message='Phase 69 verification bundle is VERIFIED' ;;
  PREPARED) status=PREPARED; message='Phase 69 verification framework is PREPARED; runtime gates are not yet executed' ;;
  *) status=BLOCKED; message='Phase 69 verification bundle is BLOCKED' ;;
esac
phase69_result "$phase" "$status" "$message" --evidence phase69-verification-manifest.json --evidence SHA256SUMS --metric decision="$decision"
[[ "$decision" != BLOCKED ]]
