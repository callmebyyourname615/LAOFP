#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55G; live_require_environment production; live_require_production_confirmation
require_phase_pass 55A 55B 55C 55D 55E 55F
[[ "${PRODUCTION_CANARY_CONFIRMATION:-}" == I_UNDERSTAND_THIS_RUNS_THE_PRODUCTION_MIGRATION_AND_5_PERCENT_CANARY ]] || live_die "production canary confirmation missing"
: "${APPLICATION_IMAGE_REPOSITORY:?required}"; : "${MIGRATION_IMAGE_REPOSITORY:?required}"; : "${PROMETHEUS_URL:?required}"
: "${DB_URL:?required}"; : "${DB_USERNAME:?required}"; : "${DB_PASSWORD:?required}"
: "${PRODUCTION_SYNTHETIC_SCRIPT:?required}"; [[ -x "$PRODUCTION_SYNTHETIC_SCRIPT" ]] || live_die "synthetic script must be executable"
: "${CANARY_DECISION_FILE:?required}"; : "${CANARY_DECISION_SIGNATURE:?required}"; : "${DECISION_PUBLIC_KEY:?required}"
: "${PRODUCTION_DATABASE_CIDRS:?required}"; : "${PRODUCTION_KAFKA_CIDRS:?required}"; : "${PRODUCTION_VAULT_CIDRS:?required}"
: "${PRODUCTION_OBJECT_STORAGE_CIDRS:?required}"; : "${PRODUCTION_EXTERNAL_API_CIDRS:?required}"
: "${PRODUCTION_API_HOST:?required}"; : "${PRODUCTION_TLS_SECRET:?required}"; : "${PRODUCTION_CLIENT_CA_SECRET:?required}"
live_require_image_repository "$APPLICATION_IMAGE_REPOSITORY" application-image-repository
live_require_image_repository "$MIGRATION_IMAGE_REPOSITORY" migration-image-repository
live_require_command kubectl; live_require_command cosign; live_require_command psql
phase_begin 55G "Production Canary Deployment"
failed=0; namespace="${PRODUCTION_NAMESPACE:-switching}"; [[ "$namespace" =~ ^[a-z0-9]([-a-z0-9]*[a-z0-9])?$ ]] || live_die "invalid production namespace"; mkdir -p "$PHASE_DIR/rendered"
run_check stable-image-preflight bash -c '
  ns="$1"; out="$2"
  image=$(kubectl -n "$ns" get deployment switching-api -o jsonpath='"'"'{.spec.template.spec.containers[?(@.name=="switching-api")].image}'"'"')
  [[ "$image" =~ @sha256:[a-f0-9]{64}$ ]] || { echo "stable image is not digest-pinned" >&2; exit 1; }
  printf "%s\n" "$image" > "$out"
' _ "$namespace" "$PHASE_DIR/previous-stable-image.txt" || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
previous=$(cat "$PHASE_DIR/previous-stable-image.txt")
rollback(){ kubectl -n "$namespace" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight=0 --overwrite >/dev/null 2>&1 || true; kubectl -n "$namespace" scale deployment switching-api-canary --replicas=0 >/dev/null 2>&1 || true; }
trap 'rc=$?; if ((rc!=0)); then rollback; fi; exit $rc' EXIT INT TERM
run_check verify-canary-decision-signature cosign verify-blob --key "$DECISION_PUBLIC_KEY" --signature "$CANARY_DECISION_SIGNATURE" "$CANARY_DECISION_FILE" || failed=1
run_check verify-canary-decision python3 scripts/golive/verify_decision.py --decision "$CANARY_DECISION_FILE" --stage 5 \
  --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$RELEASE_APP_IMAGE_DIGEST" --evidence "$GOLIVE_ROOT/phases/55F/command-center-readiness.json" \
  --output "$PHASE_DIR/canary-decision-verification.json" || failed=1
run_check change-window-gate env DB_URL="${RELEASE_GATE_DB_URL:-$DB_URL}" ENVIRONMENT=production \
  CHANGE_TYPE="${CHANGE_TYPE:-STANDARD}" RELEASE_GATE_EVIDENCE_FILE="$PHASE_DIR/release-gate-evidence.json" \
  scripts/release/check_change_window.sh || failed=1
run_check render-migration scripts/render_k8s_image.sh k8s/migration-job.yaml "$PHASE_DIR/rendered/migration-job.yaml" "$MIGRATION_IMAGE_REPOSITORY" "$RELEASE_MIGRATION_IMAGE_DIGEST" || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
sed -i.bak "s/namespace: switching/namespace: $namespace/g" "$PHASE_DIR/rendered/migration-job.yaml"; rm -f "$PHASE_DIR/rendered/migration-job.yaml.bak"
run_check migration-job bash -c '
  ns="$1"; manifest="$2"; kubectl -n "$ns" delete job switching-db-migration --ignore-not-found --wait=true
  kubectl apply -f "$manifest"; kubectl -n "$ns" wait --for=condition=complete job/switching-db-migration --timeout=900s
  kubectl -n "$ns" logs job/switching-db-migration --all-containers=true
' _ "$namespace" "$PHASE_DIR/rendered/migration-job.yaml" || failed=1
kubectl -n "$namespace" get job switching-db-migration -o json > "$PHASE_DIR/migration-job.json" 2>/dev/null || echo '{}' > "$PHASE_DIR/migration-job.json"
run_check flyway-version-83 bash -c 'export PGPASSWORD="$DB_PASSWORD"; psql "${DB_URL#jdbc:}" -X -v ON_ERROR_STOP=1 -At -c "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1" | grep -qx 83' || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
run_check render-canary scripts/render_k8s_image.sh k8s/canary/deployment.yaml "$PHASE_DIR/rendered/canary-deployment.yaml" "$APPLICATION_IMAGE_REPOSITORY" "$RELEASE_APP_IMAGE_DIGEST" || failed=1
run_check render-canary-network-policy python3 scripts/golive/render_production_network_policy.py \
  --database-cidrs "$PRODUCTION_DATABASE_CIDRS" --kafka-cidrs "$PRODUCTION_KAFKA_CIDRS" --vault-cidrs "$PRODUCTION_VAULT_CIDRS" \
  --object-storage-cidrs "$PRODUCTION_OBJECT_STORAGE_CIDRS" --external-api-cidrs "$PRODUCTION_EXTERNAL_API_CIDRS" \
  --namespace "$namespace" --name switching-api-canary --app-label switching-api-canary --track-label canary \
  --kafka-port "${PRODUCTION_KAFKA_PORT:-9093}" --output "$PHASE_DIR/rendered/networkpolicy.yaml" || failed=1
run_check render-canary-ingress python3 scripts/golive/render_canary_ingress.py \
  --namespace "$namespace" --host "$PRODUCTION_API_HOST" --tls-secret "$PRODUCTION_TLS_SECRET" \
  --client-ca-secret "$PRODUCTION_CLIENT_CA_SECRET" --ingress-class "${PRODUCTION_INGRESS_CLASS:-nginx}" \
  --output "$PHASE_DIR/rendered/ingress.yaml" || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
for item in service servicemonitor; do
  sed -e "s/namespace: switching/namespace: $namespace/g" -e "s/matchNames: \[switching\]/matchNames: [$namespace]/g" "k8s/canary/$item.yaml" > "$PHASE_DIR/rendered/$item.yaml"
done
sed -i.bak "s/namespace: switching/namespace: $namespace/g" "$PHASE_DIR/rendered/canary-deployment.yaml"; rm -f "$PHASE_DIR/rendered/canary-deployment.yaml.bak"
run_check deploy-canary-resources bash -c 'for f in "$1"/service.yaml "$1"/ingress.yaml "$1"/servicemonitor.yaml "$1"/networkpolicy.yaml "$1"/canary-deployment.yaml; do kubectl apply -f "$f"; done' _ "$PHASE_DIR/rendered" || failed=1
CANARY_REPLICAS="${CANARY_REPLICAS:-1}"; [[ "$CANARY_REPLICAS" =~ ^[1-9][0-9]*$ ]] || live_die "CANARY_REPLICAS must be a positive integer"
run_check canary-scale kubectl -n "$namespace" scale deployment switching-api-canary --replicas="$CANARY_REPLICAS" || failed=1
run_check canary-ready kubectl -n "$namespace" rollout status deployment/switching-api-canary --timeout=600s || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
run_check canary-weight-5 kubectl -n "$namespace" annotate ingress switching-api-canary nginx.ingress.kubernetes.io/canary-weight=5 --overwrite || failed=1
run_check canary-observation sleep "${CANARY_5_OBSERVATION_SECONDS:-900}" || failed=1
run_check stage-metrics-gate python3 scripts/golive/verify_stage_metrics.py \
  --prometheus-url "$PROMETHEUS_URL" --track canary --stage 5 \
  --output "$PHASE_DIR/stage-metrics.json" || failed=1
run_check production-synthetic "$PRODUCTION_SYNTHETIC_SCRIPT" || failed=1
run_check capture-canary-reconciliation python3 scripts/golive/capture_reconciliation.py --output "$PHASE_DIR/current-reconciliation.json" --label production-canary-5 --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" || failed=1
run_check reconcile-canary python3 scripts/golive/compare_reconciliation.py --baseline "$GOLIVE_ROOT/phases/55D/cutover-baseline.json" --current "$PHASE_DIR/current-reconciliation.json" --output "$PHASE_DIR/reconciliation.json" || failed=1
python3 - "$PHASE_DIR/canary-5-percent.json" "$previous" "$failed" "$APPLICATION_IMAGE_REPOSITORY@$RELEASE_APP_IMAGE_DIGEST" <<'PY'
import json,pathlib,sys
out,previous,failed,candidate=sys.argv[1:]
pathlib.Path(out).write_text(json.dumps({'schemaVersion':1,'weightPercent':5,'candidateImage':candidate,'previousStableImage':previous,'migrationVersion':'83','status':'PASS' if failed=='0' else 'FAIL'},indent=2,sort_keys=True)+'\n')
PY
if (( failed )); then write_phase_result FAIL; exit 1; fi
write_phase_result PASS
trap - EXIT INT TERM
