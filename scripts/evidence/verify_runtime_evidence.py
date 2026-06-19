#!/usr/bin/env python3
"""Verify runtime evidence structure, hashes, release identity and Go-Live state."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path


def digest(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("manifest")
    parser.add_argument("--require-go-live-ready", action="store_true")
    parser.add_argument("--expected-commit")
    parser.add_argument("--expected-digest")
    parser.add_argument("--expected-reference")
    args = parser.parse_args()

    manifest = Path(args.manifest).resolve()
    root = manifest.parent
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1:
        raise SystemExit("unsupported runtime evidence schema")
    release = data.get("release") or {}
    commit = release.get("gitCommit", "")
    image = release.get("imageDigest", "")
    if not re.fullmatch(r"[a-f0-9]{40}", commit):
        raise SystemExit("invalid git commit")
    if not re.fullmatch(r"sha256:[a-f0-9]{64}", image):
        raise SystemExit("invalid image digest")
    if args.expected_commit and args.expected_commit != commit:
        raise SystemExit("git commit mismatch")
    if args.expected_digest and args.expected_digest != image:
        raise SystemExit("image digest mismatch")
    if args.expected_reference and args.expected_reference != release.get("reference"):
        raise SystemExit("release reference mismatch")

    artifacts = data.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise SystemExit("manifest contains no artifacts")
    artifact_paths = {item.get("path") for item in artifacts}

    controls = data.get("controls")
    if not isinstance(controls, list) or not controls:
        raise SystemExit("manifest contains no controls")
    ids = set()
    calculated_ready = True
    for control in controls:
        control_id = control.get("id")
        if not isinstance(control_id, str) or control_id in ids:
            raise SystemExit("missing or duplicate control id")
        ids.add(control_id)
        status = control.get("status")
        if status not in {"PASS", "FAIL", "NOT_RUN"}:
            raise SystemExit(f"invalid control status: {control_id}")
        if control.get("requiredForGoLive") and status != "PASS":
            calculated_ready = False
        log_path = control.get("logPath")
        exit_code = control.get("exitCode")
        if status != "NOT_RUN" and not log_path:
            raise SystemExit(f"executed control has no log path: {control_id}")
        if status != "NOT_RUN" and log_path not in artifact_paths:
            raise SystemExit(f"control log is not hash-covered: {control_id}")
        if status == "PASS" and exit_code != 0:
            raise SystemExit(f"PASS control has non-zero exit code: {control_id}")
        if status == "FAIL" and (not isinstance(exit_code, int) or exit_code == 0):
            raise SystemExit(f"FAIL control has invalid exit code: {control_id}")
        if status == "NOT_RUN" and any(control.get(key) is not None for key in ("exitCode", "startedAt", "endedAt", "logPath")):
            raise SystemExit(f"NOT_RUN control contains execution metadata: {control_id}")

    if bool(data.get("goLiveReady")) != calculated_ready:
        raise SystemExit("goLiveReady does not match control statuses")
    if args.require_go_live_ready and not calculated_ready:
        raise SystemExit("runtime evidence is not Go-Live ready")

    seen = set()
    for artifact in artifacts:
        rel = artifact.get("path", "")
        if rel in seen:
            raise SystemExit(f"duplicate artifact: {rel}")
        seen.add(rel)
        candidate = (root / rel).resolve()
        try:
            candidate.relative_to(root)
        except ValueError as exc:
            raise SystemExit(f"path traversal detected: {rel}") from exc
        if not candidate.is_file() or candidate.is_symlink():
            raise SystemExit(f"missing or unsafe artifact: {rel}")
        if candidate.stat().st_size != artifact.get("size"):
            raise SystemExit(f"artifact size mismatch: {rel}")
        if digest(candidate) != artifact.get("sha256"):
            raise SystemExit(f"artifact hash mismatch: {rel}")
    print(f"Runtime evidence verified: controls={len(controls)} artifacts={len(seen)} goLiveReady={calculated_ready}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
