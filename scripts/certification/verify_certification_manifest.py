#!/usr/bin/env python3
"""Verify Phase 54 manifest structure, release identity, hashes and readiness."""
from __future__ import annotations
import argparse, hashlib, json, pathlib, re


def digest(path: pathlib.Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("manifest")
    ap.add_argument("--require-ready", action="store_true")
    ap.add_argument("--expected-commit")
    ap.add_argument("--expected-digest")
    ap.add_argument("--expected-reference")
    args = ap.parse_args()
    manifest = pathlib.Path(args.manifest).resolve()
    root = manifest.parent
    data = json.loads(manifest.read_text(encoding="utf-8"))
    if data.get("schemaVersion") != 1 or data.get("planId") != "switching-phase54-production-certification-v1":
        raise SystemExit("unsupported certification manifest")
    release = data.get("release", {})
    commit, image, reference = release.get("gitCommit", ""), release.get("imageDigest", ""), release.get("reference", "")
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{2,127}", reference):
        raise SystemExit("invalid release reference")
    if not re.fullmatch(r"[a-f0-9]{40}", commit) or not re.fullmatch(r"sha256:[a-f0-9]{64}", image):
        raise SystemExit("invalid release identity")
    if args.expected_commit and args.expected_commit != commit: raise SystemExit("git commit mismatch")
    if args.expected_digest and args.expected_digest != image: raise SystemExit("image digest mismatch")
    if args.expected_reference and args.expected_reference != reference: raise SystemExit("release reference mismatch")
    phases = data.get("phases")
    expected_ids = [f"54{letter}" for letter in "ABCDEFGHIJ"]
    if not isinstance(phases, list) or [p.get("id") for p in phases] != expected_ids:
        raise SystemExit("phase list must be ordered 54A through 54J")
    for phase in phases:
        required_evidence = phase.get("requiredEvidence")
        missing = phase.get("missingRequiredEvidence")
        if not isinstance(required_evidence, list) or not isinstance(missing, list):
            raise SystemExit(f"phase evidence contract missing: {phase.get('id')}")
        if missing:
            raise SystemExit(f"required evidence missing for {phase.get('id')}: {', '.join(missing)}")
    calculated_ready = all((not p.get("requiredForReleaseCandidate")) or p.get("status") == "PASS" for p in phases)
    if calculated_ready != bool(data.get("releaseCandidateReady")):
        raise SystemExit("releaseCandidateReady does not match phase states")
    artifacts = data.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        raise SystemExit("no artifacts in manifest")
    seen = set()
    for item in artifacts:
        rel = item.get("path", "")
        if rel in seen: raise SystemExit(f"duplicate artifact: {rel}")
        seen.add(rel)
        path = (root / rel).resolve()
        try: path.relative_to(root)
        except ValueError as exc: raise SystemExit(f"path traversal: {rel}") from exc
        if not path.is_file() or path.is_symlink(): raise SystemExit(f"missing or unsafe artifact: {rel}")
        if path.stat().st_size != item.get("size"): raise SystemExit(f"size mismatch: {rel}")
        if digest(path) != item.get("sha256"): raise SystemExit(f"hash mismatch: {rel}")
    for phase in phases:
        for rel in phase["requiredEvidence"]:
            if rel not in seen:
                raise SystemExit(f"required evidence is not hash-covered: {phase.get('id')} {rel}")
    checksum = root / "manifest.sha256"
    if checksum.is_file():
        expected = checksum.read_text(encoding="utf-8").split()[0]
        if expected != digest(manifest): raise SystemExit("manifest checksum mismatch")
    if args.require_ready and not calculated_ready: raise SystemExit("release candidate is not ready")
    print(f"Phase 54 certification verified: phases=10 artifacts={len(seen)} ready={calculated_ready}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
