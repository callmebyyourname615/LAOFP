#!/usr/bin/env python3
"""Build the tamper-evident Phase 54 certification manifest."""
from __future__ import annotations
import argparse, datetime, hashlib, json, pathlib, re
import yaml


def sha256(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True)
    ap.add_argument("--plan", default="config/phase54-certification-plan.yaml")
    ap.add_argument("--environment", required=True, choices=["uat", "performance", "dr"])
    ap.add_argument("--reference", required=True)
    ap.add_argument("--git-commit", required=True)
    ap.add_argument("--image-digest", required=True)
    args = ap.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{2,127}", args.reference):
        raise SystemExit("invalid release reference")
    if not re.fullmatch(r"[a-f0-9]{40}", args.git_commit):
        raise SystemExit("invalid git commit")
    if not re.fullmatch(r"sha256:[a-f0-9]{64}", args.image_digest):
        raise SystemExit("invalid image digest")
    root = pathlib.Path(args.root).resolve()
    plan = yaml.safe_load(pathlib.Path(args.plan).read_text(encoding="utf-8"))
    phases = []
    ready = True
    for spec in plan["phases"]:
        result_path = root / "phases" / spec["id"] / "result.json"
        if result_path.is_file():
            result = json.loads(result_path.read_text(encoding="utf-8"))
            release = result.get("release", {})
            if release.get("gitCommit") != args.git_commit or release.get("imageDigest") != args.image_digest or release.get("reference") != args.reference:
                raise SystemExit(f"release identity mismatch in {spec['id']}")
            status = result.get("status", "FAIL")
            checks = result.get("checks", [])
            exit_code = 0 if status == "PASS" else 1
            started, ended = result.get("startedAt"), result.get("endedAt")
            phase_artifacts = sorted(str(p.relative_to(root)) for p in result_path.parent.rglob("*") if p.is_file())
        else:
            status, checks, exit_code, started, ended, phase_artifacts = "NOT_RUN", [], None, None, None, []
        required = bool(spec.get("requiredForReleaseCandidate"))
        required_evidence = list(spec.get("evidence", []))
        missing_required_evidence = []
        for rel in required_evidence:
            evidence_path = (root / rel).resolve()
            try:
                evidence_path.relative_to(root)
            except ValueError:
                raise SystemExit(f"required evidence path escapes root in {spec['id']}: {rel}")
            if not evidence_path.is_file() or evidence_path.is_symlink():
                missing_required_evidence.append(rel)
        if status == "PASS" and missing_required_evidence:
            status = "FAIL"
            exit_code = 1
        if required and status != "PASS":
            ready = False
        phases.append({
            "id": spec["id"], "name": spec["name"], "status": status,
            "requiredForReleaseCandidate": required, "startedAt": started, "endedAt": ended,
            "exitCode": exit_code, "artifacts": phase_artifacts,
            "requiredEvidence": required_evidence,
            "missingRequiredEvidence": missing_required_evidence,
        })
    artifacts = []
    top_level_outputs = {root / "manifest.json", root / "manifest.sha256"}
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path.is_symlink() or path in top_level_outputs:
            continue
        rel = str(path.relative_to(root))
        artifacts.append({"path": rel, "size": path.stat().st_size, "sha256": sha256(path)})
    doc = {
        "schemaVersion": 1,
        "planId": plan["planId"],
        "generatedAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "environment": args.environment,
        "release": {"reference": args.reference, "gitCommit": args.git_commit, "imageDigest": args.image_digest},
        "phases": phases,
        "artifacts": artifacts,
        "releaseCandidateReady": ready,
    }
    out = root / "manifest.json"
    out.write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    (root / "manifest.sha256").write_text(f"{sha256(out)}  manifest.json\n", encoding="utf-8")
    print(json.dumps({"manifest": str(out), "releaseCandidateReady": ready, "phases": len(phases), "artifacts": len(artifacts)}))
    return 0 if ready else 3

if __name__ == "__main__":
    raise SystemExit(main())
