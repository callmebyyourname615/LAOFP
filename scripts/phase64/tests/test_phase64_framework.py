from __future__ import annotations

import json
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
PYTHON = "python3"


class Phase64FrameworkTest(unittest.TestCase):
    def run_script(self, name: str, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            [PYTHON, str(ROOT / "scripts/phase64" / name), *args],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_runtime_control_extraction_requires_pass(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            manifest = root / "manifest.json"
            output = root / "summary.json"
            manifest.write_text(json.dumps({"release": {}, "controls": [{"id": "full-maven-verify", "status": "PASS"}]}), encoding="utf-8")
            result = self.run_script(
                "extract_runtime_controls.py", "--manifest", str(manifest), "--required", "full-maven-verify",
                "--category", "tests", "--output", str(output),
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue(json.loads(output.read_text())["passed"])

    def test_performance_validator_accepts_threshold_compliant_summaries(self) -> None:
        config = yaml.safe_load((ROOT / "config/phase64-entry-gate.yaml").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            evidence = Path(tmp) / "evidence"
            evidence.mkdir()
            for name, limits in config["performance"]["scenarios"].items():
                summary = {
                    "metrics": {
                        "http_reqs": {"values": {"rate": float(limits["minimumRequestRate"]) + 1}},
                        "http_req_duration": {"values": {"p(95)": float(limits["maximumP95Milliseconds"]) - 1}},
                        "http_req_failed": {"values": {"rate": max(0.0, float(limits["maximumFailureRate"]) / 2)}},
                        "dropped_iterations": {"values": {"count": 0}},
                    }
                }
                (evidence / f"{limits['filePrefix']}20260623T000000Z.json").write_text(json.dumps(summary), encoding="utf-8")
            output = Path(tmp) / "summary.json"
            result = self.run_script(
                "validate_performance_evidence.py", "--config", str(ROOT / "config/phase64-entry-gate.yaml"),
                "--evidence-root", str(evidence), "--reference", "uat-rc-001",
                "--commit", "a" * 40, "--application-digest", "sha256:" + "b" * 64,
                "--output", str(output),
            )
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertTrue(json.loads(output.read_text())["passed"])

    def test_backup_validator_rejects_rpo_breach(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            attestation = root / "backup.json"
            data = json.loads((ROOT / "docs/templates/PHASE64_BACKUP_PITR_ATTESTATION.example.json").read_text())
            data["release"] = {"reference": "uat-rc-001", "gitCommit": "a" * 40, "applicationImageDigest": "sha256:" + "b" * 64}
            data["rpoSeconds"] = 301
            attestation.write_text(json.dumps(data), encoding="utf-8")
            result = self.run_script(
                "validate_backup_attestation.py", "--config", str(ROOT / "config/phase64-entry-gate.yaml"),
                "--attestation", str(attestation), "--reference", "uat-rc-001", "--commit", "a" * 40,
                "--application-digest", "sha256:" + "b" * 64, "--output", str(root / "out.json"),
            )
            self.assertNotEqual(result.returncode, 0)

    def test_entry_gate_requires_all_eight_phases(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            p61 = root / "p61.json"; runtime = root / "runtime.json"
            p61.write_text("{}", encoding="utf-8"); runtime.write_text("{}", encoding="utf-8")
            result = self.run_script(
                "evaluate_entry_gate.py", "--config", str(ROOT / "config/phase64-entry-gate.yaml"),
                "--run-dir", str(root), "--reference", "uat-rc-001", "--commit", "a" * 40,
                "--application-digest", "sha256:" + "b" * 64, "--migration-digest", "sha256:" + "c" * 64,
                "--phase61-manifest", str(p61), "--runtime-manifest", str(runtime), "--output", str(root / "decision.json"),
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(json.loads((root / "decision.json").read_text())["decision"], "BLOCK_PHASE54_ENTRY")


if __name__ == "__main__":
    unittest.main()
