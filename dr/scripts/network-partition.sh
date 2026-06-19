#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
command -v jq >/dev/null || { echo 'jq is required' >&2; exit 2; }
manifest="$(dirname "$0")/../manifests/deny-external-egress.yaml"
original="$EVIDENCE_DIR/networkpolicy-switching-api-before.json"
kubectl -n "$DR_NAMESPACE" get networkpolicy switching-api -o json \
  | jq 'del(.metadata.resourceVersion,.metadata.uid,.metadata.creationTimestamp,.metadata.generation,.metadata.managedFields,.status)' > "$original"
restored=0
restore() {
  if [[ "$restored" -eq 0 ]]; then
    kubectl apply -f "$original" >/dev/null
    record NETWORK_PARTITION RESTORED
    restored=1
  fi
}
trap restore EXIT
record NETWORK_PARTITION APPLY
kubectl apply -f "$manifest" >/dev/null
sleep "${NETWORK_PARTITION_SECONDS:-60}"
restore; trap - EXIT
wait_deployment
