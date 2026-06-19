#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run inside the Switching Git repository." >&2
  exit 2
}
chmod +x "$ROOT/.githooks/pre-commit"
git -C "$ROOT" config core.hooksPath .githooks
echo "Installed repository-managed hooks from .githooks/."
