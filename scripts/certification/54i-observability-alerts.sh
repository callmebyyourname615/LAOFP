#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54C 54H
: "${MANAGEMENT_BASE_URL:?MANAGEMENT_BASE_URL is required}"; [[ "$MANAGEMENT_BASE_URL" == https://* ]] || cert_die "MANAGEMENT_BASE_URL must use HTTPS"
phase_begin 54I "Observability & Alert Certification"
failed=0
mkdir -p "$PHASE_DIR/health"
curl_args=(--fail-with-body --silent --show-error)
if [[ -n "${MANAGEMENT_BEARER_TOKEN:-}" ]]; then curl_args+=(-H "Authorization: Bearer ${MANAGEMENT_BEARER_TOKEN}"); fi
for path in /actuator/health /actuator/health/liveness /actuator/health/readiness; do
  name="${path#/}"; name="${name//\//-}.json"
  run_check "health-${name%.json}" curl "${curl_args[@]}" "$MANAGEMENT_BASE_URL$path" -o "$PHASE_DIR/health/$name" || failed=1
done
run_check prometheus-metrics curl "${curl_args[@]}" "$MANAGEMENT_BASE_URL/actuator/prometheus" -o "$PHASE_DIR/prometheus.txt" || failed=1
run_check alert-runbooks python3 scripts/monitoring/verify_alert_runbooks.py || failed=1
export ALERT_DELIVERY_OUTPUT="$PHASE_DIR/alert-delivery-results.json"
run_check alert-delivery scripts/monitoring/run_alert_delivery_drill.sh || failed=1
run_check observability-contract python3 scripts/certification/verify_observability.py --health-dir "$PHASE_DIR/health" --metrics-file "$PHASE_DIR/prometheus.txt" --alert-delivery-file "$PHASE_DIR/alert-delivery-results.json" --output "$PHASE_DIR/observability-summary.json" || failed=1
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
