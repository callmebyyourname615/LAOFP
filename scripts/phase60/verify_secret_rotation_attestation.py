#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

import yaml

PLACEHOLDER = re.compile(r"(?i)(replace-me|change_me|example|todo|tbd)")
TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z$")
SHA = re.compile(r"^[0-9a-f]{40}$")


def meaningful(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip()) and not PLACEHOLDER.search(value)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--inventory", default="security/rotation/phase60-secret-rotation-inventory.yaml")
    parser.add_argument("--attestation")
    parser.add_argument("--readiness-only", action="store_true")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    inventory = yaml.safe_load(Path(args.inventory).read_text(encoding="utf-8"))
    errors: list[str] = []
    credentials = inventory.get("credentials") if isinstance(inventory, dict) else None
    if inventory.get("schemaVersion") != 1 or not isinstance(credentials, list):
        errors.append("secret rotation inventory has an unsupported structure")
        credentials = []
    expected = {item.get("id") for item in credentials if isinstance(item, dict)}
    if len(expected) != 6 or None in expected:
        errors.append("inventory must define exactly six uniquely identified credentials")
    for item in credentials:
        if not all(meaningful(item.get(key)) for key in ("id", "ownerRole", "vaultPath", "vaultProperty")):
            errors.append(f"inventory entry {item.get('id')} is incomplete")
        if not item.get("consumers"):
            errors.append(f"inventory entry {item.get('id')} has no consumers")

    mode = "readiness"
    if not args.readiness_only:
        mode = "attestation"
        if not args.attestation:
            errors.append("--attestation is required outside readiness-only mode")
        else:
            attestation = json.loads(Path(args.attestation).read_text(encoding="utf-8"))
            if attestation.get("schemaVersion") != 1:
                errors.append("attestation schemaVersion must equal 1")
            actual_entries = attestation.get("credentials") or []
            actual = {item.get("id"): item for item in actual_entries if isinstance(item, dict)}
            if set(actual) != expected:
                errors.append("attestation credential IDs do not match the controlled inventory")
            for credential_id in sorted(expected):
                item = actual.get(credential_id, {})
                for key in ("vaultAuditReference", "deploymentReference", "operator", "approver"):
                    if not meaningful(item.get(key)):
                        errors.append(f"{credential_id}.{key} is missing or still a placeholder")
                for key in ("rotatedAt", "connectivityVerifiedAt", "oldCredentialDisabledAt"):
                    if not isinstance(item.get(key), str) or not TIMESTAMP.fullmatch(item[key]):
                        errors.append(f"{credential_id}.{key} must be a UTC timestamp")
            purge = attestation.get("historyPurge") or {}
            for key in (
                "mirrorCloneUsed", "forbiddenPathScanPassed", "gitleaksScanPassed",
                "ciCachesInvalidated", "oldClonesInvalidated"
            ):
                if purge.get(key) is not True:
                    errors.append(f"historyPurge.{key} must be true")
            for key in ("headBefore", "headAfter"):
                if not isinstance(purge.get(key), str) or not SHA.fullmatch(purge[key]):
                    errors.append(f"historyPurge.{key} must be a full lowercase Git SHA")
            if purge.get("headBefore") == purge.get("headAfter"):
                errors.append("history purge must change the repository HEAD")
            if not meaningful(purge.get("teamRecloneNoticeReference")):
                errors.append("historyPurge.teamRecloneNoticeReference is required")
            signoff = attestation.get("signOff") or {}
            for key in ("repoCoordinator", "secOpsApprover"):
                if not meaningful(signoff.get(key)):
                    errors.append(f"signOff.{key} is required")
            if not isinstance(signoff.get("signedAt"), str) or not TIMESTAMP.fullmatch(signoff["signedAt"]):
                errors.append("signOff.signedAt must be a UTC timestamp")

    report = {
        "schemaVersion": 1,
        "mode": mode,
        "status": "PASS" if not errors else "FAIL",
        "credentialIds": sorted(expected),
        "errors": errors,
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Secret rotation {mode} verification: {report['status']}")
    for error in errors:
        print(f"  ERROR: {error}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
