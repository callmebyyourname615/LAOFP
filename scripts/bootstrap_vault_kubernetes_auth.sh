#!/usr/bin/env bash
set -euo pipefail

: "${VAULT_ADDR:?Set VAULT_ADDR}"
: "${K8S_API_URL:?Set K8S_API_URL, for example https://kubernetes.default.svc:443}"
: "${K8S_CA_CERT_FILE:?Set K8S_CA_CERT_FILE}"
: "${VAULT_TOKEN_REVIEWER_JWT_FILE:?Set VAULT_TOKEN_REVIEWER_JWT_FILE}"

AUTH_MOUNT="${VAULT_K8S_AUTH_MOUNT:-kubernetes/REPLACE_WITH_CLUSTER_NAME}"
NAMESPACE="${SWITCHING_NAMESPACE:-switching}"

vault auth enable -path="$AUTH_MOUNT" kubernetes 2>/dev/null || true
vault write "auth/$AUTH_MOUNT/config" \
  token_reviewer_jwt="@${VAULT_TOKEN_REVIEWER_JWT_FILE}" \
  kubernetes_host="$K8S_API_URL" \
  kubernetes_ca_cert="@${K8S_CA_CERT_FILE}" \
  disable_local_ca_jwt=true

vault policy write switching-external-secrets \
  k8s/external-secrets/switching-external-secrets-policy.hcl
vault policy write switching-webhook-transit \
  k8s/external-secrets/switching-webhook-transit-policy.hcl

# Vault 1.21+ requires matching service-account token audience.
vault write "auth/$AUTH_MOUNT/role/switching-external-secrets" \
  bound_service_account_names=switching-vault-auth \
  bound_service_account_namespaces="$NAMESPACE" \
  audience=vault \
  policies=switching-external-secrets \
  token_ttl=10m \
  token_max_ttl=30m

vault write "auth/$AUTH_MOUNT/role/switching-webhook" \
  bound_service_account_names=switching-api \
  bound_service_account_namespaces="$NAMESPACE" \
  audience=vault \
  policies=switching-webhook-transit \
  token_ttl=10m \
  token_max_ttl=30m

printf 'Vault Kubernetes auth configured at %s\n' "$AUTH_MOUNT"
