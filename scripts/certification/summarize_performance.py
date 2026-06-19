#!/usr/bin/env python3
"""Aggregate k6 and capacity snapshots and enforce Phase 54D thresholds."""
from __future__ import annotations

import argparse
import json
import pathlib

import yaml


def prometheus_values(path: pathlib.Path) -> dict[str, float]:
    values: dict[str, float] = {}
    if not path.is_file():
        return values
    for line in path.read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        item = json.loads(line)
        result = item.get("response", {}).get("data", {}).get("result", [])
        if len(result) != 1 or "value" not in result[0]:
            continue
        try:
            values[item["query"]] = float(result[0]["value"][1])
        except (KeyError, TypeError, ValueError):
            continue
    return values


def percentage_growth(before: float, after: float) -> float:
    return round(100.0 * (after - before) / max(abs(before), 1.0), 3)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--results", required=True)
    parser.add_argument("--capacity-before", required=True)
    parser.add_argument("--capacity-after", required=True)
    parser.add_argument("--thresholds", default="config/phase54-thresholds.yaml")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    cfg = yaml.safe_load(pathlib.Path(args.thresholds).read_text(encoding="utf-8"))["performance"]
    root = pathlib.Path(args.results)
    required = ["smoke", "sustained-2k-tps", "burst-10k-tps", "soak-8h"]
    scenarios: dict[str, dict] = {}
    reasons: list[str] = []
    for scenario in required:
        candidates = sorted(root.glob(f"{scenario}-*.report.json"))
        if not candidates:
            reasons.append(f"missing report for {scenario}")
            continue
        data = json.loads(candidates[-1].read_text(encoding="utf-8"))
        checks = {
            "errorRate": data.get("failureRate") is not None and data["failureRate"] <= float(cfg["maximumErrorRate"]),
            "droppedIterations": float(data.get("droppedIterations") or 0) <= float(cfg["maximumDroppedIterations"]),
        }
        scenario_cfg = cfg.get(scenario, {})
        if "minimumRequestCount" in scenario_cfg:
            checks["requestCount"] = float(data.get("requests") or 0) >= float(scenario_cfg["minimumRequestCount"])
        if "minimumRequestRate" in scenario_cfg:
            checks["requestRate"] = float(data.get("requestRate") or 0) >= float(scenario_cfg["minimumRequestRate"])
        if "maximumP95Milliseconds" in scenario_cfg:
            checks["p95"] = float(data.get("p95Ms") or 1e99) <= float(scenario_cfg["maximumP95Milliseconds"])
        if "maximumP99Milliseconds" in scenario_cfg:
            checks["p99"] = float(data.get("p99Ms") or 1e99) <= float(scenario_cfg["maximumP99Milliseconds"])
        passed = bool(data.get("passed")) and all(checks.values())
        if not passed:
            reasons.append(f"threshold failure for {scenario}")
        scenarios[scenario] = {"source": str(candidates[-1]), "metrics": data, "checks": checks, "passed": passed}

    soak_degradation = None
    if "soak-8h" in scenarios and "sustained-2k-tps" in scenarios:
        baseline = float(scenarios["sustained-2k-tps"]["metrics"].get("p95Ms") or 0)
        soak = float(scenarios["soak-8h"]["metrics"].get("p95Ms") or 0)
        soak_degradation = percentage_growth(baseline, soak)
        if soak_degradation > float(cfg["soak-8h"]["maximumP95DegradationPercent"]):
            reasons.append("soak P95 degradation exceeds threshold")

    before = prometheus_values(pathlib.Path(args.capacity_before))
    after = prometheus_values(pathlib.Path(args.capacity_after))
    heap_query = 'sum(jvm_memory_used_bytes{application="switching-api",area="heap"})'
    heap_growth = None
    if heap_query in before and heap_query in after:
        heap_growth = percentage_growth(before[heap_query], after[heap_query])
        if heap_growth > float(cfg["soak-8h"]["maximumJvmHeapGrowthPercent"]):
            reasons.append("JVM heap growth exceeds soak threshold")
    else:
        reasons.append("JVM heap capacity snapshots are missing")

    capacity = {
        "before": before,
        "after": after,
        "jvmHeapGrowthPercent": heap_growth,
        "soakP95DegradationPercent": soak_degradation,
    }
    document = {
        "schemaVersion": 1,
        "scenarios": scenarios,
        "capacity": capacity,
        "passed": not reasons,
        "failureReasons": reasons,
    }
    pathlib.Path(args.output).write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0 if document["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
