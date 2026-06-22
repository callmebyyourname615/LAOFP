import hashlib
import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[3]
PYTHON = "python3"
NOW = "2026-06-19T00:05:00Z"
CAPTURED = "2026-06-19T00:00:00Z"


class Phase56Tests(unittest.TestCase):
    def run_cmd(self, *args, env=None):
        return subprocess.run(args, cwd=ROOT, text=True, capture_output=True, env=env)

    def test_slo_allows_healthy_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            metrics = {
                "capturedAt": CAPTURED,
                "criticalIncidentOpen": False,
                "objectives": {},
            }
            catalog = yaml.safe_load((ROOT / "slo/slo-catalog.yaml").read_text())
            for service in catalog["services"]:
                for objective in service["objectives"]:
                    metrics["objectives"][f"{service['id']}.{objective['id']}"] = {
                        "achievedPercent": 100
                    }
            (tmp / "metrics.json").write_text(json.dumps(metrics))
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/calculate_error_budget.py",
                "--catalog",
                "slo/slo-catalog.yaml",
                "--metrics",
                str(tmp / "metrics.json"),
                "--thresholds",
                "config/phase56-thresholds.yaml",
                "--now",
                NOW,
                "--output",
                str(tmp / "report.json"),
                "--decision-output",
                str(tmp / "decision.json"),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                json.loads((tmp / "decision.json").read_text())["decision"], "ALLOW"
            )

    def test_slo_blocks_missing_objective(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "metrics.json").write_text(
                json.dumps(
                    {
                        "capturedAt": CAPTURED,
                        "criticalIncidentOpen": False,
                        "objectives": {},
                    }
                )
            )
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/calculate_error_budget.py",
                "--catalog",
                "slo/slo-catalog.yaml",
                "--metrics",
                str(tmp / "metrics.json"),
                "--thresholds",
                "config/phase56-thresholds.yaml",
                "--now",
                NOW,
                "--output",
                str(tmp / "report.json"),
                "--decision-output",
                str(tmp / "decision.json"),
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(
                json.loads((tmp / "decision.json").read_text())["decision"], "BLOCK"
            )

    def test_slo_blocks_stale_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            catalog = yaml.safe_load((ROOT / "slo/slo-catalog.yaml").read_text())
            metrics = {
                "capturedAt": "2026-06-18T00:00:00Z",
                "criticalIncidentOpen": False,
                "objectives": {
                    f"{service['id']}.{objective['id']}": {"achievedPercent": 100}
                    for service in catalog["services"]
                    for objective in service["objectives"]
                },
            }
            (tmp / "metrics.json").write_text(json.dumps(metrics))
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/calculate_error_budget.py",
                "--catalog",
                "slo/slo-catalog.yaml",
                "--metrics",
                str(tmp / "metrics.json"),
                "--thresholds",
                "config/phase56-thresholds.yaml",
                "--now",
                NOW,
                "--output",
                str(tmp / "report.json"),
                "--decision-output",
                str(tmp / "decision.json"),
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(
                json.loads((tmp / "decision.json").read_text())["decision"], "BLOCK"
            )

    def _reconciliation_snapshot(self, duplicate_transactions=0):
        return {
            "capturedAt": CAPTURED,
            "duplicateTransactions": duplicate_transactions,
            "unbalancedJournals": 0,
            "orphanOutboxMessages": 0,
            "overdueWebhookDeliveries": 0,
            "outboxBacklog": 1,
            "openReconciliationExceptions": 0,
        }

    def test_reconciliation_zero_tolerance(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "snapshot.json").write_text(
                json.dumps(self._reconciliation_snapshot())
            )
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/evaluate_reconciliation.py",
                "--snapshot",
                str(tmp / "snapshot.json"),
                "--rules",
                "reconciliation/rules/continuous-integrity.yaml",
                "--thresholds",
                "config/phase56-thresholds.yaml",
                "--now",
                NOW,
                "--output",
                str(tmp / "report.json"),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                json.loads((tmp / "report.json").read_text())["status"], "PASS"
            )

    def test_reconciliation_detects_duplicate(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "snapshot.json").write_text(
                json.dumps(self._reconciliation_snapshot(duplicate_transactions=1))
            )
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/evaluate_reconciliation.py",
                "--snapshot",
                str(tmp / "snapshot.json"),
                "--rules",
                "reconciliation/rules/continuous-integrity.yaml",
                "--thresholds",
                "config/phase56-thresholds.yaml",
                "--now",
                NOW,
                "--output",
                str(tmp / "report.json"),
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(
                json.loads((tmp / "report.json").read_text())["status"], "FAIL"
            )

    def test_capacity_blocks_connection_overcommit(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            snapshot = {
                "application": {
                    "desiredReplicas": 20,
                    "maxDbConnectionsPerPod": 30,
                    "jvmHeapMiB": 900,
                    "containerMemoryLimitMiB": 1200,
                },
                "database": {"connectionUtilizationPercent": 50},
                "kafka": {"consumerReplicas": 3, "partitions": 6},
                "storage": {"forecastDays": 100},
            }
            (tmp / "snapshot.json").write_text(json.dumps(snapshot))
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/verify_capacity_policy.py",
                "--snapshot",
                str(tmp / "snapshot.json"),
                "--policy",
                "capacity/capacity-policy.yaml",
                "--output",
                str(tmp / "report.json"),
            )
            self.assertNotEqual(result.returncode, 0)

    def test_security_detects_critical_event(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "events.json").write_text(
                json.dumps(
                    {
                        "capturedAt": NOW,
                        "events": [
                            {
                                "eventType": "DB_DDL_BY_APPLICATION",
                                "occurredAt": "2026-06-19T00:04:30Z",
                            }
                        ],
                    }
                )
            )
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/evaluate_security_events.py",
                "--events",
                str(tmp / "events.json"),
                "--catalog-dir",
                "security/detections",
                "--output",
                str(tmp / "report.json"),
            )
            self.assertNotEqual(result.returncode, 0)

    def test_progressive_delivery_rolls_back_on_alert(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            snapshot = {
                "errorRatePercent": 0.1,
                "p95LatencyMs": 100,
                "p99LatencyMs": 200,
                "observationSeconds": 1000,
                "criticalAlerts": 1,
                "reconciliationStatus": "PASS",
                "errorBudgetDecision": "ALLOW",
            }
            (tmp / "snapshot.json").write_text(json.dumps(snapshot))
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/analyze_progressive_delivery.py",
                "--snapshot",
                str(tmp / "snapshot.json"),
                "--thresholds",
                "config/phase56-thresholds.yaml",
                "--output",
                str(tmp / "report.json"),
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(
                json.loads((tmp / "report.json").read_text())["decision"],
                "ROLLBACK",
            )

    def test_ha_topology_passes_valid_snapshot(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            policy = yaml.safe_load((ROOT / "ha/ha-policy.yaml").read_text())
            snapshot = {
                "application": {
                    "readyReplicas": policy["application"]["minimumReplicas"],
                    "zones": 2,
                    "pdbMinAvailable": policy["application"][
                        "podDisruptionBudget"
                    ]["minAvailable"],
                },
                "database": {
                    "replicationLagSeconds": 0,
                    "primaryCount": 1,
                    "fencingEnabled": True,
                },
                "kafka": {
                    "brokers": policy["kafka"]["minimumBrokers"],
                    "underReplicatedPartitions": 0,
                    "minimumInSyncReplicas": policy["kafka"][
                        "minimumInSyncReplicas"
                    ],
                },
            }
            (tmp / "snapshot.json").write_text(json.dumps(snapshot))
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/verify_ha_topology.py",
                "--snapshot",
                str(tmp / "snapshot.json"),
                "--policy",
                "ha/ha-policy.yaml",
                "--output",
                str(tmp / "report.json"),
            )
            self.assertEqual(result.returncode, 0, result.stderr)

    def test_evidence_chain_detects_tamper(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            artifact = tmp / "artifact.json"
            artifact.write_text('{"status":"PASS"}\n')
            manifest = {
                "status": "PASS",
                "artifacts": [
                    {
                        "path": "artifact.json",
                        "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                    }
                ],
            }
            (tmp / "manifest.json").write_text(json.dumps(manifest))
            artifact.write_text('{"status":"MODIFIED"}\n')
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/verify_evidence_chain.py",
                "--manifest",
                str(tmp / "manifest.json"),
                "--root",
                str(tmp),
            )
            self.assertNotEqual(result.returncode, 0)

    def test_evidence_store_push_is_kms_encrypted_and_non_destructive(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            bin_dir = tmp / "bin"
            build_dir = tmp / "build"
            bin_dir.mkdir()
            (build_dir / "phase56-day2").mkdir(parents=True)
            (build_dir / "phase56-day2" / "result.json").write_text(
                '{"status":"PASS"}\n'
            )
            log = tmp / "aws.log"
            fake_aws = bin_dir / "aws"
            fake_aws.write_text(
                "#!/usr/bin/env bash\n"
                "set -eu\n"
                "printf '%s\n' \"$*\" >> \"$FAKE_AWS_LOG\"\n"
                "case \"$*\" in\n"
                "  *get-bucket-versioning*) printf '%s\n' '{\"Status\":\"Enabled\"}' ;;\n"
                "  *get-object-lock-configuration*) printf '%s\n' '{\"ObjectLockConfiguration\":{\"ObjectLockEnabled\":\"Enabled\",\"Rule\":{\"DefaultRetention\":{\"Mode\":\"COMPLIANCE\",\"Days\":365}}}}' ;;\n"
                "esac\n"
            )
            fake_aws.chmod(0o755)
            env = os.environ.copy()
            env.update(
                {
                    "PATH": f"{bin_dir}:{env['PATH']}",
                    "FAKE_AWS_LOG": str(log),
                    "RELEASE_REFERENCE": "release-56",
                    "PHASE56_EVIDENCE_BUCKET": "switching-evidence-prod",
                    "PHASE56_EVIDENCE_PREFIX": "phase56/releases",
                    "PHASE56_EVIDENCE_KMS_KEY_ID": "alias/switching-evidence",
                    "PHASE56_EVIDENCE_LOCAL_ROOT": str(build_dir),
                }
            )
            result = self.run_cmd(
                "bash", "scripts/day2/evidence_store.sh", "push", env=env
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            command_log = log.read_text()
            self.assertIn("--sse aws:kms", command_log)
            self.assertIn("--sse-kms-key-id alias/switching-evidence", command_log)
            self.assertNotIn("--delete", command_log)

    def _resilience_scenarios(self, data_loss=False):
        catalog = yaml.safe_load(
            (ROOT / "resilience/scenario-catalog.yaml").read_text()
        )
        scenarios = []
        for index, scenario in enumerate(catalog["scenarios"]):
            scenarios.append(
                {
                    "id": scenario["id"],
                    "status": "PASS",
                    "rtoSeconds": 1,
                    "rpoSeconds": 0,
                    "dataLossCount": 1 if data_loss and index == 0 else 0,
                    "duplicateReplayCount": 0,
                    "reconciliationStatus": "PASS",
                    "alertDeliveryStatus": "PASS",
                    "undocumentedRecoverySteps": 0,
                }
            )
        return scenarios

    def test_resilience_refuses_data_loss(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "results.json").write_text(
                json.dumps({"scenarios": self._resilience_scenarios(data_loss=True)})
            )
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/issue_resilience_certificate.py",
                "--results",
                str(tmp / "results.json"),
                "--policy",
                "resilience/certification-policy.yaml",
                "--thresholds",
                "resilience/acceptance-thresholds.yaml",
                "--catalog",
                "resilience/scenario-catalog.yaml",
                "--release-reference",
                "rel-1",
                "--git-commit",
                "a" * 40,
                "--image-digest",
                "sha256:" + "b" * 64,
                "--output",
                str(tmp / "certificate.json"),
            )
            self.assertNotEqual(result.returncode, 0)
            self.assertEqual(
                json.loads((tmp / "certificate.json").read_text())["status"],
                "NOT_CERTIFIED",
            )

    def test_resilience_issues_certificate_for_complete_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            (tmp / "results.json").write_text(
                json.dumps({"scenarios": self._resilience_scenarios()})
            )
            result = self.run_cmd(
                PYTHON,
                "scripts/day2/issue_resilience_certificate.py",
                "--results",
                str(tmp / "results.json"),
                "--policy",
                "resilience/certification-policy.yaml",
                "--thresholds",
                "resilience/acceptance-thresholds.yaml",
                "--catalog",
                "resilience/scenario-catalog.yaml",
                "--release-reference",
                "rel-1",
                "--git-commit",
                "a" * 40,
                "--image-digest",
                "sha256:" + "b" * 64,
                "--output",
                str(tmp / "certificate.json"),
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertEqual(
                json.loads((tmp / "certificate.json").read_text())["status"],
                "CERTIFIED",
            )


if __name__ == "__main__":
    unittest.main()
