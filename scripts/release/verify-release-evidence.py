#!/usr/bin/env python3
"""Verify release evidence, file hashes, path safety, and immutable image identity."""
from __future__ import annotations
import argparse
import hashlib
import json
import re
from pathlib import Path


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("evidence")
    parser.add_argument("--root", default=".")
    parser.add_argument("--expected-commit")
    parser.add_argument("--expected-digest")
    args = parser.parse_args()

    root = Path(args.root).resolve()
    evidence_path = Path(args.evidence).resolve()
    data = json.loads(evidence_path.read_text(encoding="utf-8"))

    if data.get("schemaVersion") != 1:
        raise SystemExit("unsupported evidence schema version")
    commit = data.get("gitCommit", "")
    image_digest = data.get("imageDigest", "")
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise SystemExit("invalid git commit")
    if not re.fullmatch(r"sha256:[a-f0-9]{64}", image_digest):
        raise SystemExit("invalid image digest")
    if args.expected_commit and commit != args.expected_commit:
        raise SystemExit("commit mismatch")
    if args.expected_digest and image_digest != args.expected_digest:
        raise SystemExit("image digest mismatch")

    artifacts = data.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise SystemExit("evidence has no artifacts")
    seen: set[str] = set()
    for item in artifacts:
        rel = item.get("path", "")
        if rel in seen:
            raise SystemExit(f"duplicate artifact path: {rel}")
        seen.add(rel)
        candidate = (root / rel).resolve()
        try:
            candidate.relative_to(root)
        except ValueError as exc:
            raise SystemExit(f"path traversal detected: {rel}") from exc
        if not candidate.is_file() or candidate.is_symlink():
            raise SystemExit(f"artifact missing or not a regular file: {rel}")
        if candidate.stat().st_size != item.get("size"):
            raise SystemExit(f"size mismatch: {rel}")
        if digest(candidate) != item.get("sha256"):
            raise SystemExit(f"hash mismatch: {rel}")
    print(f"verified {len(artifacts)} release artifacts for {commit} {image_digest}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
