#!/usr/bin/env bash
set -euo pipefail

# Static pre-deploy checks for env files and Kubernetes/Vault manifests.
# It never prints secret values.
if [[ "$#" -eq 0 ]]; then
  echo "Usage: $0 <env-or-yaml-file> [more files...]" >&2
  exit 2
fi

required_env=(
  DB_URL DB_USERNAME DB_PASSWORD
  FLYWAY_URL FLYWAY_USERNAME FLYWAY_PASSWORD
  SPRING_KAFKA_BOOTSTRAP_SERVERS KAFKA_SECURITY_PROTOCOL KAFKA_SASL_MECHANISM KAFKA_SASL_JAAS_CONFIG
  ACCOUNT_LOOKUP_BASE_URL BOL_RTGS_URL RTGS_CALLBACK_IP_WHITELIST
  BOL_FIU_URL BOL_FIU_API_KEY AML_BOL_SANCTIONS_URL AML_BOL_SANCTIONS_API_KEY
  ARCHIVE_DB_URL ARCHIVE_DB_USERNAME ARCHIVE_DB_PASSWORD
  OBJECT_STORAGE_ENDPOINT OBJECT_STORAGE_BUCKET OBJECT_STORAGE_ACCESS_KEY OBJECT_STORAGE_SECRET_KEY
  PROMPTPAY_API_URL CNAPS_API_URL NAPAS_API_URL SWIFT_API_URL
  MESSAGE_CRYPTO_KEY_BASE64 OAUTH_JWT_SECRET VAULT_ADDR WEBHOOK_VAULT_AUTH_METHOD
)

failures=0
fail() { failures=$((failures + 1)); printf 'FAIL: %s\n' "$*" >&2; }
note() { printf '%s\n' "$*"; }

looks_like_env() { grep -Eq '^[A-Z0-9_]+=' "$1"; }
extract_env() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '
    $0 !~ /^[[:space:]]*#/ && $1 == k {
      sub(/^[^=]*=/, "", $0); gsub(/^["'\'' ]+|["'\'' ]+$/, "", $0); print; found=1; exit
    }
    END { if (!found) exit 1 }
  ' "$file"
}

contains_pattern() {
  local file="$1" pattern="$2" message="$3"
  if grep -Ein "$pattern" "$file" >/dev/null; then
    fail "$file $message"
  fi
}

for file in "$@"; do
  [[ -f "$file" ]] || { fail "$file does not exist"; continue; }
  note "Checking $file"

  contains_pattern "$file" 'REPLACE_ME|REPLACE_WITH|change_me|dev-test|dev-fiu-key|test-secret' \
    'contains placeholder/development values'
  contains_pattern "$file" 'KAFKA_SECURITY_PROTOCOL=["'\'' ]*PLAINTEXT|security\.protocol:["'\'' ]*PLAINTEXT' \
    'configures Kafka PLAINTEXT'

  # Committed Kubernetes Secret values are forbidden. Empty v1/List guard files are allowed.
  if grep -Eq '^[[:space:]]*kind:[[:space:]]*Secret[[:space:]]*$' "$file"; then
    fail "$file contains a raw Kubernetes Secret; use ExternalSecret"
  fi
  if looks_like_env "$file"; then
    for key in "${required_env[@]}"; do
      value="$(extract_env "$file" "$key" || true)"
      [[ -n "$value" ]] || fail "$file is missing or has blank $key"
    done

    db_url="$(extract_env "$file" DB_URL || true)"
    flyway_url="$(extract_env "$file" FLYWAY_URL || true)"
    archive_url="$(extract_env "$file" ARCHIVE_DB_URL || true)"
    for item in "DB_URL:$db_url" "FLYWAY_URL:$flyway_url" "ARCHIVE_DB_URL:$archive_url"; do
      key="${item%%:*}"; value="${item#*:}"
      [[ "$value" == *"sslmode=verify-full"* ]] || fail "$file $key must use sslmode=verify-full"
      [[ "$value" == *"sslrootcert="* ]] || fail "$file $key must set sslrootcert"
    done

    auth_method="$(extract_env "$file" WEBHOOK_VAULT_AUTH_METHOD || true)"
    [[ "$auth_method" == "kubernetes" ]] || fail "$file WEBHOOK_VAULT_AUTH_METHOD must be kubernetes"
    if extract_env "$file" VAULT_TOKEN >/dev/null 2>&1; then
      token="$(extract_env "$file" VAULT_TOKEN || true)"
      [[ -z "$token" ]] || fail "$file must not contain a static VAULT_TOKEN"
    fi

    kafka_protocol="$(extract_env "$file" KAFKA_SECURITY_PROTOCOL || true)"
    kafka_jaas="$(extract_env "$file" KAFKA_SASL_JAAS_CONFIG || true)"
    [[ "$kafka_protocol" == "SASL_SSL" || "$kafka_protocol" == "SSL" ]] \
      || fail "$file Kafka protocol must be SASL_SSL or SSL"
    if [[ "$kafka_protocol" == SASL_* && "$kafka_jaas" != *"username="* ]]; then
      fail "$file KAFKA_SASL_JAAS_CONFIG is incomplete"
    fi
  fi

  if grep -Eq '^[[:space:]]*kind:[[:space:]]*(ExternalSecret|SecretStore)[[:space:]]*$' "$file"; then
    grep -Eq '^apiVersion:[[:space:]]*external-secrets\.io/v1[[:space:]]*$' "$file" \
      || fail "$file must use external-secrets.io/v1"
  fi

done

if [[ "$failures" -gt 0 ]]; then
  echo "Production config check failed with $failures issue(s)." >&2
  exit 1
fi

echo "Production config check passed."
