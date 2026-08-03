#!/usr/bin/env python3
"""Redact bearer tokens and remove private keys from runtime evidence."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

JWT = re.compile(r"\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b")
PRIVATE_KEY = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----",
    re.DOTALL,
)
PRIVATE_KEY_HEADER = re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----")
SENSITIVE_KEYS = {
    "accesstoken", "refreshtoken", "mfatoken", "authorization", "bearertoken",
    "clientsecret", "privatekey", "password", "token",
}
TEXT_SUFFIXES = {".txt", ".log", ".json", ".jsonl", ".xml", ".yaml", ".yml", ".md", ".csv", ".properties", ".key"}


def redact_text(value: str) -> tuple[str, int]:
    redacted, jwt_count = JWT.subn("[REDACTED_JWT]", value)
    redacted, key_count = PRIVATE_KEY.subn("[REDACTED_PRIVATE_KEY]", redacted)
    redacted, header_count = PRIVATE_KEY_HEADER.subn("[REDACTED_PRIVATE_KEY_HEADER]", redacted)
    return redacted, jwt_count + key_count + header_count


def redact_json(value: object) -> tuple[object, int]:
    if isinstance(value, dict):
        changes = 0
        redacted: dict[object, object] = {}
        for key, item in value.items():
            normalized = str(key).replace("_", "").replace("-", "").lower()
            if normalized in SENSITIVE_KEYS and item not in (None, "", "[REDACTED]"):
                redacted[key] = "[REDACTED]"
                changes += 1
            else:
                redacted_item, item_changes = redact_json(item)
                redacted[key] = redacted_item
                changes += item_changes
        return redacted, changes
    if isinstance(value, list):
        result, changes = [], 0
        for item in value:
            redacted_item, item_changes = redact_json(item)
            result.append(redacted_item)
            changes += item_changes
        return result, changes
    if isinstance(value, str):
        return redact_text(value)
    return value, 0


def redact_file(path: Path, apply: bool) -> tuple[str, int]:
    if path.suffix.lower() == ".key":
        if apply:
            path.unlink()
        return "removed-private-key", 1
    raw = path.read_text(encoding="utf-8", errors="replace")
    if path.suffix.lower() == ".json":
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            redacted, changes = redact_text(raw)
        else:
            redacted_json, changes = redact_json(parsed)
            redacted = json.dumps(redacted_json, indent=2, sort_keys=True) + "\n"
    else:
        redacted, changes = redact_text(raw)
    if changes and apply:
        path.write_text(redacted, encoding="utf-8")
    return "redacted" if changes else "unchanged", changes


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--report", required=True, type=Path)
    args = parser.parse_args()
    root = args.root.resolve()
    if not root.is_dir():
        raise SystemExit("evidence root must be a directory")

    files_scanned = files_changed = values_redacted = private_keys_removed = 0
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink() or path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        files_scanned += 1
        action, changes = redact_file(path, args.apply)
        if changes:
            files_changed += 1
            values_redacted += changes
            if action == "removed-private-key":
                private_keys_removed += 1

    report = {
        "schemaVersion": 1,
        "mode": "apply" if args.apply else "dry-run",
        "filesScanned": files_scanned,
        "filesChanged": files_changed,
        "valuesRedacted": values_redacted,
        "privateKeysRemoved": private_keys_removed,
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(report, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
