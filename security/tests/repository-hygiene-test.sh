#!/usr/bin/env bash
set -Eeuo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCANNER="$SOURCE_ROOT/security/scripts/verify_repository_hygiene.py"
POLICY="$SOURCE_ROOT/security/policy/repository-hygiene.json"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

new_repo() {
  local dir="$1"
  mkdir -p "$dir/src/main/resources/db/migration"
  git -C "$dir" init -q
  git -C "$dir" config user.email phase53a@example.invalid
  git -C "$dir" config user.name phase53a-test
  printf 'DB_PASSWORD=\n' > "$dir/.env.example"
  printf '%s\n' 'CREATE TABLE sample(id bigint);' > "$dir/src/main/resources/db/migration/V1__sample.sql"
  git -C "$dir" add .
}

expect_pass() {
  local repo="$1"
  python3 "$SCANNER" --repo "$repo" --policy "$POLICY" >/dev/null
}
expect_fail() {
  local repo="$1"
  if python3 "$SCANNER" --repo "$repo" --policy "$POLICY" >/dev/null 2>&1; then
    echo "Expected hygiene scan to fail for $repo" >&2
    exit 1
  fi
}

new_repo "$TMP/pass"
expect_pass "$TMP/pass"

new_repo "$TMP/env-backup"
printf 'DB_PASSWORD=not-a-placeholder\n' > "$TMP/env-backup/.env.bak"
git -C "$TMP/env-backup" add .env.bak
expect_fail "$TMP/env-backup"

new_repo "$TMP/literal-secret"
printf 'DB_PASSWORD=literal-production-looking-secret\n' > "$TMP/literal-secret/.env.example"
git -C "$TMP/literal-secret" add .env.example
expect_fail "$TMP/literal-secret"

new_repo "$TMP/private-key"
printf '%s%s\n' '-----BEGIN ' 'PRIVATE KEY-----' > "$TMP/private-key/credential.txt"
git -C "$TMP/private-key" add credential.txt
expect_fail "$TMP/private-key"

new_repo "$TMP/sql-dump"
mkdir -p "$TMP/sql-dump/backups"
printf '%s\n' 'COPY accounts FROM stdin;' > "$TMP/sql-dump/backups/customer.sql"
git -C "$TMP/sql-dump" add backups/customer.sql
expect_fail "$TMP/sql-dump"

new_repo "$TMP/renamed-dump"
mkdir -p "$TMP/renamed-dump/scripts"
printf '%s\n' '-- PostgreSQL database dump' > "$TMP/renamed-dump/scripts/maintenance.sql"
git -C "$TMP/renamed-dump" add scripts/maintenance.sql
expect_fail "$TMP/renamed-dump"

echo "Repository hygiene tests passed."
