#!/usr/bin/env bash
set -Eeuo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run this command inside the Switching Git repository." >&2
  exit 2
}

exec python3 "${ROOT}/security/scripts/verify_repository_hygiene.py" --repo "${ROOT}" "$@"
