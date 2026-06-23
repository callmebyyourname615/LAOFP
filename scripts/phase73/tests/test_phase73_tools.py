import datetime as dt
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
TOOLS = ROOT / "scripts/phase73"
POLICY = ROOT / "config/phase73-chaos-policy.yaml"


def run(*args, env=None, check=False):
    result = subprocess.run([str(a) for a in args], cwd=ROOT, env=env, capture_output=True, text=True, timeout=30)
    if check and result.returncode:
        raise AssertionError(f"command failed: {args}\nstdout={result.stdout}\nstderr={result.stderr}")
    return result


def approval_document(expired=False):
    now = dt.datetime.now(dt.timezone.utc)
    return {
        "schemaVersion": 1,
        "approved": True,
        "tokenId": "CHAOS-UAT-UNIT-TEST-1234",
        "environment": "uat",
        "approvedBy": "Unit Test SRE",
        "approvedAt": (now - dt.timedelta(minutes=1)).isoformat().replace("+00:00", "Z"),
        "expiresAt": (now - dt.timedelta(seconds=1) if expired else now + dt.timedelta(hours=1)).isoformat().replace("+00:00", "Z"),
        "changeReference": "CHG-UNIT-73",
        "scenarios": [
            "pod-kill", "database-network-loss", "kafka-network-delay",
            "object-storage-network-loss", "external-api-delay", "dns-error",
            "cpu-stress", "memory-stress",
        ],
    }


class Phase73ToolsTest(unittest.TestCase):
    def test_approval_accepts_valid_and_rejects_expired(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = pathlib.Path(tmp) / "approval.json"
            path.write_text(json.dumps(approval_document()), encoding="utf-8")
            result = run(sys.executable, TOOLS / "validate_approval.py", "--approval", path, "--token", "CHAOS-UAT-UNIT-TEST-1234", "--scenario", "pod-kill")
            self.assertEqual(0, result.returncode, result.stderr)
            path.write_text(json.dumps(approval_document(expired=True)), encoding="utf-8")
            result = run(sys.executable, TOOLS / "validate_approval.py", "--approval", path, "--token", "CHAOS-UAT-UNIT-TEST-1234")
            self.assertNotEqual(0, result.returncode)
            self.assertIn("expired", result.stderr)
            old = approval_document()
            old["approvedAt"] = (dt.datetime.now(dt.timezone.utc) - dt.timedelta(hours=3)).isoformat().replace("+00:00", "Z")
            path.write_text(json.dumps(old), encoding="utf-8")
            result = run(sys.executable, TOOLS / "validate_approval.py", "--approval", path, "--token", "CHAOS-UAT-UNIT-TEST-1234", "--maximum-age-minutes", "120")
            self.assertNotEqual(0, result.returncode)
            self.assertIn("maximumAgeMinutes", result.stderr)
            incomplete = approval_document()
            incomplete["scenarios"].remove("dns-error")
            path.write_text(json.dumps(incomplete), encoding="utf-8")
            result = run(sys.executable, TOOLS / "validate_approval.py", "--approval", path, "--token", "CHAOS-UAT-UNIT-TEST-1234", "--required-scenarios-json", '["pod-kill","dns-error"]')
            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing required scenarios", result.stderr)

    def test_manifest_render_is_strict(self):
        env = os.environ.copy()
        env.update({
            "PHASE73_NAMESPACE": "switching",
            "PHASE73_APP_LABEL": "switching-api",
            "PHASE73_RUN_ID": "unit-test",
            "PHASE73_RUN_ID_SAFE": "unit-test",
            "PHASE73_EXPERIMENT_DURATION": "1",
        })
        template = ROOT / "chaos/phase73/experiments/73B-pod-kill.yaml"
        with tempfile.TemporaryDirectory() as tmp:
            output = pathlib.Path(tmp) / "out.yaml"
            result = run(sys.executable, TOOLS / "render_manifest.py", "--template", template, "--output", output, env=env)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("kind: PodChaos", output.read_text(encoding="utf-8"))
            bad_env = env.copy(); bad_env.pop("PHASE73_APP_LABEL")
            result = run(sys.executable, TOOLS / "render_manifest.py", "--template", template, "--output", output, env=bad_env)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("missing manifest variables", result.stderr)

    def test_scenario_thresholds_pass_and_fail_closed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            scenarios = approval_document()["scenarios"]
            for scenario in scenarios:
                path = root / "run" / scenario / "attestation.json"
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({
                    "schemaVersion": 1, "runId": "unit", "scenario": scenario,
                    "status": "PASS", "startedAt": "2026-01-01T00:00:00Z",
                    "finishedAt": "2026-01-01T00:00:10Z", "recoveryTimeSeconds": 10,
                    "cleanupStatus": "PASS",
                    "integrity": {"dataLossCount": 0, "duplicateReplayCount": 0, "balanceMismatchCount": 0, "outboxBacklogGrowth": 0},
                    "artifacts": [],
                }), encoding="utf-8")
            output = root / "summary.json"
            result = run(sys.executable, TOOLS / "verify_scenario_evidence.py", "--policy", POLICY, "--evidence-root", root, "--output", output)
            self.assertEqual(0, result.returncode, result.stderr)
            broken = root / "run/pod-kill/attestation.json"
            doc = json.loads(broken.read_text()); doc["integrity"]["balanceMismatchCount"] = 1
            broken.write_text(json.dumps(doc), encoding="utf-8")
            result = run(sys.executable, TOOLS / "verify_scenario_evidence.py", "--policy", POLICY, "--evidence-root", root, "--output", output)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual("FAIL", json.loads(output.read_text())["status"])

    @unittest.skipUnless(shutil.which("openssl"), "openssl is required")
    def test_signed_bundle_roundtrip_and_tamper_detection(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            run_dir = root / "unit-run"; run_dir.mkdir()
            for letter in "ABCDEFGHI":
                path = run_dir / f"73{letter}" / "result.json"
                path.parent.mkdir()
                path.write_text(json.dumps({"status": "PASS"}), encoding="utf-8")
            for scenario in approval_document()["scenarios"]:
                path = run_dir / "scenario-evidence" / scenario / "attestation.json"
                path.parent.mkdir(parents=True)
                path.write_text(json.dumps({"scenario": scenario, "status": "PASS"}), encoding="utf-8")
            summary = root / "summary.json"
            summary.write_text(json.dumps({"status": "PASS", "required": 8, "passed": 8, "failed": 0, "passPercent": 100.0}), encoding="utf-8")
            approval = root / "approval.json"; approval.write_text(json.dumps(approval_document()), encoding="utf-8")
            key = root / "key.pem"
            run("openssl", "genpkey", "-algorithm", "EC", "-pkeyopt", "ec_paramgen_curve:P-256", "-out", key, check=True)
            bundle = root / "bundle"
            result = run(sys.executable, TOOLS / "build_resilience_bundle.py", "--run-dir", run_dir, "--approval", approval, "--scenario-summary", summary, "--bundle-dir", bundle, "--signing-key", key)
            self.assertEqual(0, result.returncode, result.stderr)
            result = run(sys.executable, TOOLS / "verify_bundle.py", "--bundle-dir", bundle)
            self.assertEqual(0, result.returncode, result.stderr)
            target = bundle / "scenario-summary.json"
            target.write_text(target.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            result = run(sys.executable, TOOLS / "verify_bundle.py", "--bundle-dir", bundle)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("checksum mismatch", result.stderr)


if __name__ == "__main__":
    unittest.main()
