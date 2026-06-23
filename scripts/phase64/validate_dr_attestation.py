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
    limits = yaml.safe_load(args.config.read_text(encoding="utf-8"))["dr"]
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
    scenario_results = {}
    scenarios = data.get("scenarios") or {}
    for name in limits["requiredScenarios"]:
        item = scenarios.get(name) or {}
        passed = (
            item.get("status") == "PASS"
            and isinstance(item.get("recoverySeconds"), (int, float))
            and 0 <= item["recoverySeconds"] <= limits["maximumRecoverySeconds"]
            and isinstance(item.get("evidence"), str)
            and bool(item["evidence"].strip())
        )
        scenario_results[name] = {"passed": passed, **item}
        if not passed:
            errors.append(f"DR scenario failed or incomplete: {name}")
    integrity = {
        "committedTransactionLoss": data.get("committedTransactionLoss") == limits["maximumCommittedTransactionLoss"],
        "duplicateReplayCount": data.get("duplicateReplayCount") == limits["maximumDuplicateReplayCount"],
        "outboxReplayIdempotent": data.get("outboxReplayIdempotent") is limits["requireOutboxReplayIdempotent"],
        "postRecoveryHealth": data.get("postRecoveryHealth") == limits["requiredPostRecoveryHealth"],
        "signed": all(isinstance(data.get(key), str) and data[key].strip() for key in ("signedBy", "signedAt")),
    }
    for name, passed in integrity.items():
        if not passed:
            errors.append(f"DR integrity check failed: {name}")
    result = {
        "schemaVersion": 1,
        "release": release,
        "scenarios": scenario_results,
        "integrity": integrity,
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
