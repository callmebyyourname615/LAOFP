#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import zipfile
from datetime import datetime, timezone
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def validate_approval(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    errors = []
    if data.get("schemaVersion") != 1:
        errors.append("approval schemaVersion must be 1")
    if data.get("decision") != "APPROVE_PHASE54_ENTRY":
        errors.append("approval decision must be APPROVE_PHASE54_ENTRY")
    for key in ("changeReference", "engineeringLead", "qaLead", "securityLead", "sreLead", "changeManager", "signedAt"):
        if not isinstance(data.get(key), str) or not data[key].strip():
            errors.append(f"approval.{key} is required")
    if errors:
        raise SystemExit("; ".join(errors))
    return data


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--approval", type=Path, required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--migration-digest", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    approval = validate_approval(args.approval)
    run_dir = args.run_dir.resolve()
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    approval_copy = output_dir / "handoff-approval.json"
    approval_copy.write_text(json.dumps(approval, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    files = []
    for path in sorted(run_dir.rglob("*")):
        if not path.is_file() or path.is_symlink():
            continue
        relative = path.resolve().relative_to(run_dir)
        if relative.parts and relative.parts[0] == "64J":
            continue
        files.append(path)
    artifacts = [{
        "path": path.resolve().relative_to(run_dir).as_posix(),
        "sizeBytes": path.stat().st_size,
        "sha256": sha256(path),
    } for path in files]
    manifest = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "status": "APPROVED_FOR_PHASE54_ENTRY",
        "release": {
            "reference": args.reference,
            "gitCommit": args.commit,
            "applicationImageDigest": args.application_digest,
            "migrationImageDigest": args.migration_digest,
        },
        "approval": approval,
        "artifacts": artifacts,
    }
    manifest_path = output_dir / "bundle-manifest.json"
    manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    checksums = output_dir / "checksums.sha256"
    checksums.write_text("".join(f"{item['sha256']}  {item['path']}\n" for item in artifacts), encoding="utf-8")
    summary = output_dir / "PHASE54_HANDOFF.md"
    summary.write_text(
        "# Phase 54 Entry Handoff\n\n"
        f"- Release: `{args.reference}`\n"
        f"- Git commit: `{args.commit}`\n"
        f"- Application image: `{args.application_digest}`\n"
        f"- Migration image: `{args.migration_digest}`\n"
        f"- Evidence artifacts: **{len(artifacts)}**\n"
        f"- Decision: **APPROVED_FOR_PHASE54_ENTRY**\n",
        encoding="utf-8",
    )
    zip_path = output_dir / "phase64-uat-evidence-bundle.zip"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            archive.write(path, arcname=f"evidence/{path.resolve().relative_to(run_dir).as_posix()}")
        archive.write(manifest_path, arcname="bundle-manifest.json")
        archive.write(checksums, arcname="checksums.sha256")
        archive.write(approval_copy, arcname="handoff-approval.json")
        archive.write(summary, arcname="PHASE54_HANDOFF.md")
    print(zip_path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
