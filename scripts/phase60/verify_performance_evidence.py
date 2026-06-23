#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

CRITERIA = {
    "smoke": {"pattern": "smoke-*.json", "failureRateMax": 0.0, "p95MsMax": 200.0, "targetRate": 100.0},
    "sustained-2k-tps": {"pattern": "sustained-2k-tps-*.json", "failureRateMax": 0.001, "p95MsMax": 300.0, "targetRate": 2000.0},
    "sustained-10k-tps": {"pattern": "sustained-10k-tps-*.json", "failureRateMax": 0.001, "p95MsMax": 500.0, "targetRate": 10000.0},
    "burst-20k-tps": {"pattern": "burst-20k-tps-*.json", "failureRateMax": 0.005, "p95MsMax": 750.0, "targetRate": 20000.0},
    "soak-8h": {"pattern": "soak-8h-*.json", "failureRateMax": 0.001, "p95MsMax": 750.0, "targetRate": 5000.0},
}
PLACEHOLDER = re.compile(r"(?i)(replace-me|change_me|todo|tbd)")


def metric(data: dict, name: str, key: str):
    return ((data.get("metrics") or {}).get(name) or {}).get("values", {}).get(key)


def latest(result_dir: Path, pattern: str) -> Path | None:
    files = [path for path in result_dir.rglob(pattern) if not path.name.endswith(".report.json")]
    return max(files, key=lambda path: path.stat().st_mtime) if files else None


def validate_attestation(path: Path) -> tuple[list[str], dict]:
    errors: list[str] = []
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("schemaVersion") != 1:
        errors.append("performance attestation schemaVersion must equal 1")
    for key in ("noMemoryLeak", "noDatabaseConnectionExhaustion", "noSustainedKafkaLagGrowth"):
        if document.get(key) is not True:
            errors.append(f"{key} must be true")
    gc_pause = document.get("maximumGcPauseMs")
    if not isinstance(gc_pause, (int, float)) or gc_pause >= 100:
        errors.append("maximumGcPauseMs must be below 100")
    for key in ("operator", "performanceLead", "capacityPlanReference", "signedAt"):
        value = document.get(key)
        if not isinstance(value, str) or not value.strip() or PLACEHOLDER.search(value):
            errors.append(f"{key} is missing or still a placeholder")
    return errors, document


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result-dir", type=Path, required=True)
    parser.add_argument("--settlement-summary", type=Path)
    parser.add_argument("--attestation", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    errors: list[str] = []
    scenarios: dict[str, dict] = {}
    for name, criterion in CRITERIA.items():
        path = latest(args.result_dir, criterion["pattern"])
        if path is None:
            errors.append(f"missing k6 summary for {name}")
            continue
        data = json.loads(path.read_text(encoding="utf-8"))
        failure_rate = metric(data, "http_req_failed", "rate")
        p95 = metric(data, "http_req_duration", "p(95)")
        dropped = metric(data, "dropped_iterations", "count") or 0
        checks_rate = metric(data, "checks", "rate")
        request_rate = metric(data, "http_reqs", "rate")
        scenario_errors: list[str] = []
        if failure_rate is None or failure_rate > criterion["failureRateMax"]:
            scenario_errors.append(f"failure rate {failure_rate} exceeds {criterion['failureRateMax']}")
        if p95 is None or p95 >= criterion["p95MsMax"]:
            scenario_errors.append(f"p95 {p95}ms is not below {criterion['p95MsMax']}ms")
        if dropped != 0:
            scenario_errors.append(f"dropped iterations is {dropped}, expected 0")
        if checks_rate is None or checks_rate < 1 - criterion["failureRateMax"]:
            scenario_errors.append(f"checks rate {checks_rate} is below the scenario threshold")
        # Request rate is recorded for capacity review. Ramping scenarios do not hold the peak for the whole run,
        # so it is not used as a standalone pass/fail criterion.
        scenarios[name] = {
            "file": path.as_posix(),
            "passed": not scenario_errors,
            "failureRate": failure_rate,
            "p95Ms": p95,
            "droppedIterations": dropped,
            "checksRate": checks_rate,
            "requestRate": request_rate,
            "targetRate": criterion["targetRate"],
            "errors": scenario_errors,
        }
        errors.extend(f"{name}: {error}" for error in scenario_errors)

    settlement = None
    if args.settlement_summary:
        settlement = json.loads(args.settlement_summary.read_text(encoding="utf-8"))
        if settlement.get("transactions") != 500000:
            errors.append("settlement benchmark did not use exactly 500000 transactions")
        if settlement.get("passed") is not True:
            errors.append("settlement 500k benchmark failed")
        if int(settlement.get("balanceMismatchCount", 1)) != 0:
            errors.append("settlement benchmark has a balance mismatch")
    else:
        errors.append("settlement summary is required")

    attestation = None
    if args.attestation:
        attestation_errors, attestation = validate_attestation(args.attestation)
        errors.extend(attestation_errors)
    else:
        errors.append("performance/capacity attestation is required")

    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "passed": not errors,
        "scenarios": scenarios,
        "settlement": settlement,
        "capacityAttestation": attestation,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 60H performance evidence: {'PASS' if document['passed'] else 'FAIL'}")
    for error in errors:
        print(f"  ERROR: {error}")
    return 0 if document["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
