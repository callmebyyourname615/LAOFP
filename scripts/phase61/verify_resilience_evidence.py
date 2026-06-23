#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import re
from pathlib import Path

PLACEHOLDER = re.compile(r"(?i)(replace|todo|tbd|change_me)")
REQUIRED_DRILLS = {
    "application-pod-kill",
    "postgres-primary-failover",
    "postgres-failback",
    "kafka-broker-fail",
    "vault-leader-fail",
    "object-storage-node-fail",
    "network-partition",
    "external-api-timeout",
    "deployment-rollback",
}
REQUIRED_EVIDENCE = {
    "backup/full-backup.log",
    "backup/verify-backup.log",
    "backup/pitr-restore.log",
    "platform/postgres-primary-failover.log",
    "platform/postgres-failback.log",
    "platform/vault-leader-fail.log",
    "platform/object-storage-node-fail.log",
    "dr/evidence.json",
    "alerts/alert-routing-result.json",
    "phase61-resilience-evidence.json",
}


def text(value) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--evidence-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    document = json.loads(args.attestation.read_text(encoding="utf-8"))
    errors: list[str] = []
    if document.get("schemaVersion") != 1:
        errors.append("schemaVersion must equal 1")

    recovery = document.get("recovery") or {}
    if recovery.get("backupVerified") is not True or recovery.get("pitrVerified") is not True:
        errors.append("backup and PITR must be verified")
    try:
        if float(recovery["actualRpoSeconds"]) > float(recovery["approvedRpoSeconds"]):
            errors.append("RPO exceeds approved target")
        if float(recovery["actualRtoSeconds"]) > float(recovery["approvedRtoSeconds"]):
            errors.append("RTO exceeds approved target")
    except Exception:
        errors.append("RPO/RTO values must be numeric")
    if int(recovery.get("financialTransactionLoss", 1)) != 0:
        errors.append("financial transaction loss must be zero")

    drills = {item.get("name"): item for item in document.get("drills", []) if isinstance(item, dict)}
    if set(drills) != REQUIRED_DRILLS:
        errors.append("drill set does not match mandatory Phase 61I scenarios")
    for name in REQUIRED_DRILLS:
        item = drills.get(name, {})
        if item.get("passed") is not True:
            errors.append(f"{name} did not pass")
        if int(item.get("transactionLoss", 1)) != 0:
            errors.append(f"{name} has transaction loss")

    alerts = document.get("alerts") or {}
    if not all(alerts.get(key) is True for key in (
        "allCriticalAlertsFired", "allCriticalAlertsRouted", "acknowledgementVerified"
    )):
        errors.append("critical alert lifecycle is incomplete")

    evidence_root = args.evidence_dir.resolve()
    actual_files = {
        path.relative_to(evidence_root).as_posix()
        for path in evidence_root.rglob("*") if path.is_file() and path.stat().st_size > 0
    } if evidence_root.is_dir() else set()
    for relative in sorted(REQUIRED_EVIDENCE - actual_files):
        errors.append(f"missing runtime evidence: {relative}")

    inventory_path = evidence_root / "phase61-resilience-evidence.json"
    inventory = {}
    if inventory_path.is_file():
        try:
            inventory = json.loads(inventory_path.read_text(encoding="utf-8"))
            listed = {item.get("path"): item for item in inventory.get("files", []) if isinstance(item, dict)}
            for relative, item in listed.items():
                path = evidence_root / relative
                if not path.is_file() or path.stat().st_size != item.get("bytes") or sha256(path) != item.get("sha256"):
                    errors.append(f"resilience evidence hash mismatch: {relative}")
        except Exception as exc:
            errors.append(f"invalid resilience evidence inventory: {exc}")

    alert_result_path = evidence_root / "alerts/alert-routing-result.json"
    if alert_result_path.is_file():
        alert_result = json.loads(alert_result_path.read_text(encoding="utf-8"))
        if not all(alert_result.get(key) is True for key in ("posted", "observable", "resolutionPosted")):
            errors.append("synthetic Alertmanager drill did not complete")

    for key in ("sreLead", "operationsLead", "securityLead", "signedAt"):
        if not text(document.get(key)):
            errors.append(f"{key} missing or placeholder")

    report = {
        "schemaVersion": 1,
        "passed": not errors,
        "evidenceFiles": sorted(actual_files),
        "attestation": document,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 61I resilience certification: {'PASS' if not errors else 'FAIL'}")
    for error in errors:
        print("  ERROR:", error)
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
