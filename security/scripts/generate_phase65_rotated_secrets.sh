#!/usr/bin/env bash
set -Eeuo pipefail
out="${1:?Usage: generate_phase65_rotated_secrets.sh /secure/path/secrets.env}"
root="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"; abs="$(python3 -c 'import os,sys; print(os.path.abspath(sys.argv[1]))' "$out")"
case "$abs" in "$root"/*) echo 'Refusing to write generated secrets inside repository' >&2; exit 64;; esac
umask 077; mkdir -p "$(dirname "$abs")"; : > "$abs"
for name in POSTGRES_PASSWORD REPLICATION_PASSWORD DB_APP_PASSWORD FLYWAY_PASSWORD ARCHIVE_POSTGRES_PASSWORD MINIO_ROOT_PASSWORD; do
  value="$(openssl rand -base64 48 | tr -dc 'A-Za-z0-9' | head -c 32)"; [[ ${#value} -eq 32 ]] || exit 1; printf '%s=%s\n' "$name" "$value" >> "$abs"
done
chmod 600 "$abs"; printf 'Generated six credentials at %s (mode 600). Move them to Vault, then securely delete this file.\n' "$abs"
