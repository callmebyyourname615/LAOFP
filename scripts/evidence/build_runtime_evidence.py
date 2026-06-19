#!/usr/bin/env python3
"""Build a tamper-evident runtime evidence manifest from runner results."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path

import yaml

EXCLUDED = {"manifest.json", "SUMMARY.md", "runtime-evidence-bundle.zip"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def load_jsonl(path: Path) -> dict[str, dict]:
    rows: dict[str, dict] = {}
    if not path.exists():
        return rows
    for number, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        row = json.loads(line)
        control_id = row.get("id")
        if not isinstance(control_id, str) or control_id in rows:
            raise ValueError(f"{path}:{number}: missing or duplicate control id")
        rows[control_id] = row
    return rows


def safe_files(root: Path) -> list[dict[str, object]]:
    result = []
    for path in sorted(root.rglob("*")):
        if path.name in EXCLUDED or not path.is_file() or path.is_symlink():
            continue
        resolved = path.resolve()
        try:
            rel = resolved.relative_to(root.resolve())
        except ValueError as exc:
            raise ValueError(f"artifact escapes evidence root: {path}") from exc
        result.append({"path": rel.as_posix(), "size": path.stat().st_size, "sha256": sha256(path)})
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--plan", default="config/runtime-evidence-plan.yaml")
    parser.add_argument("--evidence-dir", required=True)
    parser.add_argument("--environment", required=True, choices=["uat", "performance", "dr"])
    parser.add_argument("--git-commit", required=True)
    parser.add_argument("--image-digest", required=True)
    parser.add_argument("--release-reference", required=True)
    args = parser.parse_args()

    if not re.fullmatch(r"[a-f0-9]{40}", args.git_commit):
        raise SystemExit("git commit must be a full lowercase 40-character SHA")
    if not re.fullmatch(r"sha256:[a-f0-9]{64}", args.image_digest):
        raise SystemExit("image digest must be sha256:<64 lowercase hex>")

    plan = yaml.safe_load(Path(args.plan).read_text(encoding="utf-8"))
    if plan.get("schemaVersion") != 1:
        raise SystemExit("unsupported runtime evidence plan schema")
    if args.environment not in plan.get("allowedEnvironments", []):
        raise SystemExit("environment is not approved by evidence plan")

    evidence_dir = Path(args.evidence_dir).resolve()
    evidence_dir.mkdir(parents=True, exist_ok=True)
    results = load_jsonl(evidence_dir / "step-results.jsonl")
    controls = []
    known_ids = set()
    for control in plan.get("controls", []):
        control_id = control["id"]
        if control_id in known_ids:
            raise SystemExit(f"duplicate plan control: {control_id}")
        known_ids.add(control_id)
        row = results.get(control_id, {})
        status = row.get("status", "NOT_RUN")
        if status not in {"PASS", "FAIL", "NOT_RUN"}:
            raise SystemExit(f"invalid status for {control_id}")
        controls.append({
            "id": control_id,
            "requiredForGoLive": bool(control.get("requiredForGoLive")),
            "status": status,
            "exitCode": row.get("exitCode"),
            "startedAt": row.get("startedAt"),
            "endedAt": row.get("endedAt"),
            "logPath": row.get("logPath"),
        })
    unknown = sorted(set(results) - known_ids)
    if unknown:
        raise SystemExit(f"runner produced controls absent from plan: {', '.join(unknown)}")

    go_live_ready = all(
        not item["requiredForGoLive"] or item["status"] == "PASS"
        for item in controls
    )
    document = {
        "schemaVersion": 1,
        "planId": plan["planId"],
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "environment": args.environment,
        "release": {
            "gitCommit": args.git_commit,
            "imageDigest": args.image_digest,
            "reference": args.release_reference,
        },
        "controls": controls,
        "artifacts": safe_files(evidence_dir),
        "goLiveReady": go_live_ready,
    }
    manifest = evidence_dir / "manifest.json"
    manifest.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    passed = sum(item["status"] == "PASS" for item in controls)
    failed = sum(item["status"] == "FAIL" for item in controls)
    not_run = sum(item["status"] == "NOT_RUN" for item in controls)
    summary = [
        "# Runtime Evidence Summary",
        "",
        f"- Release: `{args.release_reference}`",
        f"- Environment: `{args.environment}`",
        f"- Git commit: `{args.git_commit}`",
        f"- Image digest: `{args.image_digest}`",
        f"- Controls: PASS {passed}, FAIL {failed}, NOT_RUN {not_run}",
        f"- Go-Live ready: **{'YES' if go_live_ready else 'NO'}**",
        "",
        "| Control | Required | Status |",
        "|---|---:|---|",
    ]
    summary.extend(
        f"| `{item['id']}` | {'yes' if item['requiredForGoLive'] else 'no'} | {item['status']} |"
        for item in controls
    )
    (evidence_dir / "SUMMARY.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
    print(manifest)
    return 0 if go_live_ready else 3


if __name__ == "__main__":
    raise SystemExit(main())
