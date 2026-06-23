from __future__ import annotations

import argparse
import importlib.util
import json
import pathlib
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
TOOL = ROOT / "scripts/phase67/phase67_control.py"
POLICY = ROOT / "config/phase67-production-cutover-policy.yaml"
spec = importlib.util.spec_from_file_location("phase67_control", TOOL)
control = importlib.util.module_from_spec(spec)
assert spec.loader
spec.loader.exec_module(control)

RELEASE = {
    "reference": "CHG-2026-0001",
    "rc_id": "switching-1.0.0",
    "git_commit": "a" * 40,
    "application_digest": "sha256:" + "b" * 64,
    "migration_digest": "sha256:" + "c" * 64,
    "environment": "production",
    "mode": "preflight",
}


def ns(**kwargs):
    values = dict(RELEASE)
    values.update(kwargs)
    return argparse.Namespace(**values)


class Phase67FrameworkTest(unittest.TestCase):
    def test_decision_engine_healthy_hold_and_rollback(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = pathlib.Path(td)
            cases = [
                ({"signals": {}}, "CONTINUE", 0),
                ({"signals": {"p95LatencyExceeded": True}}, "HOLD", 2),
                ({"signals": {"financialMismatch": True}}, "ROLLBACK_REQUIRED", 2),
            ]
            for index, (payload, expected_decision, expected_rc) in enumerate(cases):
                source = tmp / f"signals-{index}.json"
                output = tmp / f"decision-{index}.json"
                source.write_text(json.dumps(payload), encoding="utf-8")
                rc = control.cmd_decision(ns(policy=str(POLICY), input=str(source), stage=str(index), output=str(output), allowed_decision=["CONTINUE"]))
                self.assertEqual(rc, expected_rc)
                self.assertEqual(json.loads(output.read_text())["decision"], expected_decision)

    def test_command_center_hash_chain_detects_tampering(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            timeline = pathlib.Path(td) / "timeline.jsonl"
            base = dict(timeline=str(timeline), actor="qa", reference="CHG-2026-0001", event_id=None, recorded_at=None)
            self.assertEqual(control.cmd_event_append(argparse.Namespace(**base, event_type="GATE_OPENED", message="opened")), 0)
            self.assertEqual(control.cmd_event_append(argparse.Namespace(**base, event_type="GATE_APPROVED", message="approved")), 0)
            self.assertEqual(control.cmd_event_verify(argparse.Namespace(timeline=str(timeline), output=None)), 0)
            lines = timeline.read_text().splitlines()
            event = json.loads(lines[0]); event["message"] = "tampered"; lines[0] = json.dumps(event)
            timeline.write_text("\n".join(lines) + "\n")
            self.assertEqual(control.cmd_event_verify(argparse.Namespace(timeline=str(timeline), output=None)), 2)

    def test_hypercare_requires_day_14_and_zero_financial_mismatch(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = pathlib.Path(td); source = tmp / "hypercare.json"; output = tmp / "result.json"
            checkpoint = {"criticalIncidents": 0, "unresolvedHighIncidents": 0, "balanceMismatchCount": 0, "duplicateBusinessReferenceCount": 0, "allSignalsPresent": True}
            payload = {"durationDays": 14, "checkpoints": [dict(checkpoint, day=day) for day in (1, 3, 7, 14)]}
            source.write_text(json.dumps(payload))
            args = argparse.Namespace(policy=str(POLICY), input=str(source), output=str(output))
            self.assertEqual(control.cmd_hypercare(args), 0)
            payload["checkpoints"][-1]["balanceMismatchCount"] = 1
            source.write_text(json.dumps(payload))
            self.assertEqual(control.cmd_hypercare(args), 2)
            self.assertEqual(json.loads(output.read_text())["status"], "FAIL")

    def test_prerequisite_binds_release_identity(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            result = pathlib.Path(td) / "result.json"
            release = {
                "reference": RELEASE["reference"], "releaseCandidateId": RELEASE["rc_id"], "gitCommit": RELEASE["git_commit"],
                "applicationImageDigest": RELEASE["application_digest"], "migrationImageDigest": RELEASE["migration_digest"], "environment": "production",
            }
            result.write_text(json.dumps({"phase": "55B", "status": "PASS", "release": release}))
            args = ns(result=str(result), phase="55B")
            self.assertEqual(control.cmd_prerequisite(args), 0)
            args.migration_digest = "sha256:" + "d" * 64
            self.assertEqual(control.cmd_prerequisite(args), 2)

    def test_bundle_roundtrip_and_checksum_failure(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            tmp = pathlib.Path(td); root = tmp / "root"; out = tmp / "bundle"
            release = {
                "reference": RELEASE["reference"], "releaseCandidateId": RELEASE["rc_id"], "gitCommit": RELEASE["git_commit"],
                "applicationImageDigest": RELEASE["application_digest"], "migrationImageDigest": RELEASE["migration_digest"], "environment": "production", "mode": "execute",
            }
            for letter in "ABCDEFGHI":
                phase = f"67{letter}"; directory = root / "phases" / phase; directory.mkdir(parents=True)
                (directory / "result.json").write_text(json.dumps({"phase": phase, "status": "PASS", "release": release}))
                (directory / "evidence.txt").write_text(f"{phase}\n")
            self.assertEqual(control.cmd_bundle(ns(root=str(root), output_dir=str(out))), 0)
            archive = out / "phase67-bau-acceptance.tar.gz"; checksum = out / "phase67-bau-acceptance.tar.gz.sha256"
            verify = argparse.Namespace(archive=str(archive), checksum=str(checksum), output=None)
            self.assertEqual(control.cmd_verify_bundle(verify), 0)
            checksum.write_text("0" * 64 + f"  {archive.name}\n")
            self.assertEqual(control.cmd_verify_bundle(verify), 2)

    def test_freeze_preflight_never_requires_repository_mutation(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            output = pathlib.Path(td) / "freeze.json"
            args = ns(repository=str(ROOT), attestation="", minimum_approvers=2, maximum_age_seconds=1800, output=str(output))
            self.assertEqual(control.cmd_freeze(args), 3)
            data = json.loads(output.read_text())
            self.assertEqual(data["status"], "PREPARED")
            self.assertIsNone(data["checks"]["repositoryClean"])


if __name__ == "__main__":
    unittest.main()
