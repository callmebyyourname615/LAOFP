#!/usr/bin/env bash
set -Eeuo pipefail

log() {
  printf '%s level=%s component=switching-backup message=%q\n' \
    "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "${1:-INFO}" "${*:2}" >&2
}

die() {
  log ERROR "$*"
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_env() {
  local name
  for name in "$@"; do
    [[ -n "${!name:-}" ]] || die "required environment variable is empty: ${name}"
  done
}

require_file() {
  [[ -r "$1" ]] || die "required readable file not found: $1"
}

is_true() {
  [[ "${1,,}" == "true" || "$1" == "1" || "${1,,}" == "yes" ]]
}

validate_safe_token() {
  local value="$1" label="$2"
  [[ "$value" =~ ^[A-Za-z0-9._/-]+$ ]] || die "${label} contains unsupported characters"
}

validate_wal_name() {
  local value="$1"
  [[ "$value" =~ ^[0-9A-F]{24}$ || "$value" =~ ^[0-9A-F]{8}\.history$ || "$value" =~ ^[0-9A-F]{24}\.[0-9A-F]{8}\.backup$ ]]
}

atomic_write() {
  local destination="$1"
  local temporary="${destination}.tmp.$$"
  cat >"${temporary}"
  chmod 0600 "${temporary}"
  mv -f "${temporary}" "${destination}"
}

cleanup_dir() {
  local path="$1"
  [[ -n "$path" && "$path" != "/" && -d "$path" ]] || return 0
  rm -rf -- "$path"
}

postgres_connection_uri() {
  local database="${1:-${PGDATABASE:-postgres}}"
  printf 'host=%s port=%s dbname=%s user=%s sslmode=%s' \
    "$PGHOST" "${PGPORT:-5432}" "$database" "$PGUSER" "${PGSSLMODE:-verify-full}"
  if [[ -n "${PGSSLROOTCERT:-}" ]]; then
    printf ' sslrootcert=%s' "$PGSSLROOTCERT"
  fi
  if [[ -n "${PGSSLCERT:-}" ]]; then
    printf ' sslcert=%s' "$PGSSLCERT"
  fi
  if [[ -n "${PGSSLKEY:-}" ]]; then
    printf ' sslkey=%s' "$PGSSLKEY"
  fi
}

json_escape() {
  jq -Rn --arg value "$1" '$value'
}
