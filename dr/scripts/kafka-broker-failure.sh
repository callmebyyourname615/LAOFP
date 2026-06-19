#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
: "${KAFKA_STATEFULSET:?KAFKA_STATEFULSET is required}"
ns="${KAFKA_NAMESPACE:-$DR_NAMESPACE}"
original=$(kubectl -n "$ns" get statefulset "$KAFKA_STATEFULSET" -o jsonpath='{.spec.replicas}')
restored=0
restore() {
  if [[ "$restored" -eq 0 ]]; then
    kubectl -n "$ns" scale statefulset "$KAFKA_STATEFULSET" --replicas="$original" >/dev/null
    kubectl -n "$ns" rollout status statefulset "$KAFKA_STATEFULSET" --timeout="${DR_TIMEOUT:-600s}" >/dev/null
    record KAFKA_RECOVERED "$original replicas"
    restored=1
  fi
}
trap restore EXIT INT TERM
record KAFKA_FAILURE "$ns/$KAFKA_STATEFULSET replicas=$original->0"
kubectl -n "$ns" scale statefulset "$KAFKA_STATEFULSET" --replicas=0 >/dev/null
sleep "${KAFKA_OUTAGE_SECONDS:-60}"
restore
trap - EXIT INT TERM
