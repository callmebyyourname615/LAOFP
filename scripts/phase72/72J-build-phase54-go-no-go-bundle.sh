#!/usr/bin/env bash
set -euo pipefail
source "$(cd "$(dirname "$0")" && pwd)/common.sh"
phase=72J
manifest="$PHASE72_EVIDENCE_ROOT/phase72-final-uat-manifest.json"
args=(--evidence-root "$PHASE72_EVIDENCE_ROOT" --output "$manifest")
[[ -n "${PHASE72_FINAL_ATTESTATION:-}" ]] && args+=(--attestation "$PHASE72_FINAL_ATTESTATION")
decision=$(python3 "$PHASE72_ROOT/scripts/phase72/build_phase72_manifest.py" "${args[@]}")
case "$decision" in
  GO) status=PASS; msg="Phase 72 evidence is complete and Phase 54 decision is GO";;
  PREPARED) status=PREPARED; msg="Phase 72 package is prepared; runtime execution is incomplete";;
  NO_GO) status=BLOCKED; msg="Runtime phases passed but final GO requirements are not satisfied";;
  *) status=BLOCKED; msg="Phase 72 evidence contains missing, failed or blocked phases";;
esac
phase72_result "$phase" "$status" "$msg" --detail decision="$decision"
# Rebuild once so 72J result itself is represented by the package checksum on the next external packaging pass.
if [[ "$decision" == GO ]]; then exit 0; fi
[[ "$PHASE72_MODE" == preflight && "$decision" == PREPARED ]] && exit 0
exit 2
