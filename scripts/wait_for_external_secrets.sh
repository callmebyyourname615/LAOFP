#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${SWITCHING_NAMESPACE:-switching}"
TIMEOUT="${EXTERNAL_SECRET_TIMEOUT:-180s}"
resources=(switching-application-secrets switching-migration-secrets switching-trust-bundle)

for resource in "${resources[@]}"; do
  kubectl -n "$NAMESPACE" wait \
    --for=condition=Ready \
    "externalsecret/$resource" \
    --timeout="$TIMEOUT"
done

for secret in switching-secrets switching-flyway-secrets switching-trust-bundle; do
  kubectl -n "$NAMESPACE" get secret "$secret" >/dev/null
  manager="$(kubectl -n "$NAMESPACE" get secret "$secret" -o jsonpath='{.metadata.labels.app\.kubernetes\.io/managed-by}')"
  if [[ "$manager" != "external-secrets" ]]; then
    echo "Secret $secret is not marked as managed by external-secrets" >&2
    exit 1
  fi
done

echo "External Secrets are Ready."
