#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"

scenario="${1:?scenario is required}"
template="${2:?manifest template is required}"
phase_dir="${PHASE73_PHASE_DIR:?PHASE73_PHASE_DIR is required}"
phase73_require_execution_approval
phase73_require_command kubectl
phase73_require_command curl
phase73_require_command psql
phase73_require_command python3
phase73_require_command timeout
phase73_require_file "$template"
python3 "$PHASE73_SCRIPT_DIR/validate_approval.py" --approval "$CHAOS_APPROVAL_FILE" --token "$CHAOS_APPROVAL_TOKEN" --scenario "$scenario" --maximum-age-minutes "$PHASE73_APPROVAL_MAX_AGE_MINUTES" --required-scenarios-json "$PHASE73_REQUIRED_SCENARIOS_JSON" >/dev/null

case "$scenario" in
  database-network-loss) phase73_require_nonempty_json_array PHASE73_DATABASE_CIDRS_JSON "$PHASE73_DATABASE_CIDRS_JSON" ;;
  kafka-network-delay) phase73_require_nonempty_json_array PHASE73_KAFKA_CIDRS_JSON "$PHASE73_KAFKA_CIDRS_JSON" ;;
  object-storage-network-loss) phase73_require_nonempty_json_array PHASE73_OBJECT_STORAGE_CIDRS_JSON "$PHASE73_OBJECT_STORAGE_CIDRS_JSON" ;;
  external-api-delay) phase73_require_nonempty_json_array PHASE73_EXTERNAL_API_CIDRS_JSON "$PHASE73_EXTERNAL_API_CIDRS_JSON" ;;
esac

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"
financial_command="${PHASE73_FINANCIAL_INTEGRITY_COMMAND:-}"
[[ -n "$financial_command" && -x "$financial_command" ]] || { echo "PHASE73_FINANCIAL_INTEGRITY_COMMAND must be an executable" >&2; exit 66; }

scenario_dir="$phase_dir/scenarios/$scenario"
mkdir -p "$scenario_dir"
rendered="$scenario_dir/experiment.yaml"
started_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
start_epoch="$(date +%s)"
cleanup_status="FAIL"
applied=false
cleanup() {
  local rc=$?
  if [[ "$applied" == "true" ]]; then
    if timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" kubectl delete -f "$rendered" --ignore-not-found=true --wait=true --timeout="${PHASE73_CLEANUP_TIMEOUT_SECONDS}s" >>"$scenario_dir/cleanup.log" 2>&1; then
      cleanup_status="PASS"
    fi
  else
    cleanup_status="PASS"
  fi
  printf '%s\n' "$cleanup_status" > "$scenario_dir/cleanup-status.txt"
  return "$rc"
}
trap cleanup EXIT

export EVIDENCE_DIR="$scenario_dir"
export DR_ENVIRONMENT=uat
export DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export DR_NAMESPACE="$PHASE73_NAMESPACE"
export BASE_URL="${BASE_URL:-$PHASE73_BASE_URL}"
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" "$PHASE73_ROOT/dr/scripts/capture-baseline.sh" >"$scenario_dir/baseline-command.log" 2>&1
timeout "${PHASE73_RENDER_TIMEOUT_SECONDS:-30}s" python3 "$PHASE73_SCRIPT_DIR/render_manifest.py" --template "$template" --output "$rendered" >"$scenario_dir/render.json"
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" kubectl apply -f "$rendered" >"$scenario_dir/apply.log" 2>&1
applied=true
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" kubectl get -f "$rendered" -o yaml >"$scenario_dir/applied-resource.yaml" 2>&1
sleep "$PHASE73_EXPERIMENT_DURATION"
sleep "$PHASE73_STABILIZATION_SECONDS"
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" kubectl delete -f "$rendered" --ignore-not-found=true --wait=true --timeout="${PHASE73_CLEANUP_TIMEOUT_SECONDS}s" >"$scenario_dir/cleanup.log" 2>&1
cleanup_status="PASS"
applied=false
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" kubectl -n "$PHASE73_NAMESPACE" rollout status "deployment/$PHASE73_DEPLOYMENT_NAME" --timeout="${PHASE73_CLEANUP_TIMEOUT_SECONDS}s" >"$scenario_dir/rollout.log" 2>&1
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" "$PHASE73_ROOT/dr/scripts/verify-recovery.sh" >"$scenario_dir/recovery-command.log" 2>&1
financial="$scenario_dir/financial-integrity.json"
timeout "${PHASE73_COMMAND_TIMEOUT_SECONDS}s" "$financial_command" --output "$financial" >"$scenario_dir/financial-integrity.log" 2>&1
finished_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
recovery_seconds="$(( $(date +%s) - start_epoch ))"
python3 "$PHASE73_SCRIPT_DIR/collect_scenario_attestation.py" \
  --scenario "$scenario" --run-id "$PHASE73_RUN_ID" --evidence-dir "$scenario_dir" \
  --started-at "$started_at" --finished-at "$finished_at" --recovery-seconds "$recovery_seconds" \
  --cleanup-status "$cleanup_status" --financial-integrity "$financial" --output "$scenario_dir/attestation.json"
