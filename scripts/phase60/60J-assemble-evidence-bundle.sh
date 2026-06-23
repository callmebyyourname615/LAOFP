#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE60_ROOT"
phase_setup "60J" "Evidence bundle and UAT entry gate"

PHASE_FINALIZED=false
finalize_failure() {
  local code=$?
  if [[ "$PHASE_FINALIZED" != "true" ]]; then
    phase_finalize "FAIL" "$code" "evidence bundle or UAT entry verification failed"
  fi
  exit "$code"
}
trap finalize_failure ERR

if phase_is_preflight; then
  phase_finalize "PREPARED" 0 "evidence manifest and UAT entry gate are prepared; Phases 60A-60I must pass first"
  PHASE_FINALIZED=true
  trap - ERR
  exit 0
fi

[[ "${TARGET_ENVIRONMENT:-}" == "uat" ]] || { phase_log "TARGET_ENVIRONMENT must equal uat"; exit 64; }
: "${APPLICATION_IMAGE_DIGEST:?APPLICATION_IMAGE_DIGEST is required}"
: "${MIGRATION_IMAGE_DIGEST:?MIGRATION_IMAGE_DIGEST is required}"
: "${UAT_ENTRY_ATTESTATION:?UAT_ENTRY_ATTESTATION is required}"

for phase in 60A 60B 60C 60D 60E 60F 60G 60H 60I; do
  result="$PHASE60_RUN_DIR/$phase/result.json"
  [[ -f "$result" ]] || { phase_log "missing result: $result"; exit 1; }
  python3 - "$result" <<'PY'
import json, pathlib, sys
result = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
if result.get("status") != "PASS":
    raise SystemExit(f"{result.get('phase')} is {result.get('status')}, expected PASS")
PY
done

# Validate the sign-off and prospective evidence set before marking the gate PASS.
# Temporary output stays outside the run directory to avoid circular hashes.
tmp_manifest="$(mktemp)"
trap 'rm -f "$tmp_manifest"' EXIT
python3 scripts/phase60/build_evidence_manifest.py \
  --run-dir "$PHASE60_RUN_DIR" \
  --run-id "$PHASE60_RUN_ID" \
  --application-image-digest "$APPLICATION_IMAGE_DIGEST" \
  --migration-image-digest "$MIGRATION_IMAGE_DIGEST" \
  --signoff "$UAT_ENTRY_ATTESTATION" \
  --output "$tmp_manifest"
python3 scripts/phase60/verify_evidence_manifest.py \
  --manifest "$tmp_manifest" --run-dir "$PHASE60_RUN_DIR"

# Finalize first, then bind the immutable 60J result into the final manifest.
phase_finalize "PASS" 0 "all Phase 60 gates passed and were approved for UAT entry"
PHASE_FINALIZED=true
trap - ERR

manifest="$PHASE60_RUN_DIR/phase60-evidence-manifest.json"
python3 scripts/phase60/build_evidence_manifest.py \
  --run-dir "$PHASE60_RUN_DIR" \
  --run-id "$PHASE60_RUN_ID" \
  --application-image-digest "$APPLICATION_IMAGE_DIGEST" \
  --migration-image-digest "$MIGRATION_IMAGE_DIGEST" \
  --signoff "$UAT_ENTRY_ATTESTATION" \
  --output "$manifest" >/dev/null
python3 scripts/phase60/verify_evidence_manifest.py \
  --manifest "$manifest" --run-dir "$PHASE60_RUN_DIR" >/dev/null

bundle="$PHASE60_EVIDENCE_ROOT/phase60-${PHASE60_RUN_ID}.tar.gz"
tar -C "$PHASE60_EVIDENCE_ROOT" -czf "$bundle" "$PHASE60_RUN_ID"
sha256sum "$bundle" > "$bundle.sha256"
