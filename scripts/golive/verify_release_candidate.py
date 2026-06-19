#!/usr/bin/env python3
"""Verify release candidate checksums, identity, required artifacts and immutability."""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re

DIGEST_RE = re.compile(r"^sha256:[a-f0-9]{64}$")
REQUIRED = {
    "evidence/phase54-manifest.json",
    "sbom/application.spdx.json",
    "sbom/migration.spdx.json",
    "signatures/application-verification.txt",
    "signatures/migration-verification.txt",
    "provenance/application.intoto.jsonl",
    "provenance/migration.intoto.jsonl",
    "manifests/deployment.yaml",
    "manifests/migration-job.yaml",
}


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--expected-rc-id")
    ap.add_argument("--expected-git-commit")
    ap.add_argument("--expected-application-digest")
    ap.add_argument("--expected-migration-digest")
    ap.add_argument("--output")
    args = ap.parse_args()
    root = pathlib.Path(args.root).resolve()
    manifest_path = root / "manifest.json"
    checksums_path = root / "checksums.sha256"
    errors: list[str] = []
    if not manifest_path.is_file() or manifest_path.is_symlink():
        raise SystemExit("missing regular manifest.json")
    if not checksums_path.is_file() or checksums_path.is_symlink():
        raise SystemExit("missing regular checksums.sha256")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("immutable") is not True:
        errors.append("manifest is not immutable")
    expected = {
        "releaseCandidateId": args.expected_rc_id,
        "gitCommit": args.expected_git_commit,
    }
    for key, value in expected.items():
        if value and manifest.get(key) != value:
            errors.append(f"{key} mismatch")
    images = manifest.get("images", {})
    for name, expected_digest in (("application", args.expected_application_digest), ("migration", args.expected_migration_digest)):
        image = images.get(name, {})
        digest = image.get("digest", "")
        if not DIGEST_RE.fullmatch(digest):
            errors.append(f"invalid {name} digest")
        if expected_digest and digest != expected_digest:
            errors.append(f"{name} digest mismatch")
        if image.get("reference") != f"{image.get('repository')}@{digest}":
            errors.append(f"{name} reference is not digest pinned")
    rows: dict[str, str] = {}
    for line in checksums_path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        try:
            digest, rel = line.split("  ", 1)
        except ValueError:
            errors.append("invalid checksum row")
            continue
        if rel in rows:
            errors.append(f"duplicate checksum row: {rel}")
        rows[rel] = digest
    actual_files = {
        p.relative_to(root).as_posix()
        for p in root.rglob("*")
        if p.is_file() and not p.is_symlink() and p != checksums_path
    }
    if actual_files != set(rows):
        errors.append("checksum inventory does not match candidate files")
    for rel, digest in rows.items():
        path = (root / rel).resolve()
        try:
            path.relative_to(root)
        except ValueError:
            errors.append(f"checksum path escapes root: {rel}")
            continue
        if not path.is_file() or path.is_symlink():
            errors.append(f"artifact missing or symlink: {rel}")
        elif sha256(path) != digest:
            errors.append(f"checksum mismatch: {rel}")
    manifest_paths = {item.get("path") for item in manifest.get("artifacts", [])}
    missing_required = sorted(REQUIRED - manifest_paths)
    if missing_required:
        errors.append("required artifacts missing: " + ", ".join(missing_required))
    for item in manifest.get("artifacts", []):
        rel = item.get("path", "")
        path = root / rel
        if path.is_file() and (item.get("sha256") != sha256(path) or item.get("size") != path.stat().st_size):
            errors.append(f"manifest metadata mismatch: {rel}")
    report = {
        "schemaVersion": 1,
        "releaseCandidateId": manifest.get("releaseCandidateId"),
        "verified": not errors,
        "filesVerified": len(rows),
        "errors": errors,
    }
    text = json.dumps(report, indent=2, sort_keys=True) + "\n"
    if args.output:
        pathlib.Path(args.output).write_text(text, encoding="utf-8")
    print(text, end="")
    return 0 if not errors else 2


if __name__ == "__main__":
    raise SystemExit(main())
