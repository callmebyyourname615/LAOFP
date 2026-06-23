#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
phase63_load_env
cd "$PHASE63_ROOT"
phase63_setup 63H 'SMOS RBAC, endpoint authorization and provisioning audit'
STATUS=FAIL; MESSAGE='SMOS RBAC audit failed'
trap 'code=$?; phase63_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase63_require_file scripts/phase63/audit_smos_rbac.py
phase63_require_dir src/main/java/com/example/switching/usermgmt
phase63_run 'SMOS endpoint/RBAC static audit' python3 scripts/phase63/audit_smos_rbac.py \
  --root . --output "$PHASE63_PHASE_DIR/smos-rbac-audit.json" \
  --matrix "$PHASE63_PHASE_DIR/smos-rbac-matrix.md"
if phase63_is_full; then
  : "${PHASE63_SMOS_RUNTIME_COMMAND:?PHASE63_SMOS_RUNTIME_COMMAND is required}"
  phase63_controlled_command smos-runtime-security "$PHASE63_SMOS_RUNTIME_COMMAND" "$PHASE63_PHASE_DIR/smos-runtime-security.log"
  STATUS=PASS; MESSAGE='SMOS static RBAC audit and signed UAT runtime security suite passed'
else
  STATUS=PASS; MESSAGE='SMOS endpoint authorization, maker-checker and public-auth allowlist audit passed'
fi
