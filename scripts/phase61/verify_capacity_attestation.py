#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import re
from datetime import datetime, timezone
from pathlib import Path

CRITERIA = {
    "smoke": {"pattern": "smoke-*.json", "failureRateMax": 0.0, "p95MsMax": 200.0, "minimumRate": 95.0},
    "sustained-2k-tps": {"pattern": "sustained-2k-tps-*.json", "failureRateMax": 0.001, "p95MsMax": 300.0, "minimumRate": 1900.0},
    "sustained-10k-tps": {"pattern": "sustained-10k-tps-*.json", "failureRateMax": 0.001, "p95MsMax": 500.0, "minimumRate": 9500.0},
    "burst-20k-tps": {"pattern": "burst-20k-tps-*.json", "failureRateMax": 0.005, "p95MsMax": 750.0, "minimumRate": 18000.0},
    "soak-8h": {"pattern": "soak-8h-*.json", "failureRateMax": 0.001, "p95MsMax": 750.0, "minimumRate": 4750.0},
}
PLACEHOLDER = re.compile(r"(?i)(replace-me|change_me|todo|tbd)")


def latest(root: Path, pattern: str) -> Path | None:
    files = [path for path in root.rglob(pattern) if not path.name.endswith(".report.json")]
    return max(files, key=lambda path: path.stat().st_mtime) if files else None


def metric(document: dict, metric_name: str, value_name: str):
    return (((document.get("metrics") or {}).get(metric_name) or {}).get("values") or {}).get(value_name)


def require_nonempty_directory(path: Path, label: str, errors: list[str]) -> list[str]:
    if not path.is_dir():
        errors.append(f"{label} directory is missing: {path}")
        return []
    files = sorted(item.name for item in path.iterdir() if item.is_file() and item.stat().st_size > 0)
    if not files:
        errors.append(f"{label} directory contains no non-empty evidence files")
    if "metadata.json" not in files:
        errors.append(f"{label} metadata.json is missing")
    return files


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result-dir", type=Path, required=True)
    parser.add_argument("--before-dir", type=Path, required=True)
    parser.add_argument("--after-dir", type=Path, required=True)
    parser.add_argument("--attestation", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    errors: list[str] = []
    scenarios: dict[str, dict] = {}
    for name, criterion in CRITERIA.items():
        path = latest(args.result_dir, criterion["pattern"])
        if path is None:
            errors.append(f"missing k6 summary for {name}")
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"{name}: invalid k6 summary: {exc}")
            continue
        failure_rate = metric(data, "http_req_failed", "rate")
        p95 = metric(data, "http_req_duration", "p(95)")
        dropped = metric(data, "dropped_iterations", "count")
        checks_rate = metric(data, "checks", "rate")
        request_rate = metric(data, "http_reqs", "rate")
        scenario_errors: list[str] = []
        if failure_rate is None or float(failure_rate) > criterion["failureRateMax"]:
            scenario_errors.append(f"failure rate {failure_rate} exceeds {criterion['failureRateMax']}")
        if p95 is None or float(p95) >= criterion["p95MsMax"]:
            scenario_errors.append(f"p95 {p95}ms is not below {criterion['p95MsMax']}ms")
        if dropped is None or int(dropped) != 0:
            scenario_errors.append(f"dropped iterations is {dropped}, expected 0")
        if checks_rate is None or float(checks_rate) < 1 - criterion["failureRateMax"]:
            scenario_errors.append(f"checks rate {checks_rate} is below the required threshold")
        if request_rate is None or float(request_rate) < criterion["minimumRate"]:
            scenario_errors.append(f"observed request rate {request_rate} is below {criterion['minimumRate']} TPS")
        scenarios[name] = {
            "file": path.as_posix(),
            "passed": not scenario_errors,
            "failureRate": failure_rate,
            "p95Ms": p95,
            "droppedIterations": dropped,
            "checksRate": checks_rate,
            "requestRate": request_rate,
            "minimumRate": criterion["minimumRate"],
            "errors": scenario_errors,
        }
        errors.extend(f"{name}: {item}" for item in scenario_errors)

    before_files = require_nonempty_directory(args.before_dir, "capacity-before", errors)
    after_files = require_nonempty_directory(args.after_dir, "capacity-after", errors)

    try:
        attestation = json.loads(args.attestation.read_text(encoding="utf-8"))
    except Exception as exc:
        attestation = {}
        errors.append(f"invalid capacity attestation: {exc}")
    if attestation.get("schemaVersion") != 1:
        errors.append("capacity attestation schemaVersion must equal 1")
    for key in (
        "noMemoryLeak", "noDatabaseConnectionExhaustion", "noSustainedKafkaLagGrowth",
        "hpaStable", "noPodRestartStorm", "noReplicaLagBreach", "noDiskSaturation",
        "noThreadPoolExhaustion",
    ):
        if attestation.get(key) is not True:
            errors.append(f"{key} must be true")
    numeric_limits = {
        "maximumGcPauseMs": 100.0,
        "maximumCpuPercent": 85.0,
        "maximumMemoryPercent": 85.0,
    }
    for key, upper_bound in numeric_limits.items():
        try:
            value = float(attestation[key])
        except Exception:
            errors.append(f"{key} must be numeric")
            continue
        violates = value >= upper_bound if key == "maximumGcPauseMs" else value > upper_bound
        if violates:
            errors.append(f"{key} exceeds the approved limit {upper_bound}")
    for key in ("operator", "performanceLead", "capacityPlanReference", "signedAt"):
        value = attestation.get(key)
        if not isinstance(value, str) or not value.strip() or PLACEHOLDER.search(value):
            errors.append(f"{key} is missing or still a placeholder")

    report = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "passed": not errors,
        "scenarios": scenarios,
        "capacityBeforeFiles": before_files,
        "capacityAfterFiles": after_files,
        "capacityAttestation": attestation,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Phase 61G capacity certification: {'PASS' if not errors else 'FAIL'}")
    for error in errors:
        print("  ERROR:", error)
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
