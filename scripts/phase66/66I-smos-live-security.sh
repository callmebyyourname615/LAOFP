#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66I" "SMOS live security and provisioning"
STATUS="FAIL"; MESSAGE="SMOS live security certification failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase66_require_file config/phase66/smos-runtime-checks.yaml
if ! phase66_is_full; then
  phase66_run "validate SMOS runtime check contract" python3 scripts/phase66/run_smos_security_checks.py \
    --config config/phase66/smos-runtime-checks.yaml --output "$PHASE66_PHASE_DIR/smos-security.json" --contract-only
  STATUS="PREPARED"; MESSAGE="SMOS RBAC/MFA/session/maker-checker checks are ready; no users provisioned"; exit 0
fi
phase66_require_uat
: "${SMOS_BASE_URL:?SMOS_BASE_URL required}" "${SMOS_OPERATOR_TOKEN:?SMOS_OPERATOR_TOKEN required}"
: "${SMOS_PROVISION_INITIAL_ADMINS_COMMAND:?SMOS_PROVISION_INITIAL_ADMINS_COMMAND required}"
: "${SMOS_SECURITY_LIFECYCLE_COMMAND:?SMOS_SECURITY_LIFECYCLE_COMMAND required for MFA, token refresh/revoke and maker-checker tests}"
phase66_capture "provision initial UAT admins" "$PHASE66_PHASE_DIR/admin-provisioning.log" bash -Eeuo pipefail -c "$SMOS_PROVISION_INITIAL_ADMINS_COMMAND"
phase66_capture "exercise MFA/session/maker-checker lifecycle" "$PHASE66_PHASE_DIR/security-lifecycle.log" bash -Eeuo pipefail -c "$SMOS_SECURITY_LIFECYCLE_COMMAND"
phase66_run "execute SMOS live security checks" python3 scripts/phase66/run_smos_security_checks.py \
  --config config/phase66/smos-runtime-checks.yaml --output "$PHASE66_PHASE_DIR/smos-security.json"
STATUS="PASS"; MESSAGE="SMOS live authorization, MFA, session and maker-checker controls passed"
