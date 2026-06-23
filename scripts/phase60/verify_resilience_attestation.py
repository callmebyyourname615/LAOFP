#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

EXPECTED_SCENARIOS = {
    "pod-kill", "kafka-fail", "net-partition", "s3-down", "ext-timeout", "deployment-rollback"
}
PLACEHOLDER = re.compile(r"(?i)(replace|change_me|todo|tbd)")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$")


def text(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--alert-inventory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.attestation.read_text(encoding="utf-8"))
    alert_inventory = json.loads(args.alert_inventory.read_text(encoding="utf-8"))
    errors: list[str] = []

    if data.get("schemaVersion") != 1:
        errors.append("schemaVersion must equal 1")
    if not text(data.get("runId")):
        errors.append("runId is missing or still a placeholder")

    backup = data.get("backup") or {}
    if not text(backup.get("backupId")):
        errors.append("backup.backupId is required")
    if not isinstance(backup.get("archiveSha256"), str) or not SHA256.fullmatch(backup["archiveSha256"]):
        errors.append("backup.archiveSha256 must be a lowercase SHA-256")
    for key in ("fullBackupVerified", "crossRegionCopyVerified"):
        if backup.get(key) is not True:
            errors.append(f"backup.{key} must be true")
    if not isinstance(backup.get("completedAt"), str) or not TIMESTAMP.fullmatch(backup["completedAt"]):
        errors.append("backup.completedAt must be a UTC timestamp")

    restore = data.get("restore") or {}
    if restore.get("passed") is not True or restore.get("rowCountAndChecksumMatch") is not True:
        errors.append("restore must pass with row-count and checksum match")
    for actual_key, target_key, maximum in (
        ("actualRtoSeconds", "targetRtoSeconds", 1800),
        ("actualRpoSeconds", "targetRpoSeconds", 300),
    ):
        actual, target = restore.get(actual_key), restore.get(target_key)
        if not isinstance(actual, (int, float)) or not isinstance(target, (int, float)):
            errors.append(f"restore {actual_key}/{target_key} must be numeric")
        elif target > maximum or actual > target:
            errors.append(f"restore {actual_key} exceeds the approved target")

    scenarios = data.get("drScenarios") or []
    by_name = {item.get("name"): item for item in scenarios if isinstance(item, dict)}
    if set(by_name) != EXPECTED_SCENARIOS:
        errors.append("DR scenario set does not match the mandatory Phase 60I set")
    for name in sorted(EXPECTED_SCENARIOS):
        item = by_name.get(name, {})
        if item.get("passed") is not True:
            errors.append(f"DR scenario {name} did not pass")
        if item.get("transactionLoss") != 0:
            errors.append(f"DR scenario {name} has transaction loss")
        recovery = item.get("recoverySeconds")
        if not isinstance(recovery, (int, float)) or recovery > 300:
            errors.append(f"DR scenario {name} recovery exceeds 300 seconds")

    alerts = data.get("alerts") or {}
    inventory_count = alert_inventory.get("alertCount")
    if alert_inventory.get("passed") is not True:
        errors.append("static alert inventory did not pass")
    if alerts.get("staticValidationPassed") is not True:
        errors.append("alerts.staticValidationPassed must be true")
    if alerts.get("staticRuleInventoryCount") != inventory_count:
        errors.append("attested alert count does not match generated inventory")
    if alerts.get("syntheticRouteDelivered") is not True:
        errors.append("synthetic alert was not delivered to the receiver")
    if not text(alerts.get("receiverReference")):
        errors.append("alerts.receiverReference is required")
    if not isinstance(alerts.get("acknowledgedAt"), str) or not TIMESTAMP.fullmatch(alerts["acknowledgedAt"]):
        errors.append("alerts.acknowledgedAt must be a UTC timestamp")

    signoff = data.get("signOff") or {}
    for key in ("sreLead", "operationsLead"):
        if not text(signoff.get(key)):
            errors.append(f"signOff.{key} is required")
    if not isinstance(signoff.get("signedAt"), str) or not TIMESTAMP.fullmatch(signoff["signedAt"]):
        errors.append("signOff.signedAt must be a UTC timestamp")

    report = {
        "schemaVersion": 1,
        "status": "PASS" if not errors else "FAIL",
        "alertRuleCount": inventory_count,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 60I resilience attestation: {report['status']}")
    for error in errors:
        print(f"  ERROR: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
