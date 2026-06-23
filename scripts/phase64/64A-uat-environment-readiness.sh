#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64A" "UAT environment readiness"
STATUS=FAIL; MESSAGE="UAT readiness validation failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file "$PHASE64_CONFIG"
phase64_require_file config/phase61-uat-infrastructure-contract.yaml
phase64_require_file scripts/phase61/probe_uat_contract.py
phase64_require_command python3
if phase64_is_preflight; then
  phase64_run "validate UAT contract structure" python3 scripts/phase61/probe_uat_contract.py \
    --contract config/phase61-uat-infrastructure-contract.yaml --preflight \
    --output "$PHASE64_PHASE_DIR/uat-readiness.json"
  STATUS=PREPARED; MESSAGE="UAT readiness probes are configured and production execution is prohibited"; exit 0
fi
phase64_require_release_identity
: "${UAT_ENV_FILE:?UAT_ENV_FILE is required}"
: "${MANAGEMENT_BASE_URL:?MANAGEMENT_BASE_URL is required}"
phase64_require_file "$UAT_ENV_FILE"
[[ "$MANAGEMENT_BASE_URL" == https://* ]] || { phase64_log "ERROR MANAGEMENT_BASE_URL must use HTTPS"; exit 64; }
phase64_run "verify checked-out release commit" bash -c 'test "$(git rev-parse HEAD)" = "$RELEASE_GIT_COMMIT"'
phase64_run "probe UAT infrastructure contract" python3 scripts/phase61/probe_uat_contract.py \
  --contract config/phase61-uat-infrastructure-contract.yaml --env-file "$UAT_ENV_FILE" \
  --base-url "$MANAGEMENT_BASE_URL" --output "$PHASE64_PHASE_DIR/uat-readiness.json"
phase64_require_command curl
curl_args=(--fail-with-body --silent --show-error --max-time 20)
[[ -n "${TLS_CA_FILE:-}" ]] && curl_args+=(--cacert "$TLS_CA_FILE")
[[ -n "${MANAGEMENT_BEARER_TOKEN:-}" ]] && curl_args+=(-H "Authorization: Bearer ${MANAGEMENT_BEARER_TOKEN}")
phase64_run "verify UAT readiness endpoint" curl "${curl_args[@]}" "$MANAGEMENT_BASE_URL/actuator/health/readiness" -o "$PHASE64_PHASE_DIR/readiness-response.json"
STATUS=PASS; MESSAGE="UAT contract, release commit and readiness endpoint passed"
