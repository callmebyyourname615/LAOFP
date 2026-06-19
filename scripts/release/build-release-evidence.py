#!/usr/bin/env python3
"""Build deterministic release evidence for a digest-pinned Switching release."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path

EXCLUDED_PARTS = {".git", "target", "build", ".idea", ".DS_Store", "__pycache__"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def safe_relative(root: Path, path: Path) -> str:
    resolved = path.resolve()
    root_resolved = root.resolve()
    try:
        rel = resolved.relative_to(root_resolved)
    except ValueError as exc:
        raise ValueError(f"artifact escapes repository root: {path}") from exc
    if any(part in EXCLUDED_PARTS for part in rel.parts):
        raise ValueError(f"artifact path contains excluded component: {rel}")
    return rel.as_posix()


def collect(root: Path, patterns: list[str]) -> list[dict[str, object]]:
    seen: set[Path] = set()
    result: list[dict[str, object]] = []
    for pattern in patterns:
        for candidate in root.glob(pattern):
            if not candidate.is_file() or candidate.is_symlink():
                continue
            if any(part in EXCLUDED_PARTS for part in candidate.relative_to(root).parts):
                continue
            seen.add(candidate)
    for candidate in sorted(seen, key=lambda p: p.as_posix()):
        result.append({
            "path": safe_relative(root, candidate),
            "size": candidate.stat().st_size,
            "sha256": sha256(candidate),
        })
    if not result:
        raise SystemExit("no release artifacts matched")
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--commit", required=True)
    parser.add_argument("--image-reference", required=True)
    parser.add_argument("--image-digest", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--builder", default=os.getenv("GITHUB_WORKFLOW", "local"))
    parser.add_argument("--include", action="append", default=[])
    args = parser.parse_args()

    if not __import__("re").fullmatch(r"[a-f0-9]{40}", args.commit):
        raise SystemExit("commit must be a full lowercase 40-character SHA")
    if not __import__("re").fullmatch(r"sha256:[a-f0-9]{64}", args.image_digest):
        raise SystemExit("image digest must be sha256:<64 lowercase hex>")

    root = Path(args.root).resolve()
    patterns = args.include or [
        "pom.xml",
        "Dockerfile",
        "src/main/resources/db/migration/*.sql",
        "k8s/**/*.yaml",
        "schemas/**/*.json",
        "target/*.jar",
    ]
    evidence = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "gitCommit": args.commit,
        "imageReference": args.image_reference,
        "imageDigest": args.image_digest,
        "builder": args.builder,
        "artifacts": collect(root, patterns),
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
