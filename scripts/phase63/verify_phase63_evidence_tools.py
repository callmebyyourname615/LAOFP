#!/usr/bin/env python3
from __future__ import annotations
import json
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PYTHON = sys.executable


def run(*args: str, expect: int = 0) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(args, cwd=ROOT, text=True, capture_output=True)
    if completed.returncode != expect:
        raise AssertionError(
            f"command returned {completed.returncode}, expected {expect}: {' '.join(args)}\n"
            f"stdout:\n{completed.stdout}\nstderr:\n{completed.stderr}"
        )
    return completed


def write(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if isinstance(value, (dict, list)):
        path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")
    else:
        path.write_text(str(value), encoding="utf-8")


def main() -> int:
    with tempfile.TemporaryDirectory(prefix="phase63-tools-") as temp:
        base = Path(temp)

        backup_dir = base / "backup-runtime"
        for name in ("full-backup.log", "verify-backup.log", "pitr-restore.log"):
            write(backup_dir / name, f"synthetic evidence for {name}\n")
        backup = {
            "schemaVersion": 1, "backupId": "backup-20260623-001", "backupCompleted": True,
            "checksumVerified": True, "rowCountsMatched": True, "pitrTargetVerified": True,
            "pitrTargetTime": "2026-06-23T08:00:00Z", "rpoMinutes": 4.0, "rtoMinutes": 20.0,
            "transactionLossCount": 0, "outboxReplayIdempotent": True,
            "sreLead": "SRE Lead", "databaseLead": "DB Lead",
            "signedAt": "2026-06-23T09:00:00Z", "changeReference": "CHG-6301",
        }
        write(base / "backup.json", backup)
        run(PYTHON, "scripts/phase63/verify_backup_pitr_attestation.py", "--attestation", str(base / "backup.json"),
            "--evidence-dir", str(backup_dir), "--output", str(base / "backup-report.json"))
        backup_bad = {**backup, "rpoMinutes": 5.0}
        write(base / "backup-bad.json", backup_bad)
        run(PYTHON, "scripts/phase63/verify_backup_pitr_attestation.py", "--attestation", str(base / "backup-bad.json"),
            "--evidence-dir", str(backup_dir), "--output", str(base / "backup-bad-report.json"), expect=1)

        dr_dir = base / "dr-runtime"
        for name in ("dr/reconciliation.json", "dr/replay-integrity.json", "dr/evidence.json",
                     "platform/postgres-failover.log", "platform/postgres-failback.log"):
            write(dr_dir / name, "{}\n")
        dr = {
            "schemaVersion": 1,
            "scenarios": {key: "PASS" for key in (
                "podKill", "kafkaBrokerFailure", "objectStorageFailure", "networkPartition",
                "externalApiTimeout", "deploymentRollback", "postgresFailover", "postgresFailback")},
            "rpoMinutes": 3.0, "rtoMinutes": 18.0, "transactionLossCount": 0,
            "outboxReplayIdempotent": True, "failbackVerified": True,
            "sreLead": "SRE Lead", "applicationLead": "App Lead", "databaseLead": "DB Lead",
            "signedAt": "2026-06-23T09:00:00Z", "changeReference": "CHG-6302",
        }
        write(base / "dr.json", dr)
        run(PYTHON, "scripts/phase63/verify_dr_attestation.py", "--attestation", str(base / "dr.json"),
            "--evidence-dir", str(dr_dir), "--output", str(base / "dr-report.json"))

        inventory = {"schemaVersion": 1, "passed": True, "alertCount": 62}
        delivery = {"passed": True, "alertCount": 58, "observedCount": 58, "missing": [], "wrongReceiver": []}
        alert = {
            "schemaVersion": 1, "repositoryAlertCount": 62, "prometheusRuleAlertCount": 58,
            "pendingFiringResolvedVerified": True, "receiverDeliveryVerified": True,
            "recoveryNotificationVerified": True, "runbooksOpened": True,
            "sreLead": "SRE Lead", "onCallLead": "OnCall Lead",
            "signedAt": "2026-06-23T09:00:00Z", "changeReference": "CHG-6303",
        }
        write(base / "inventory.json", inventory); write(base / "delivery.json", delivery); write(base / "alert.json", alert)
        run(PYTHON, "scripts/phase63/verify_alert_attestation.py", "--inventory", str(base / "inventory.json"),
            "--delivery", str(base / "delivery.json"), "--attestation", str(base / "alert.json"),
            "--output", str(base / "alert-report.json"))

        sanctions_log = base / "sanctions-sync.log"; write(sanctions_log, "sync completed\n")
        sanctions = {
            "schemaVersion": 1, "provider": "UAT Sanctions Provider", "datasetVersion": "2026-06-23",
            "providerRecordCount": 100, "duplicateProviderUidCount": 0, "syncCompleted": True,
            "providerUidUnique": True, "laoNormalizationVerified": True,
            "duplicateHandlingVerified": True, "staleDataAlertVerified": True,
            "screeningRegressionPassed": True, "complianceLead": "Compliance Lead", "qaLead": "QA Lead",
            "signedAt": "2026-06-23T09:00:00Z", "changeReference": "CHG-6304",
        }
        write(base / "sanctions.json", sanctions)
        run(PYTHON, "scripts/phase63/verify_sanctions_attestation.py", "--attestation", str(base / "sanctions.json"),
            "--runtime-log", str(sanctions_log), "--output", str(base / "sanctions-report.json"))

        run_dir = base / "full-run"
        for phase in [f"63{x}" for x in "ABCDEFGHI"]:
            write(run_dir / phase / "evidence.txt", f"{phase} synthetic evidence\n")
            write(run_dir / phase / "result.json", {
                "schemaVersion": 1, "phase": phase, "status": "PASS", "gitCommit": "a" * 40,
                "artifacts": [],
            })
        entry = {
            "schemaVersion": 1, "allCriticalFindingsClosed": True, "runtimeEvidenceReviewed": True,
            "goNoGoApproved": True, "engineeringLead": "Eng Lead", "qaLead": "QA Lead",
            "sreLead": "SRE Lead", "securityLead": "Security Lead", "changeManager": "Change Manager",
            "signedAt": "2026-06-23T09:00:00Z", "changeReference": "CHG-6305",
        }
        write(base / "entry.json", entry)
        run(PYTHON, "scripts/phase63/build_evidence_manifest.py", "--run-dir", str(run_dir), "--mode", "full",
            "--attestation", str(base / "entry.json"), "--output", str(run_dir / "manifest.json"),
            "--checksums", str(run_dir / "SHA256SUMS"))
        run(PYTHON, "scripts/phase63/verify_evidence_manifest.py", "--manifest", str(run_dir / "manifest.json"),
            "--run-dir", str(run_dir), "--mode", "full")
        (run_dir / "63A/evidence.txt").write_text("tampered\n", encoding="utf-8")
        run(PYTHON, "scripts/phase63/verify_evidence_manifest.py", "--manifest", str(run_dir / "manifest.json"),
            "--run-dir", str(run_dir), "--mode", "full", expect=1)

    print("Phase 63 evidence tools: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
