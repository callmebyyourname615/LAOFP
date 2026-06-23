#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE73_ROOT"
phase73_setup "73J" "Signed resilience certification and Phase 54/55 gate"
STATUS=FAIL; MESSAGE="resilience bundle assembly failed"
trap 'rc=$?; phase73_finalize "$STATUS" "$rc" "$MESSAGE"' EXIT
for tool in scripts/phase73/build_resilience_bundle.py scripts/phase73/verify_bundle.py; do phase73_require_file "$tool"; done
if phase73_is_preflight; then
  phase73_require_command openssl
  STATUS=PREPARED; MESSAGE="signed resilience bundle and release gate contract are ready"; exit 0
fi
phase73_require_execution_approval
phase73_require_command openssl
: "${PHASE73_SIGNING_KEY:?PHASE73_SIGNING_KEY is required}"
[[ -f "$PHASE73_SIGNING_KEY" ]] || { phase73_log "ERROR signing key not found"; exit 66; }
summary="$PHASE73_RUN_DIR/73I/scenario-summary.json"
phase73_require_file "$summary"
bundle_dir="$PHASE73_PHASE_DIR/bundle"
phase73_run "build signed bundle" python3 scripts/phase73/build_resilience_bundle.py \
  --run-dir "$PHASE73_RUN_DIR" --approval "$CHAOS_APPROVAL_FILE" --scenario-summary "$summary" \
  --bundle-dir "$bundle_dir" --signing-key "$PHASE73_SIGNING_KEY"
phase73_run "verify signed bundle" python3 scripts/phase73/verify_bundle.py --bundle-dir "$bundle_dir"
phase73_run "archive bundle" tar -C "$PHASE73_PHASE_DIR" -czf "$PHASE73_PHASE_DIR/phase73-resilience-bundle.tar.gz" bundle
STATUS=PASS; MESSAGE="signed resilience certification bundle verified; Phase 54/55 resilience gate is green"
