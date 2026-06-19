#!/usr/bin/env bash
set -Eeuo pipefail

: "${IMAGE_REPOSITORY:?IMAGE_REPOSITORY required}"
: "${IMAGE_DIGEST:?IMAGE_DIGEST required}"
: "${PROMETHEUS_URL:?PROMETHEUS_URL required}"
NAMESPACE="${NAMESPACE:-switching}"
STAGE_SECONDS="${CANARY_STAGE_SECONDS:-300}"
CANARY_REPLICAS="${CANARY_REPLICAS:-2}"
WEIGHTS="${CANARY_WEIGHTS:-5 25 50}"

[[ "${IMAGE_DIGEST}" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo "invalid IMAGE_DIGEST" >&2; exit 64; }
mkdir -p build/canary
./scripts/render_k8s_image.sh k8s/canary/deployment.yaml build/canary/deployment.yaml "${IMAGE_REPOSITORY}" "${IMAGE_DIGEST}"
for manifest in deployment service ingress servicemonitor; do
  source="k8s/canary/${manifest}.yaml"
  [[ "$manifest" == deployment ]] && source="build/canary/deployment.yaml"
  sed "s/namespace: switching/namespace: ${NAMESPACE}/g" "$source" > "build/canary/${manifest}.rendered.yaml"
done

rollback() {
  kubectl -n "${NAMESPACE}" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight="0" --overwrite >/dev/null 2>&1 || true
  kubectl -n "${NAMESPACE}" scale deployment switching-api-canary --replicas=0 >/dev/null 2>&1 || true
}
trap 'rc=$?; if (( rc != 0 )); then rollback; fi; exit $rc' EXIT INT TERM

kubectl apply -f build/canary/service.rendered.yaml
kubectl apply -f build/canary/ingress.rendered.yaml
kubectl apply -f build/canary/servicemonitor.rendered.yaml
kubectl apply -f build/canary/deployment.rendered.yaml
kubectl -n "${NAMESPACE}" scale deployment switching-api-canary --replicas="${CANARY_REPLICAS}"
kubectl -n "${NAMESPACE}" rollout status deployment/switching-api-canary --timeout=600s

for weight in ${WEIGHTS}; do
  [[ "${weight}" =~ ^([1-9]|[1-9][0-9]|100)$ ]] || { echo "invalid canary weight ${weight}" >&2; exit 64; }
  kubectl -n "${NAMESPACE}" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight="${weight}" --overwrite
  sleep "${STAGE_SECONDS}"
  python3 scripts/release/prometheus-gate.py --prometheus-url "${PROMETHEUS_URL}"
done

stable_previous="$(kubectl -n "${NAMESPACE}" get deployment switching-api -o jsonpath='{.spec.template.spec.containers[?(@.name=="switching-api")].image}')"
new_image="${IMAGE_REPOSITORY}@${IMAGE_DIGEST}"
kubectl -n "${NAMESPACE}" set image deployment/switching-api switching-api="${new_image}"
if ! kubectl -n "${NAMESPACE}" rollout status deployment/switching-api --timeout=600s; then
  kubectl -n "${NAMESPACE}" set image deployment/switching-api switching-api="${stable_previous}" || true
  kubectl -n "${NAMESPACE}" rollout status deployment/switching-api --timeout=600s || true
  exit 1
fi
rollback
trap - EXIT INT TERM
printf 'promoted %s; previous stable was %s\n' "${new_image}" "${stable_previous}"
cat > build/canary/summary.json <<EOF
{"namespace":"${NAMESPACE}","image":"${new_image}","previousImage":"${stable_previous}","weights":"${WEIGHTS} 100","status":"PASS"}
EOF
