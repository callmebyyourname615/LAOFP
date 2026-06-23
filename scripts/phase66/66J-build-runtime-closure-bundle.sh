#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66J" "Runtime closure bundle and Phase 54 decision"
STATUS="FAIL"; MESSAGE="runtime closure bundle failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
args=(--run-dir "$PHASE66_RUN_DIR" --schema schemas/phase66/runtime-closure-manifest.schema.json --output "$PHASE66_RUN_DIR/manifest.json" --decision-output "$PHASE66_RUN_DIR/PHASE54_ENTRY_DECISION.json")
[[ -n "${PHASE66_APPROVAL_FILE:-}" ]] && args+=(--approval "$PHASE66_APPROVAL_FILE")
phase66_run "build tamper-evident closure bundle" python3 scripts/phase66/build_runtime_closure_bundle.py "${args[@]}"
decision=$(python3 -c 'import json,sys;print(json.load(open(sys.argv[1]))["decision"])' "$PHASE66_RUN_DIR/PHASE54_ENTRY_DECISION.json")
case "$decision" in
  CERTIFIED) STATUS="PASS"; MESSAGE="all runtime controls passed and signed Phase 54 entry approval verified" ;;
  PREPARED) STATUS="PREPARED"; MESSAGE="framework preflight is complete; runtime evidence and approval remain required" ;;
  *) STATUS="BLOCKED"; MESSAGE="Phase 54 entry blocked by missing or failed runtime evidence"; exit 2 ;;
esac
