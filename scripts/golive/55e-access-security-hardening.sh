#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
live_require_release_identity
PHASE_ID=55E; live_require_environment production; live_require_production_confirmation
require_phase_pass 55A 55B 55C 55D
[[ "${SECURITY_HARDENING_APPLY_CONFIRMATION:-}" == I_UNDERSTAND_THIS_APPLIES_PRODUCTION_SECURITY_CONTROLS ]] || live_die "security hardening apply confirmation missing"
: "${APPLICATION_IMAGE_REPOSITORY:?APPLICATION_IMAGE_REPOSITORY required}"; : "${MIGRATION_IMAGE_REPOSITORY:?MIGRATION_IMAGE_REPOSITORY required}"
: "${SECRET_ROTATION_ATTESTATION:?SECRET_ROTATION_ATTESTATION required}"; [[ -f "$SECRET_ROTATION_ATTESTATION" && ! -L "$SECRET_ROTATION_ATTESTATION" ]] || live_die "rotation attestation must be a regular non-symlink file"
: "${SECRET_ROTATION_ATTESTATION_SIGNATURE:?SECRET_ROTATION_ATTESTATION_SIGNATURE required}"; [[ -f "$SECRET_ROTATION_ATTESTATION_SIGNATURE" && ! -L "$SECRET_ROTATION_ATTESTATION_SIGNATURE" ]] || live_die "rotation attestation signature must be a regular non-symlink file"
: "${ATTESTATION_PUBLIC_KEY:?ATTESTATION_PUBLIC_KEY required}"
: "${PRODUCTION_DATABASE_CIDRS:?required}"; : "${PRODUCTION_KAFKA_CIDRS:?required}"; : "${PRODUCTION_VAULT_CIDRS:?required}"
: "${PRODUCTION_OBJECT_STORAGE_CIDRS:?required}"; : "${PRODUCTION_EXTERNAL_API_CIDRS:?required}"
namespace="${PRODUCTION_NAMESPACE:-switching}"; [[ "$namespace" == switching ]] || live_die "Phase 55 production security bundle currently requires namespace switching"
live_require_image_repository "$APPLICATION_IMAGE_REPOSITORY" application-image-repository
live_require_image_repository "$MIGRATION_IMAGE_REPOSITORY" migration-image-repository
live_require_command kubectl; live_require_command psql; live_require_command cosign
phase_begin 55E "Production Access and Security Hardening"
failed=0; mkdir -p "$PHASE_DIR/rendered"
run_check render-deployment scripts/render_k8s_image.sh k8s/deployment.yaml "$PHASE_DIR/rendered/deployment.yaml" "$APPLICATION_IMAGE_REPOSITORY" "$RELEASE_APP_IMAGE_DIGEST" || failed=1
run_check render-migration-job scripts/render_k8s_image.sh k8s/migration-job.yaml "$PHASE_DIR/rendered/migration-job.yaml" "$MIGRATION_IMAGE_REPOSITORY" "$RELEASE_MIGRATION_IMAGE_DIGEST" || failed=1
run_check render-network-policy python3 scripts/golive/render_production_network_policy.py \
  --database-cidrs "$PRODUCTION_DATABASE_CIDRS" --kafka-cidrs "$PRODUCTION_KAFKA_CIDRS" --vault-cidrs "$PRODUCTION_VAULT_CIDRS" \
  --object-storage-cidrs "$PRODUCTION_OBJECT_STORAGE_CIDRS" --external-api-cidrs "$PRODUCTION_EXTERNAL_API_CIDRS" \
  --namespace "$namespace" --name switching-api --app-label switching-api \
  --kafka-port "${PRODUCTION_KAFKA_PORT:-9093}" --output "$PHASE_DIR/rendered/networkpolicy.yaml" || failed=1
run_check server-dry-run-security-bundle kubectl apply -k k8s/production --dry-run=server -o yaml || failed=1
run_check server-dry-run-egress kubectl apply -f "$PHASE_DIR/rendered/networkpolicy.yaml" --dry-run=server -o yaml || failed=1
run_check secret-rotation-signature cosign verify-blob --key "$ATTESTATION_PUBLIC_KEY" --signature "$SECRET_ROTATION_ATTESTATION_SIGNATURE" "$SECRET_ROTATION_ATTESTATION" || failed=1
run_check secret-rotation-preflight python3 - "$SECRET_ROTATION_ATTESTATION" "$RELEASE_REFERENCE" "$RELEASE_RC_ID" "$RELEASE_GIT_COMMIT" <<'PYROT' || failed=1
import datetime as dt,json,pathlib,sys
path,reference,rc_id,commit=sys.argv[1:]; doc=json.loads(pathlib.Path(path).read_text()); errors=[]
if doc.get('releaseReference')!=reference or doc.get('releaseCandidateId')!=rc_id or doc.get('gitCommit')!=commit: errors.append('release identity mismatch')
if len({x for x in doc.get('approvedBy',[]) if x})<2: errors.append('two approvers required')
try:
    rotated=dt.datetime.fromisoformat(str(doc.get('rotatedAt','')).replace('Z','+00:00')); age=(dt.datetime.now(dt.timezone.utc)-rotated).total_seconds()
    if age<0 or age>90*86400: errors.append('rotation must be within 90 days')
except Exception: errors.append('rotatedAt invalid')
try:
    expires=dt.datetime.fromisoformat(str(doc.get('breakGlassAccessExpiresAt','')).replace('Z','+00:00')); hours=(expires-dt.datetime.now(dt.timezone.utc)).total_seconds()/3600
    if not 0<hours<=24: errors.append('break-glass expiry must be within 24 hours')
except Exception: errors.append('break-glass expiry invalid')
if errors: raise SystemExit('; '.join(errors))
PYROT
if (( failed )); then write_phase_result FAIL; exit 1; fi
cp "$SECRET_ROTATION_ATTESTATION" "$PHASE_DIR/secret-rotation-attestation.json"
cp "$SECRET_ROTATION_ATTESTATION_SIGNATURE" "$PHASE_DIR/secret-rotation-attestation.sig"
run_check apply-security-bundle kubectl apply --server-side -k k8s/production || failed=1
run_check apply-egress-policy kubectl apply --server-side -f "$PHASE_DIR/rendered/networkpolicy.yaml" || failed=1
if (( failed )); then write_phase_result FAIL; exit 1; fi
run_check database-grants env OUTPUT="$PHASE_DIR/database-grants.json" scripts/golive/collect_database_grants.sh || failed=1
run_check access-hardening-contract python3 scripts/golive/verify_access_hardening.py \
  --deployment "$PHASE_DIR/rendered/deployment.yaml" --migration-job "$PHASE_DIR/rendered/migration-job.yaml" \
  --network-policy "$PHASE_DIR/rendered/networkpolicy.yaml" --database-grants "$PHASE_DIR/database-grants.json" \
  --rotation-attestation "$PHASE_DIR/secret-rotation-attestation.json" --reference "$RELEASE_REFERENCE" --rc-id "$RELEASE_RC_ID" --git-commit "$RELEASE_GIT_COMMIT" --output "$PHASE_DIR/access-hardening.json" \
  --kubernetes-output "$PHASE_DIR/kubernetes-hardening.json" || failed=1
run_check runtime-rbac-check bash -c '
  ! kubectl auth can-i --as=system:serviceaccount:switching:switching-api create deployments -n switching
  ! kubectl auth can-i --as=system:serviceaccount:switching:switching-api get secrets -n switching
' || failed=1
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
