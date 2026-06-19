#!/usr/bin/env python3
"""Assemble an immutable, tamper-evident production release candidate."""
from __future__ import annotations
import argparse
import datetime as dt
import hashlib
import json
import os
import pathlib
import re
import shutil
import stat
import sys
from typing import Iterable

DIGEST_RE = re.compile(r"^sha256:[a-f0-9]{64}$")
SHA_RE = re.compile(r"^[a-f0-9]{40}$")
REF_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{2,127}$")
RC_RE = re.compile(r"^switching-[A-Za-z0-9][A-Za-z0-9._-]{2,95}$")


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def require_regular(path: pathlib.Path, label: str) -> pathlib.Path:
    path = path.resolve()
    if not path.is_file() or path.is_symlink():
        raise SystemExit(f"{label} must be a regular, non-symlink file: {path}")
    return path


def safe_rel(value: str) -> pathlib.PurePosixPath:
    rel = pathlib.PurePosixPath(value)
    if rel.is_absolute() or ".." in rel.parts or not rel.parts:
        raise SystemExit(f"unsafe destination path: {value}")
    return rel


def copy_file(src: pathlib.Path, root: pathlib.Path, rel: str) -> dict:
    destination_rel = safe_rel(rel)
    destination = root.joinpath(*destination_rel.parts)
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, destination)
    destination.chmod(stat.S_IRUSR | stat.S_IRGRP | stat.S_IROTH)
    return {
        "path": destination_rel.as_posix(),
        "size": destination.stat().st_size,
        "sha256": sha256(destination),
    }


def load_phase54_manifest(path: pathlib.Path, reference: str, commit: str, app_digest: str) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("releaseCandidateReady") is not True:
        raise SystemExit("Phase 54 manifest is not releaseCandidateReady")
    release = data.get("release", {})
    if release.get("reference") != reference or release.get("gitCommit") != commit:
        raise SystemExit("Phase 54 release identity mismatch")
    # Phase 54 certifies the application artifact. A distinct migration image is
    # allowed in Phase 55, but the application digest must match exactly.
    if release.get("imageDigest") != app_digest:
        raise SystemExit("Phase 54 application image digest mismatch")
    for phase in data.get("phases", []):
        if phase.get("requiredForReleaseCandidate") and phase.get("status") != "PASS":
            raise SystemExit(f"Phase 54 prerequisite is not PASS: {phase.get('id')}")
    return data


def parse_artifact(value: str) -> tuple[pathlib.Path, str]:
    if "=" not in value:
        raise SystemExit("--artifact must be SOURCE=DESTINATION")
    src, rel = value.split("=", 1)
    return pathlib.Path(src), rel


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--output", required=True)
    ap.add_argument("--reference", required=True)
    ap.add_argument("--rc-id", required=True)
    ap.add_argument("--git-commit", required=True)
    ap.add_argument("--application-image-repository", required=True)
    ap.add_argument("--application-image-digest", required=True)
    ap.add_argument("--migration-image-repository", required=True)
    ap.add_argument("--migration-image-digest", required=True)
    ap.add_argument("--phase54-manifest", required=True)
    ap.add_argument("--artifact", action="append", default=[])
    ap.add_argument("--source-date-epoch", type=int, default=None)
    args = ap.parse_args()

    if not REF_RE.fullmatch(args.reference):
        raise SystemExit("invalid release reference")
    if not RC_RE.fullmatch(args.rc_id):
        raise SystemExit("invalid RC id")
    if not SHA_RE.fullmatch(args.git_commit):
        raise SystemExit("invalid Git commit")
    for label, digest in (("application", args.application_image_digest), ("migration", args.migration_image_digest)):
        if not DIGEST_RE.fullmatch(digest):
            raise SystemExit(f"invalid {label} image digest")
    for label, repository in (("application", args.application_image_repository), ("migration", args.migration_image_repository)):
        if "@" in repository or repository.endswith(":latest") or not repository.strip():
            raise SystemExit(f"invalid {label} image repository")

    output = pathlib.Path(args.output).resolve()
    if output.exists() and any(output.iterdir()):
        raise SystemExit(f"output directory must be empty: {output}")
    output.mkdir(parents=True, exist_ok=True)

    phase54_path = require_regular(pathlib.Path(args.phase54_manifest), "Phase 54 manifest")
    phase54 = load_phase54_manifest(phase54_path, args.reference, args.git_commit, args.application_image_digest)
    artifacts: list[dict] = []
    artifacts.append(copy_file(phase54_path, output, "evidence/phase54-manifest.json"))

    required_destinations = {
        "sbom/application.spdx.json",
        "sbom/migration.spdx.json",
        "signatures/application-verification.txt",
        "signatures/migration-verification.txt",
        "provenance/application.intoto.jsonl",
        "provenance/migration.intoto.jsonl",
        "manifests/deployment.yaml",
        "manifests/migration-job.yaml",
    }
    seen_destinations: set[str] = set()
    for raw in args.artifact:
        source, rel = parse_artifact(raw)
        rel = safe_rel(rel).as_posix()
        if rel in seen_destinations:
            raise SystemExit(f"duplicate artifact destination: {rel}")
        seen_destinations.add(rel)
        artifacts.append(copy_file(require_regular(source, "artifact"), output, rel))
    missing = sorted(required_destinations - seen_destinations)
    if missing:
        raise SystemExit("missing required release artifacts: " + ", ".join(missing))

    created = dt.datetime.fromtimestamp(
        args.source_date_epoch if args.source_date_epoch is not None else int(dt.datetime.now(dt.timezone.utc).timestamp()),
        tz=dt.timezone.utc,
    ).isoformat().replace("+00:00", "Z")
    manifest = {
        "schemaVersion": 1,
        "releaseCandidateId": args.rc_id,
        "releaseReference": args.reference,
        "gitCommit": args.git_commit,
        "createdAt": created,
        "images": {
            "application": {
                "repository": args.application_image_repository,
                "digest": args.application_image_digest,
                "reference": f"{args.application_image_repository}@{args.application_image_digest}",
            },
            "migration": {
                "repository": args.migration_image_repository,
                "digest": args.migration_image_digest,
                "reference": f"{args.migration_image_repository}@{args.migration_image_digest}",
            },
        },
        "minimumFlywayVersion": "83",
        "phase54ManifestSha256": sha256(phase54_path),
        "phase54ReleaseCandidateReady": phase54.get("releaseCandidateReady"),
        "artifacts": sorted(artifacts, key=lambda item: item["path"]),
        "immutable": True,
    }
    manifest_path = output / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    manifest_path.chmod(stat.S_IRUSR | stat.S_IRGRP | stat.S_IROTH)

    checksum_rows = []
    for path in sorted(p for p in output.rglob("*") if p.is_file() and p.name != "checksums.sha256"):
        checksum_rows.append(f"{sha256(path)}  {path.relative_to(output).as_posix()}")
    (output / "checksums.sha256").write_text("\n".join(checksum_rows) + "\n", encoding="utf-8")
    print(json.dumps({"releaseCandidate": args.rc_id, "artifacts": len(artifacts), "output": str(output)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
