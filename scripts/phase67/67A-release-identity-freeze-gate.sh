#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
p67_require_identity
PHASE_ID=67A
p67_require_environment release
p67_require_production_confirmation
p67_begin 67A "Release Identity and Change-Freeze Gate"
failed=0
args=(
  --repository "${PHASE67_REPOSITORY_ROOT:-.}"
  --attestation "${CHANGE_FREEZE_ATTESTATION:-}"
  --minimum-approvers 2
  --maximum-age-seconds 1800
  --output "$PHASE_DIR/release-freeze-gate.json"
  --reference "$RELEASE_REFERENCE"
  --rc-id "$RELEASE_RC_ID"
  --git-commit "$RELEASE_GIT_COMMIT"
  --application-digest "$RELEASE_APP_IMAGE_DIGEST"
  --migration-digest "$RELEASE_MIGRATION_IMAGE_DIGEST"
  --environment "$PHASE67_ENVIRONMENT"
  --mode "$PHASE67_MODE"
)
p67_run_check release-freeze python3 scripts/phase67/phase67_control.py freeze "${args[@]}" || failed=1
if (( failed )); then p67_write_result FAIL; exit 1; fi
p67_write_result
