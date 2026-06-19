#!/usr/bin/env bash
set -Eeuo pipefail

ENV_FILE="${1:-.env}"
ROOT="$(git rev-parse --show-toplevel 2>/dev/null)" || {
  echo "Run inside the Switching Git repository." >&2
  exit 2
}
cd "$ROOT"

[[ -f "$ENV_FILE" ]] || { echo "$ENV_FILE does not exist." >&2; exit 1; }
if ! git check-ignore -q -- "$ENV_FILE"; then
  echo "$ENV_FILE is not ignored by Git." >&2
  exit 1
fi

mode="$(stat -c '%a' "$ENV_FILE" 2>/dev/null || stat -f '%Lp' "$ENV_FILE")"
case "$mode" in
  600|400) ;;
  *) echo "$ENV_FILE permissions must be 0600 or 0400; found $mode." >&2; exit 1 ;;
esac

python3 - "$ENV_FILE" <<'PY'
from __future__ import annotations

import base64
import binascii
import re
import sys
from pathlib import Path

path = Path(sys.argv[1])
required = (
    "POSTGRES_PASSWORD",
    "DB_APP_PASSWORD",
    "DB_PASSWORD",
    "FLYWAY_PASSWORD",
    "REPLICATION_PASSWORD",
    "ARCHIVE_POSTGRES_PASSWORD",
    "MINIO_ROOT_PASSWORD",
    "MESSAGE_CRYPTO_KEY_BASE64",
    "WEBHOOK_LOCAL_MASTER_KEY_BASE64",
)
unique_passwords = (
    "POSTGRES_PASSWORD",
    "DB_APP_PASSWORD",
    "FLYWAY_PASSWORD",
    "REPLICATION_PASSWORD",
    "ARCHIVE_POSTGRES_PASSWORD",
    "MINIO_ROOT_PASSWORD",
)
placeholder = re.compile(
    r"(?i)(change[_-]?me|replace[_-]?me|__inject|__required|example|placeholder|dev-test)"
)

values: dict[str, str] = {}
errors: list[str] = []
for number, raw in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
    line = raw.strip()
    if not line or line.startswith("#"):
        continue
    if "=" not in line:
        errors.append(f"line {number}: invalid KEY=VALUE syntax")
        continue
    key, value = line.split("=", 1)
    key = key.strip()
    if key in values:
        errors.append(f"line {number}: duplicate key {key}")
    values[key] = value.strip().strip("'\"")

for key in required:
    value = values.get(key, "")
    if not value:
        errors.append(f"missing or blank required key {key}")
    elif placeholder.search(value):
        errors.append(f"placeholder/default marker found in {key}")

for key in unique_passwords:
    value = values.get(key, "")
    if value and len(value) < 24:
        errors.append(f"{key} must be at least 24 characters")

seen: dict[str, str] = {}
for key in unique_passwords:
    value = values.get(key, "")
    if not value:
        continue
    previous = seen.get(value)
    if previous:
        errors.append(f"{key} reuses the same value as {previous}")
    else:
        seen[value] = key

if values.get("DB_PASSWORD") and values.get("DB_APP_PASSWORD"):
    if values["DB_PASSWORD"] != values["DB_APP_PASSWORD"]:
        errors.append("DB_PASSWORD must match DB_APP_PASSWORD for the generated local profile")

for key in ("MESSAGE_CRYPTO_KEY_BASE64", "WEBHOOK_LOCAL_MASTER_KEY_BASE64"):
    value = values.get(key, "")
    if not value:
        continue
    try:
        decoded = base64.b64decode(value, validate=True)
    except (binascii.Error, ValueError):
        errors.append(f"{key} must be valid Base64")
        continue
    if len(decoded) != 32:
        errors.append(f"{key} must decode to exactly 32 bytes")

if errors:
    print(f"Local environment validation FAILED with {len(errors)} issue(s):", file=sys.stderr)
    for error in errors:
        print(f"  - {error}", file=sys.stderr)
    print("No secret values were printed.", file=sys.stderr)
    raise SystemExit(1)

print(f"Local environment validation passed for {path}.")
PY
