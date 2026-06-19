#!/usr/bin/env bash
set -Eeuo pipefail

usage() {
  cat <<'USAGE'
Usage: security/scripts/generate-local-env.sh [--output FILE] [--force]

Creates a mode-0600 local .env with cryptographically random development-only
secrets. Secret values are generated inside one Python process and never printed
or passed as command-line arguments. The file remains ignored by Git and Docker.
USAGE
}

OUTPUT=".env"
FORCE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) [[ $# -ge 2 ]] || exit 64; OUTPUT="$2"; shift 2 ;;
    --force) FORCE=true; shift ;;
    --help|-h) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 64 ;;
  esac
done

ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run inside the Switching Git repository." >&2
  exit 2
}
cd "$ROOT"
[[ -f .env.example ]] || { echo "Missing .env.example" >&2; exit 2; }
if [[ -e "$OUTPUT" && "$FORCE" != true ]]; then
  echo "$OUTPUT already exists. Use --force to replace it." >&2
  exit 1
fi

umask 077
python3 - "$OUTPUT" <<'PY'
from __future__ import annotations

import base64
import os
import secrets
import sys
from pathlib import Path

output = Path(sys.argv[1])
values = {
    "POSTGRES_PASSWORD": secrets.token_hex(24),
    "DB_APP_PASSWORD": secrets.token_hex(24),
    "FLYWAY_PASSWORD": secrets.token_hex(24),
    "REPLICATION_PASSWORD": secrets.token_hex(24),
    "ARCHIVE_POSTGRES_PASSWORD": secrets.token_hex(24),
    "MINIO_ROOT_PASSWORD": secrets.token_urlsafe(30),
    "MESSAGE_CRYPTO_KEY_BASE64": base64.b64encode(secrets.token_bytes(32)).decode("ascii"),
    "WEBHOOK_LOCAL_MASTER_KEY_BASE64": base64.b64encode(secrets.token_bytes(32)).decode("ascii"),
}
values["DB_PASSWORD"] = values["DB_APP_PASSWORD"]

lines = Path(".env.example").read_text(encoding="utf-8").splitlines()
rendered: list[str] = []
seen: set[str] = set()
for line in lines:
    if "=" in line and not line.lstrip().startswith("#"):
        key = line.split("=", 1)[0]
        if key in values:
            line = f"{key}={values[key]}"
            seen.add(key)
    rendered.append(line)
for key in sorted(values.keys() - seen):
    rendered.append(f"{key}={values[key]}")

flags = os.O_WRONLY | os.O_CREAT | os.O_TRUNC
fd = os.open(output, flags, 0o600)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as handle:
        handle.write("\n".join(rendered) + "\n")
finally:
    try:
        os.chmod(output, 0o600)
    except OSError:
        pass
PY

echo "Generated $OUTPUT with mode 0600. Secret values were not printed."
echo "This file is local development material only; never commit or attach it."
