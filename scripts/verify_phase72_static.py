#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import py_compile
import subprocess
import sys
from pathlib import Path

REQUIRED = [
    "AGENT/PHASE72A-72J_CHECKLIST.md",
    "config/phase72/file-ownership-policy.yaml",
    "config/phase72/final-uat-policy.yaml",
    "config/phase72/performance-thresholds.yaml",
    "config/phase72/uat-dependencies.yaml",
    "config/phase72/resilience-policy.yaml",
    "docs/phase72/PHASE72_IMPLEMENTATION.md",
    "docs/phase72/PHASE72_EXIT_CRITERIA.md",
    "docs/phase72/PHASE72_OPERATOR_RUNBOOK.md",
    "docs/phase72/CROSS_BORDER_TIMESTAMP_FIX_REPORT.md",
    "schemas/phase72/phase72-result.schema.json",
    "schemas/phase72/phase72-manifest.schema.json",
    "schemas/phase72/final-go-attestation.schema.json",
    "schemas/phase72/dependency-result.schema.json",
    "schemas/phase72/performance-summary.schema.json",
    "schemas/phase72/secret-rotation-attestation.schema.json",
    "schemas/phase72/runtime-security-attestation.schema.json",
    "scripts/phase72/common.sh",
    "scripts/phase72/write_phase_result.py",
    "scripts/phase72/build_phase72_manifest.py",
    "scripts/phase72/run_phase72.sh",
    "scripts/verify_cross_border_temporal_binding.py",
    "scripts/execute-and-verify/13-phase72-final-uat-closure.sh",
    "src/test/java/com/example/switching/crossborder/CrossBorderTemporalBindingRegressionTest.java",
    ".github/workflows/phase72-final-uat-closure.yml",
]

PHASE_SCRIPTS = [
    "72A-phase71-handoff-collision-guard.sh",
    "72B-crossborder-temporal-binding-closure.sh",
    "72C-full-maven-verification-closure.sh",
    "72D-repository-verification-gate.sh",
    "72E-uat-environment-activation.sh",
    "72F-performance-evidence-campaign.sh",
    "72G-backup-pitr-dr-certification.sh",
    "72H-secret-rotation-purge-ceremony.sh",
    "72I-runtime-security-alert-certification.sh",
    "72J-build-phase54-go-no-go-bundle.sh",
]


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    for rel in REQUIRED:
        if not (root / rel).is_file():
            fail(f"missing required file: {rel}")
    for name in PHASE_SCRIPTS:
        if not (root / "scripts/phase72" / name).is_file():
            fail(f"missing Phase 72 script: {name}")

    shell_files = list((root / "scripts/phase72").glob("*.sh")) + [
        root / "scripts/execute-and-verify/13-phase72-final-uat-closure.sh"
    ]
    for path in shell_files:
        completed = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
        if completed.returncode:
            fail(f"shell syntax error in {path.relative_to(root)}: {completed.stderr.strip()}")

    python_files = list((root / "scripts/phase72").glob("*.py")) + [
        root / "scripts/verify_phase72_static.py",
        root / "scripts/verify_cross_border_temporal_binding.py",
    ]
    for path in python_files:
        try:
            py_compile.compile(str(path), doraise=True)
        except py_compile.PyCompileError as exc:
            fail(f"Python compilation failed for {path.relative_to(root)}: {exc}")

    for path in (root / "schemas/phase72").glob("*.json"):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"invalid JSON {path.relative_to(root)}: {exc}")
        if data.get("$schema") != "https://json-schema.org/draft/2020-12/schema":
            fail(f"schema draft missing from {path.relative_to(root)}")

    for path in (root / "config/phase72").glob("*.yaml"):
        text = path.read_text(encoding="utf-8")
        if "\t" in text or "schemaVersion:" not in text:
            fail(f"invalid YAML contract shape: {path.relative_to(root)}")
        try:
            import yaml  # type: ignore
            parsed = yaml.safe_load(text)
            if not isinstance(parsed, dict) or parsed.get("schemaVersion") != 1:
                fail(f"invalid YAML content: {path.relative_to(root)}")
        except ModuleNotFoundError:
            pass

    scanner = subprocess.run(
        [sys.executable, str(root / "scripts/verify_cross_border_temporal_binding.py"), "--self-test"],
        capture_output=True,
        text=True,
    )
    if scanner.returncode:
        fail(f"temporal scanner self-test failed: {scanner.stdout} {scanner.stderr}")

    test_text = (root / "src/test/java/com/example/switching/crossborder/CrossBorderTemporalBindingRegressionTest.java").read_text(encoding="utf-8")
    for marker in ["Types.TIMESTAMP_WITH_TIMEZONE", "setObject", "Instant"]:
        if marker not in test_text:
            fail(f"regression test missing marker: {marker}")

    run_text = (root / "scripts/phase72/run_phase72.sh").read_text(encoding="utf-8")
    for name in PHASE_SCRIPTS:
        if name not in run_text:
            fail(f"orchestrator does not include {name}")

    manifest_text = (root / "scripts/phase72/build_phase72_manifest.py").read_text(encoding="utf-8")
    for decision in ["PREPARED", "BLOCKED", "NO_GO", "GO"]:
        if decision not in manifest_text:
            fail(f"manifest builder missing decision {decision}")

    print("PASS: Phase 72 static contract")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
