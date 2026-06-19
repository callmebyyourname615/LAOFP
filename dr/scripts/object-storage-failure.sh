#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
: "${OBJECT_STORAGE_WORKLOAD:?OBJECT_STORAGE_WORKLOAD is required}"
ns="${OBJECT_STORAGE_NAMESPACE:-$DR_NAMESPACE}"
kind="${OBJECT_STORAGE_WORKLOAD_KIND:-deployment}"
case "$kind" in deployment|statefulset) ;; *) echo 'OBJECT_STORAGE_WORKLOAD_KIND must be deployment or statefulset' >&2; exit 2;; esac
original=$(kubectl -n "$ns" get "$kind" "$OBJECT_STORAGE_WORKLOAD" -o jsonpath='{.spec.replicas}')
restored=0
restore() {
  if [[ "$restored" -eq 0 ]]; then
    kubectl -n "$ns" scale "$kind" "$OBJECT_STORAGE_WORKLOAD" --replicas="$original" >/dev/null
    kubectl -n "$ns" rollout status "$kind/$OBJECT_STORAGE_WORKLOAD" --timeout="${DR_TIMEOUT:-600s}" >/dev/null
    record OBJECT_STORAGE_RECOVERED "$original replicas"
    restored=1
  fi
}
trap restore EXIT INT TERM
record OBJECT_STORAGE_FAILURE "$ns/$kind/$OBJECT_STORAGE_WORKLOAD replicas=$original->0"
kubectl -n "$ns" scale "$kind" "$OBJECT_STORAGE_WORKLOAD" --replicas=0 >/dev/null
sleep "${OBJECT_STORAGE_OUTAGE_SECONDS:-60}"
restore
trap - EXIT INT TERM
