#!/usr/bin/env python3
from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / "scripts/evidence/build_runtime_evidence.py"
VERIFY = ROOT / "scripts/evidence/verify_runtime_evidence.py"
PLAN = ROOT / "config/runtime-evidence-plan.yaml"
COMMIT = "a" * 40
DIGEST = "sha256:" + "b" * 64


class RuntimeEvidenceTest(unittest.TestCase):
    def make_results(self, root: Path, complete: bool) -> None:
        plan = yaml.safe_load(PLAN.read_text(encoding="utf-8"))
        logs = root / "logs"
        logs.mkdir(parents=True)
        rows = []
        controls = plan["controls"] if complete else plan["controls"][:1]
        for control in controls:
            path = logs / f"{control['id']}.log"
            path.write_text("redacted evidence\n", encoding="utf-8")
            rows.append({
                "id": control["id"], "status": "PASS", "exitCode": 0,
                "startedAt": "2026-06-19T00:00:00Z", "endedAt": "2026-06-19T00:00:01Z",
                "logPath": f"logs/{control['id']}.log",
            })
        (root / "step-results.jsonl").write_text(
            "".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")

    def build(self, root: Path) -> subprocess.CompletedProcess[str]:
        return subprocess.run([
            "python3", str(BUILD), "--plan", str(PLAN), "--evidence-dir", str(root),
            "--environment", "uat", "--git-commit", COMMIT, "--image-digest", DIGEST,
            "--release-reference", "CHG-53J-TEST",
        ], text=True, capture_output=True, check=False)

    def test_complete_evidence_is_go_live_ready_and_verifiable(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_results(root, complete=True)
            self.assertEqual(self.build(root).returncode, 0)
            result = subprocess.run([
                "python3", str(VERIFY), str(root / "manifest.json"), "--require-go-live-ready",
                "--expected-commit", COMMIT, "--expected-digest", DIGEST,
            ], check=False)
            self.assertEqual(result.returncode, 0)

    def test_missing_controls_are_not_go_live_ready(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_results(root, complete=False)
            self.assertEqual(self.build(root).returncode, 3)
            document = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
            self.assertFalse(document["goLiveReady"])

    def test_tampering_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            self.make_results(root, complete=True)
            self.assertEqual(self.build(root).returncode, 0)
            next((root / "logs").glob("*.log")).write_text("tampered\n", encoding="utf-8")
            result = subprocess.run(["python3", str(VERIFY), str(root / "manifest.json")], check=False)
            self.assertNotEqual(result.returncode, 0)


if __name__ == "__main__":
    unittest.main()
