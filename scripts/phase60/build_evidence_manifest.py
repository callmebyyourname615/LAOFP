#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

PHASES = [f"60{letter}" for letter in "ABCDEFGHIJ"]
DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
COMMIT = re.compile(r"^[0-9a-f]{40}$")
PLACEHOLDER = re.compile(r"(?i)(replace|change_me|todo|tbd)")
TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def meaningful(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--application-image-digest", required=True)
    parser.add_argument("--migration-image-digest", required=True)
    parser.add_argument("--signoff", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    errors: list[str] = []

    if not DIGEST.fullmatch(args.application_image_digest):
        errors.append("application image digest is not digest-pinned")
    if not DIGEST.fullmatch(args.migration_image_digest):
        errors.append("migration image digest is not digest-pinned")

    signoff = json.loads(args.signoff.read_text(encoding="utf-8"))
    if signoff.get("schemaVersion") != 1 or signoff.get("decision") != "APPROVE_UAT_ENTRY":
        errors.append("UAT entry sign-off is not an approval")
    if signoff.get("runId") != args.run_id:
        errors.append("UAT entry sign-off runId does not match")
    for key in ("qaLead", "securityLead", "sreLead", "engineeringLead", "productOwner", "changeReference"):
        if not meaningful(signoff.get(key)):
            errors.append(f"UAT entry sign-off {key} is missing or a placeholder")
    if not isinstance(signoff.get("signedAt"), str) or not TIMESTAMP.fullmatch(signoff["signedAt"]):
        errors.append("UAT entry sign-off signedAt must be a UTC timestamp")

    phase_entries: dict[str, dict] = {}
    commits: set[str] = set()
    for phase in PHASES:
        path = args.run_dir / phase / "result.json"
        if not path.is_file():
            errors.append(f"missing result for {phase}")
            continue
        result = json.loads(path.read_text(encoding="utf-8"))
        if result.get("status") != "PASS":
            errors.append(f"{phase} status is {result.get('status')}, expected PASS")
        commit = result.get("gitCommit")
        if isinstance(commit, str):
            commits.add(commit)
        if result.get("targetEnvironment") not in {"uat", "repository"}:
            errors.append(f"{phase} has unsupported target environment")
        if phase in {"60F", "60G", "60H", "60I", "60J"} and result.get("targetEnvironment") != "uat":
            errors.append(f"{phase} must be executed against UAT")
        phase_entries[phase] = {
            "status": result.get("status"),
            "resultPath": path.relative_to(args.run_dir).as_posix(),
            "resultSha256": sha256(path),
        }
    if len(commits) != 1:
        errors.append("phase results are not bound to one Git commit")
    git_commit = next(iter(commits), "unknown")
    if not COMMIT.fullmatch(git_commit):
        errors.append("phase result Git commit is not a full lowercase SHA")

    excluded = {args.output.resolve()}
    artifacts: list[dict] = []
    for path in sorted(args.run_dir.rglob("*")):
        if not path.is_file() or path.resolve() in excluded:
            continue
        if path.name.endswith((".tar.gz", ".sha256")):
            continue
        artifacts.append({
            "path": path.relative_to(args.run_dir).as_posix(),
            "sizeBytes": path.stat().st_size,
            "sha256": sha256(path),
        })
    if not artifacts:
        errors.append("evidence bundle contains no artifacts")

    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1

    document = {
        "schemaVersion": 1,
        "runId": args.run_id,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "gitCommit": git_commit,
        "applicationImageDigest": args.application_image_digest,
        "migrationImageDigest": args.migration_image_digest,
        "targetEnvironment": "uat",
        "phases": phase_entries,
        "artifacts": artifacts,
        "uatEntrySignOff": {key: value for key, value in signoff.items() if key != "schemaVersion" and key != "runId"},
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 60 evidence manifest built: {len(artifacts)} artifacts")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
