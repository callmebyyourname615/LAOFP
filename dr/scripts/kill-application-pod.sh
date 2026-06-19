#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
pod=$(kubectl -n "$DR_NAMESPACE" get pods -l app=switching-api -o jsonpath='{.items[0].metadata.name}')
record APP_POD_KILL "$pod"; kubectl -n "$DR_NAMESPACE" delete pod "$pod" --wait=false
wait_deployment; record APP_POD_RECOVERED "deployment available"
