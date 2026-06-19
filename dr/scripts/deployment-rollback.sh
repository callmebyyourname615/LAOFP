#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"; require_dr_confirmation
: "${ROLLBACK_DIGEST:?ROLLBACK_DIGEST must be sha256:...}"
[[ "$ROLLBACK_DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]] || exit 2
image="${IMAGE_REPOSITORY:?IMAGE_REPOSITORY is required}@${ROLLBACK_DIGEST}"
current=$(kubectl -n "$DR_NAMESPACE" get deploy switching-api -o jsonpath='{.spec.template.spec.containers[0].image}')
record ROLLBACK_START "$current -> $image"
kubectl -n "$DR_NAMESPACE" set image deployment/switching-api switching-api="$image"
wait_deployment
actual=$(kubectl -n "$DR_NAMESPACE" get deploy switching-api -o jsonpath='{.spec.template.spec.containers[0].image}')
[[ "$actual" == "$image" ]] || { record ROLLBACK_FAILED "$actual"; exit 1; }
record ROLLBACK_COMPLETE "$actual"
