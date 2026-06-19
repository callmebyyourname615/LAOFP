#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54H
[[ "$CERTIFICATION_ENVIRONMENT" == uat ]] || cert_die "54C must run in UAT"
[[ "${UAT_REHEARSAL_CONFIRMATION:-}" == I_UNDERSTAND_THIS_DEPLOYS_AND_ROLLS_BACK_UAT ]] || cert_die "invalid UAT_REHEARSAL_CONFIRMATION"
: "${RELEASE_IMAGE_REPOSITORY:?RELEASE_IMAGE_REPOSITORY is required}"
cert_require_command kubectl
NAMESPACE="${UAT_NAMESPACE:-switching-uat}"
DEPLOYMENT="${UAT_DEPLOYMENT:-switching-api}"
CONTAINER="${UAT_CONTAINER:-switching-api}"
IMAGE_REF="${RELEASE_IMAGE_REPOSITORY}@${RELEASE_IMAGE_DIGEST}"
phase_begin 54C "UAT Deployment Rehearsal"
failed=0
run_check cluster-context bash -lc '
  context=$(kubectl config current-context); case "$context" in *prod*|*production*) echo "production context prohibited: $context" >&2; exit 64;; esac
  kubectl get namespace "$0"' "$NAMESPACE" || failed=1
previous_image="$(kubectl -n "$NAMESPACE" get deployment "$DEPLOYMENT" -o jsonpath="{.spec.template.spec.containers[?(@.name==\"$CONTAINER\")].image}" 2>/dev/null || true)"
[[ -n "$previous_image" ]] || cert_die "cannot determine previous UAT image"
printf '%s\n' "$previous_image" > "$PHASE_DIR/previous-image.txt"
mkdir -p "$PHASE_DIR/rendered"
run_check render-manifests scripts/render_k8s_image.sh k8s/migration-job.yaml "$PHASE_DIR/rendered/migration-job.yaml" "$RELEASE_IMAGE_REPOSITORY" "$RELEASE_IMAGE_DIGEST" || failed=1
run_check render-uat-namespace sed -i.bak "s/namespace: switching/namespace: $NAMESPACE/g" "$PHASE_DIR/rendered/migration-job.yaml" || failed=1
rm -f "$PHASE_DIR/rendered/migration-job.yaml.bak"
run_check migration-job bash -lc '
  ns="$0"; manifest="$1"; kubectl -n "$ns" delete job switching-db-migration --ignore-not-found --wait=true;
  kubectl -n "$ns" apply -f "$manifest"; kubectl -n "$ns" wait --for=condition=complete job/switching-db-migration --timeout=300s;
  kubectl -n "$ns" logs job/switching-db-migration --all-containers=true' "$NAMESPACE" "$PHASE_DIR/rendered/migration-job.yaml" || failed=1
run_check deploy-candidate kubectl -n "$NAMESPACE" set image "deployment/$DEPLOYMENT" "$CONTAINER=$IMAGE_REF" || failed=1
run_check candidate-rollout kubectl -n "$NAMESPACE" rollout status "deployment/$DEPLOYMENT" --timeout=600s || failed=1
run_check candidate-readiness bash -lc '
  ns="$1"; dep="$2"; container="$3"; expected="$4";
  actual=$(kubectl -n "$ns" get deployment "$dep" -o "jsonpath={.spec.template.spec.containers[?(@.name==\"$container\")].image}");
  test "$actual" = "$expected";
  kubectl -n "$ns" wait --for=condition=Ready pod -l app=switching-api --timeout=300s' _ "$NAMESPACE" "$DEPLOYMENT" "$CONTAINER" "$IMAGE_REF" || failed=1
kubectl -n "$NAMESPACE" get deployment "$DEPLOYMENT" -o json > "$PHASE_DIR/deployment-state.json" || true
run_check rollback-rehearsal bash -lc '
  ns="$1"; dep="$2"; container="$3"; previous="$4";
  kubectl -n "$ns" set image "deployment/$dep" "$container=$previous";
  kubectl -n "$ns" rollout status "deployment/$dep" --timeout=600s;
  actual=$(kubectl -n "$ns" get deployment "$dep" -o "jsonpath={.spec.template.spec.containers[?(@.name==\"$container\")].image}");
  test "$actual" = "$previous"' _ "$NAMESPACE" "$DEPLOYMENT" "$CONTAINER" "$previous_image" || failed=1
kubectl -n "$NAMESPACE" get deployment "$DEPLOYMENT" -o json > "$PHASE_DIR/rollback-state.json" || true
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
