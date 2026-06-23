#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

import yaml


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    limits = yaml.safe_load(args.config.read_text(encoding="utf-8"))["backup"]
    data = json.loads(args.attestation.read_text(encoding="utf-8"))
    errors: list[str] = []
    release = data.get("release") or {}
    expected = {
        "reference": args.reference,
        "gitCommit": args.commit,
        "applicationImageDigest": args.application_digest,
    }
    for key, value in expected.items():
        if release.get(key) != value:
            errors.append(f"release.{key} mismatch")
    checks = {
        "backupChecksumVerified": bool(data.get("backupChecksumVerified")),
        "rowCountVerified": bool(data.get("rowCountVerified")),
        "restoreCompleted": bool(data.get("restoreCompleted")),
        "pitrCompleted": bool(data.get("pitrCompleted")),
        "rpoWithinLimit": isinstance(data.get("rpoSeconds"), (int, float)) and data["rpoSeconds"] <= limits["maximumRpoSeconds"],
        "rtoWithinLimit": isinstance(data.get("rtoSeconds"), (int, float)) and data["rtoSeconds"] <= limits["maximumRtoSeconds"],
        "zeroTransactionLoss": data.get("transactionLoss") == limits["maximumTransactionLoss"],
        "evidencePresent": isinstance(data.get("evidence"), list) and bool(data["evidence"]),
        "signed": all(isinstance(data.get(key), str) and data[key].strip() for key in ("signedBy", "signedAt")),
    }
    for name, passed in checks.items():
        if not passed:
            errors.append(f"backup check failed: {name}")
    result = {
        "schemaVersion": 1,
        "release": release,
        "backupId": data.get("backupId"),
        "checks": checks,
        "rpoSeconds": data.get("rpoSeconds"),
        "rtoSeconds": data.get("rtoSeconds"),
        "passed": not errors,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    for error in errors:
        print(f"ERROR: {error}")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
