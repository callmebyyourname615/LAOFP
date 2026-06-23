#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--results-root", required=True)
parser.add_argument("--output", required=True)
parser.add_argument("--synthetic", action="store_true")
args = parser.parse_args()

root = Path(args.results_root)
limits = {
    "smoke": (200, 0.0, False),
    "sustained-2k-tps": (300, 0.001, True),
    "sustained-10k-tps": (500, 0.001, True),
    "burst-20k-tps": (500, 0.005, True),
    "soak-8h": (500, 0.001, True),
}
rows: list[dict[str, object]] = []
for scenario, (p95_limit, error_limit, error_strict) in limits.items():
    matches = sorted(root.rglob(f"{scenario}-*.json"))
    matches = [path for path in matches if not path.name.endswith(".metadata.json")]
    if not matches:
        rows.append({"scenario": scenario, "present": False, "passed": False})
        continue
    path = matches[-1]
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        rows.append({"scenario": scenario, "present": True, "passed": False, "error": "invalid_json"})
        continue
    metrics = data.get("metrics", {})
    duration = metrics.get("http_req_duration", {}).get("values", {})
    rate = metrics.get("http_req_failed", {}).get("values", {})
    p95 = duration.get("p(95)", duration.get("p95"))
    error_rate = rate.get("rate")
    passed = (
        isinstance(p95, (int, float))
        and isinstance(error_rate, (int, float))
        and p95 < p95_limit
        and (error_rate < error_limit if error_strict else error_rate <= error_limit)
    )
    rows.append(
        {
            "scenario": scenario,
            "present": True,
            "p95Ms": p95,
            "errorRate": error_rate,
            "p95Limit": p95_limit,
            "errorLimit": error_limit,
            "passed": passed,
            "source": str(path),
        }
    )

settlement_rows: list[dict[str, object]] = []
for path in sorted(root.rglob("summary.json")):
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        if int(data.get("transactions", 0)) >= 500000:
            passed = (
                bool(data.get("passed"))
                and int(data.get("durationSeconds", 999999)) <= 1800
                and int(data.get("transactionLossCount", data.get("missingTransactionCount", 0))) == 0
                and int(data.get("balanceMismatchCount", 0)) == 0
            )
            settlement_rows.append(
                {"scenario": "settlement-500k", "present": True, "passed": passed, "source": str(path)}
            )
    except Exception:
        continue
if not settlement_rows:
    settlement_rows = [{"scenario": "settlement-500k", "present": False, "passed": False}]
rows.extend(settlement_rows[-1:])

payload = {
    "schemaVersion": 1,
    "scenarios": rows,
    "passed": all(bool(row["passed"]) for row in rows) and not args.synthetic,
    "synthetic": args.synthetic,
}
out = Path(args.output)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(payload, sort_keys=True))
raise SystemExit(0 if payload["passed"] else 1)
