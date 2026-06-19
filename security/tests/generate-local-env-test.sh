#!/usr/bin/env bash
set -Eeuo pipefail
SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "$TMP/repo/security/scripts"
cp "$SOURCE_ROOT/.env.example" "$TMP/repo/.env.example"
cp "$SOURCE_ROOT/security/scripts/generate-local-env.sh" "$SOURCE_ROOT/security/scripts/check-local-env.sh" "$TMP/repo/security/scripts/"
printf '.env\n' > "$TMP/repo/.gitignore"
git -C "$TMP/repo" init -q
git -C "$TMP/repo" config user.email phase53a@example.invalid
git -C "$TMP/repo" config user.name phase53a-test

cd "$TMP/repo"
security/scripts/generate-local-env.sh
[[ -s .env ]]
mode="$(stat -c '%a' .env 2>/dev/null || stat -f '%Lp' .env)"
[[ "$mode" == "600" ]] || { echo "Expected mode 600, found $mode" >&2; exit 1; }
for key in POSTGRES_PASSWORD DB_APP_PASSWORD DB_PASSWORD FLYWAY_PASSWORD REPLICATION_PASSWORD ARCHIVE_POSTGRES_PASSWORD MINIO_ROOT_PASSWORD MESSAGE_CRYPTO_KEY_BASE64 WEBHOOK_LOCAL_MASTER_KEY_BASE64; do
  value="$(awk -F= -v key="$key" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' .env)"
  [[ -n "$value" ]] || { echo "$key was not generated" >&2; exit 1; }
done
git check-ignore -q .env || { echo ".env must remain ignored" >&2; exit 1; }
security/scripts/check-local-env.sh .env

echo "Local environment generator test passed."
