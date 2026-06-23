#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
out="${1:-}"
[[ -n "$out" ]] || { echo 'Usage: generate_rotated_secrets.sh /absolute/path/out.env' >&2; exit 64; }
case "$out" in /*) ;; *) echo 'Output path must be absolute and outside the repository' >&2; exit 64;; esac
repo="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
case "$out" in "$repo"/*) echo 'Refusing to write secrets inside repository' >&2; exit 64;; esac
mkdir -p "$(dirname "$out")"
: > "$out"
for key in POSTGRES_PASSWORD REPLICATION_PASSWORD DB_APP_PASSWORD FLYWAY_PASSWORD ARCHIVE_POSTGRES_PASSWORD MINIO_ROOT_PASSWORD; do
  value="$(openssl rand -base64 48 | tr -d '/+=' | head -c 40)"
  printf '%s=%s\n' "$key" "$value" >> "$out"
done
chmod 600 "$out"
echo "Generated six credentials at a protected path; values were not printed."
