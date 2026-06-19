#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${SWITCHING_NAMESPACE:-switching}"

kubectl apply -f k8s/external-secrets/vault-secret-store.yaml
kubectl apply -f k8s/external-secrets/application-secrets.yaml
kubectl apply -f k8s/external-secrets/migration-secrets.yaml
kubectl apply -f k8s/external-secrets/trust-bundle.yaml

./scripts/wait_for_external_secrets.sh

# No delete is issued until all replacement Secrets are Ready and ownership labels verified.
# Re-applying the ExternalSecrets reconciles legacy same-name Secrets under ESO ownership.
kubectl -n "$NAMESPACE" annotate externalsecret switching-application-secrets \
  switching.example.com/migrated-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" --overwrite
kubectl -n "$NAMESPACE" annotate externalsecret switching-migration-secrets \
  switching.example.com/migrated-at="$(date -u +%Y-%m-%dT%H:%M:%SZ)" --overwrite

echo "Migration to External Secrets completed. Keep the legacy source disabled; do not apply k8s/secret.yaml from older releases."
