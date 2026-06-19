#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
: "${FAULT_CONFIGMAP:?FAULT_CONFIGMAP is required}"
key="${FAULT_CONFIG_KEY:-FAULT_MODE}"
[[ "$key" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || { echo 'Unsafe FAULT_CONFIG_KEY' >&2; exit 2; }
original=$(kubectl -n "$DR_NAMESPACE" get configmap "$FAULT_CONFIGMAP" -o "jsonpath={.data.${key}}")
restored=0
restore() {
  if [[ "$restored" -eq 0 ]]; then
    kubectl -n "$DR_NAMESPACE" patch configmap "$FAULT_CONFIGMAP" --type merge \
      -p "{\"data\":{\"$key\":\"$original\"}}" >/dev/null
    kubectl -n "$DR_NAMESPACE" rollout restart deployment/switching-api >/dev/null
    wait_deployment >/dev/null
    record EXTERNAL_TIMEOUT DISABLE
    restored=1
  fi
}
trap restore EXIT INT TERM
record EXTERNAL_TIMEOUT ENABLE
kubectl -n "$DR_NAMESPACE" patch configmap "$FAULT_CONFIGMAP" --type merge \
  -p "{\"data\":{\"$key\":\"timeout\"}}" >/dev/null
kubectl -n "$DR_NAMESPACE" rollout restart deployment/switching-api >/dev/null
wait_deployment
sleep "${EXTERNAL_TIMEOUT_SECONDS:-60}"
restore
trap - EXIT INT TERM
