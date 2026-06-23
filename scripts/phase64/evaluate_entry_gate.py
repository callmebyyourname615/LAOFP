#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

import yaml


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--run-dir", type=Path, required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--migration-digest", required=True)
    parser.add_argument("--phase61-manifest", type=Path, required=True)
    parser.add_argument("--runtime-manifest", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))
    errors: list[str] = []
    phases = {}
    expected_release = {
        "reference": args.reference,
        "gitCommit": args.commit,
        "applicationImageDigest": args.application_digest,
        "migrationImageDigest": args.migration_digest,
    }
    for phase in config["requiredPhases"]:
        result_path = args.run_dir / phase / "result.json"
        if not result_path.is_file():
            errors.append(f"missing phase result: {phase}")
            phases[phase] = {"status": "MISSING"}
            continue
        data = json.loads(result_path.read_text(encoding="utf-8"))
        status = data.get("status")
        release = data.get("release") or {}
        if status != "PASS":
            errors.append(f"{phase} status is {status}")
        for key, expected in expected_release.items():
            if release.get(key) != expected:
                errors.append(f"{phase} release.{key} mismatch")
        phases[phase] = {
            "status": status,
            "path": result_path.relative_to(args.run_dir).as_posix(),
            "sha256": sha256(result_path),
        }
    source_evidence = {}
    for name, path in (("phase61", args.phase61_manifest), ("runtime", args.runtime_manifest)):
        if not path.is_file():
            errors.append(f"missing source evidence manifest: {name}")
            continue
        source_evidence[name] = {
            "path": path.relative_to(args.run_dir).as_posix(),
            "sha256": sha256(path),
        }
    decision = "APPROVE_PHASE54_ENTRY" if not errors else "BLOCK_PHASE54_ENTRY"
    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "decision": decision,
        "release": expected_release,
        "phases": phases,
        "sourceEvidence": source_evidence,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 54 entry gate: {decision}")
    for error in errors:
        print(f"ERROR: {error}")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
