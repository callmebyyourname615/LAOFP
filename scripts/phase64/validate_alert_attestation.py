#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml


def repository_alerts(root: Path, rule_directory: str) -> set[str]:
    alerts: set[str] = set()
    for path in sorted((root / rule_directory).glob("*.yaml")):
        for document in yaml.safe_load_all(path.read_text(encoding="utf-8")):
            if not isinstance(document, dict) or document.get("kind") != "PrometheusRule":
                continue
            for group in document.get("spec", {}).get("groups", []):
                for rule in group.get("rules", []):
                    if rule.get("alert"):
                        alerts.add(str(rule["alert"]))
    return alerts


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))["alerts"]
    data = json.loads(args.attestation.read_text(encoding="utf-8"))
    errors: list[str] = []
    release = data.get("release") or {}
    expected_release = {
        "reference": args.reference,
        "gitCommit": args.commit,
        "applicationImageDigest": args.application_digest,
    }
    for key, value in expected_release.items():
        if release.get(key) != value:
            errors.append(f"release.{key} mismatch")
    expected = repository_alerts(args.root, config["ruleDirectory"])
    supplied_rows = data.get("alerts") or []
    supplied: dict[str, dict] = {}
    for row in supplied_rows:
        name = row.get("name") if isinstance(row, dict) else None
        if not isinstance(name, str) or not name:
            errors.append("alert evidence contains a row without a name")
            continue
        if name in supplied:
            errors.append(f"duplicate alert evidence: {name}")
            continue
        supplied[name] = row
    missing = sorted(expected - set(supplied))
    unexpected = sorted(set(supplied) - expected)
    if missing:
        errors.append(f"missing evidence for {len(missing)} current alerts")
    if unexpected:
        errors.append(f"attestation contains {len(unexpected)} unknown alerts")
    status_rows = {}
    for name in sorted(expected):
        row = supplied.get(name) or {}
        checks = {
            "fired": row.get("fired") is True,
            "routed": row.get("routed") is True,
            "resolved": row.get("resolved") is True,
            "receiver": isinstance(row.get("receiver"), str) and bool(row["receiver"].strip()),
            "evidence": isinstance(row.get("evidence"), str) and bool(row["evidence"].strip()),
        }
        status_rows[name] = {"passed": all(checks.values()), "checks": checks}
        if not all(checks.values()):
            errors.append(f"alert evidence incomplete: {name}")
    signed = all(isinstance(data.get(key), str) and data[key].strip() for key in ("signedBy", "signedAt"))
    if not signed:
        errors.append("alert attestation is not signed")
    result = {
        "schemaVersion": 1,
        "release": release,
        "expectedAlertCount": len(expected),
        "suppliedAlertCount": len(supplied),
        "missingAlerts": missing,
        "unexpectedAlerts": unexpected,
        "alerts": status_rows,
        "signed": signed,
        "passed": not errors,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    for error in errors:
        print(f"ERROR: {error}")
    print(f"Alert evidence: {'PASS' if not errors else 'FAIL'} ({len(expected)} current alerts)")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
