#!/usr/bin/env python3
"""Regression tests for Phase 54 readiness, required evidence and tamper detection."""
from __future__ import annotations

import importlib.util
import json
import pathlib
import sys
import tempfile
from types import ModuleType

import yaml

ROOT = pathlib.Path(__file__).resolve().parents[3]
COMMIT = "a" * 40
DIGEST = "sha256:" + "b" * 64
REF = "REL-54-TEST"


def load_module(name: str, relative: str) -> ModuleType:
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    if spec is None or spec.loader is None:
        raise AssertionError(f"cannot load module: {relative}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


BUILD = load_module("phase54_build_manifest", "scripts/certification/build_certification_manifest.py")
VERIFY = load_module("phase54_verify_manifest", "scripts/certification/verify_certification_manifest.py")
CANDIDATE = load_module("phase54_create_candidate", "scripts/certification/create_release_candidate.py")
SCAN = load_module("phase54_scan_evidence", "scripts/certification/scan_evidence.py")


def invoke(module: ModuleType, args: list[str], expected: int = 0) -> None:
    previous = sys.argv
    sys.argv = [getattr(module, "__file__", "module"), *args]
    try:
        try:
            result = module.main()
            code = int(result or 0)
        except SystemExit as exc:
            code = int(exc.code or 0) if isinstance(exc.code, int) else 1
    finally:
        sys.argv = previous
    if code != expected:
        raise AssertionError(f"{module.__name__} returned {code}, expected {expected}; args={args}")


def phase_result(root: pathlib.Path, phase: str, status: str = "PASS") -> None:
    directory = root / "phases" / phase
    directory.mkdir(parents=True, exist_ok=True)
    log = directory / "logs" / "check.log"
    log.parent.mkdir(exist_ok=True)
    log.write_text("ok\n", encoding="utf-8")
    document = {
        "schemaVersion": 1,
        "phase": phase,
        "name": phase,
        "status": status,
        "startedAt": "2026-06-19T00:00:00Z",
        "endedAt": "2026-06-19T00:00:01Z",
        "release": {
            "reference": REF,
            "gitCommit": COMMIT,
            "imageDigest": DIGEST,
            "environment": "uat",
        },
        "checks": [
            {
                "id": "check",
                "status": status,
                "exitCode": 0 if status == "PASS" else 1,
                "log": f"phases/{phase}/logs/check.log",
            }
        ],
    }
    (directory / "result.json").write_text(json.dumps(document), encoding="utf-8")


def required_evidence(root: pathlib.Path) -> None:
    plan = yaml.safe_load((ROOT / "config/phase54-certification-plan.yaml").read_text(encoding="utf-8"))
    for phase in plan["phases"]:
        for relative in phase.get("evidence", []):
            path = root / relative
            if path.exists():
                continue
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("placeholder\n", encoding="utf-8")


def build(root: pathlib.Path, expected: int = 0) -> None:
    invoke(
        BUILD,
        [
            "--root", str(root),
            "--environment", "uat",
            "--reference", REF,
            "--git-commit", COMMIT,
            "--image-digest", DIGEST,
        ],
        expected,
    )


def verify(root: pathlib.Path, require_ready: bool = False, expected: int = 0) -> None:
    args = [str(root / "manifest.json")]
    if require_ready:
        args.append("--require-ready")
    invoke(VERIFY, args, expected)


def main() -> int:
    # Complete evidence is ready and any post-build tampering is detected.
    with tempfile.TemporaryDirectory() as temporary:
        root = pathlib.Path(temporary) / "evidence"
        root.mkdir()
        for letter in "ABCDEFGHIJ":
            phase_result(root, f"54{letter}")
        required_evidence(root)
        build(root)
        verify(root, require_ready=True)
        (root / "phases/54A/logs/check.log").write_text("tampered\n", encoding="utf-8")
        verify(root, expected=1)

    # A failed phase makes the package not ready.
    with tempfile.TemporaryDirectory() as temporary:
        root = pathlib.Path(temporary) / "evidence"
        root.mkdir()
        for letter in "ABCDEFGHIJ":
            phase_result(root, f"54{letter}", "FAIL" if letter == "D" else "PASS")
        required_evidence(root)
        build(root, expected=3)
        verify(root)
        verify(root, require_ready=True, expected=1)

    # A PASS result cannot compensate for missing evidence declared by the plan.
    with tempfile.TemporaryDirectory() as temporary:
        root = pathlib.Path(temporary) / "evidence"
        root.mkdir()
        for letter in "ABCDEFGHIJ":
            phase_result(root, f"54{letter}")
        required_evidence(root)
        (root / "phases/54D/performance-summary.json").unlink()
        build(root, expected=3)
        verify(root, expected=1)

    # Final candidate includes and hashes all ten phase result files.
    with tempfile.TemporaryDirectory() as temporary:
        root = pathlib.Path(temporary) / "evidence"
        root.mkdir()
        for letter in "ABCDEFGHIJ":
            phase_result(root, f"54{letter}")
        output = root / "release-candidate" / "manifest.json"
        invoke(
            CANDIDATE,
            [
                "--root", str(root),
                "--reference", REF,
                "--git-commit", COMMIT,
                "--image-digest", DIGEST,
                "--through", "54J",
                "--output", str(output),
            ],
        )
        candidate = json.loads(output.read_text(encoding="utf-8"))
        assert candidate["certifiedThrough"] == "54J"
        assert [item["id"] for item in candidate["prerequisitePhases"]] == [f"54{x}" for x in "ABCDEFGHIJ"]

    # Runtime evidence scanner detects but never echoes secret values into findings.
    with tempfile.TemporaryDirectory() as temporary:
        root = pathlib.Path(temporary) / "evidence"
        root.mkdir()
        (root / "safe.log").write_text("status=UP\n", encoding="utf-8")
        report = root / "scan.json"
        invoke(SCAN, ["--root", str(root), "--output", str(report)])
        secret = "abcdefghijklmnopqrstuvwxyz123456"
        (root / "unsafe.log").write_text(f"Authorization: Bearer {secret}\n", encoding="utf-8")
        invoke(SCAN, ["--root", str(root), "--output", str(report)], expected=1)
        findings = json.loads(report.read_text(encoding="utf-8"))["findings"]
        assert findings and secret not in json.dumps(findings)

    print("Phase 54 certification framework tests: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
