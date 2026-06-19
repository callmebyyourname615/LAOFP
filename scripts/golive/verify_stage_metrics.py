#!/usr/bin/env python3
"""Fail closed when a Phase 55 canary/cutover Prometheus metric breaches its threshold."""
from __future__ import annotations

import argparse
import datetime as dt
import json
import math
import os
import pathlib
import sys
import urllib.parse
import urllib.request

try:
    import yaml
except ImportError as exc:  # pragma: no cover - deployment preflight handles this
    raise SystemExit("PyYAML is required") from exc


def utc_now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")


def query(base: str, expression: str, timeout: float) -> float:
    url = base.rstrip("/") + "/api/v1/query?" + urllib.parse.urlencode({"query": expression})
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        payload = json.load(response)
    if payload.get("status") != "success":
        raise RuntimeError("Prometheus query did not return success")
    result = payload.get("data", {}).get("result", [])
    if len(result) != 1:
        raise RuntimeError(f"Prometheus query expected one result but returned {len(result)}")
    value = float(result[0]["value"][1])
    if not math.isfinite(value):
        raise RuntimeError("Prometheus query returned a non-finite value")
    return value


def evaluate(value: float, comparison: str, threshold: float) -> bool:
    if comparison == "maximum":
        return value <= threshold
    if comparison == "minimum":
        return value >= threshold
    raise ValueError(f"unsupported comparison: {comparison}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prometheus-url", required=True)
    parser.add_argument("--track", required=True, choices=("canary", "stable"))
    parser.add_argument("--stage", required=True)
    parser.add_argument("--config", default="config/phase55-stage-metrics.yaml")
    parser.add_argument("--output", required=True)
    parser.add_argument("--window")
    args = parser.parse_args()

    config_path = pathlib.Path(args.config)
    config = yaml.safe_load(config_path.read_text(encoding="utf-8"))
    if config.get("schemaVersion") != 1:
        raise SystemExit("unsupported stage metrics schemaVersion")
    window = args.window or str(config.get("window", "5m"))
    timeout = float(config.get("queryTimeoutSeconds", 15))
    checks: list[dict[str, object]] = []
    failed = False

    for item in config.get("metrics", []):
        metric_id = str(item["id"])
        expression = str(item["expression"])
        override = item.get("queryEnvironmentOverride")
        if override and os.environ.get(str(override)):
            expression = os.environ[str(override)]
        expression = expression.format(track=args.track, window=window)
        comparison = str(item["comparison"])
        threshold = float(item["threshold"])
        record: dict[str, object] = {
            "id": metric_id,
            "description": str(item.get("description", "")),
            "comparison": comparison,
            "threshold": threshold,
        }
        try:
            value = query(args.prometheus_url, expression, timeout)
            passed = evaluate(value, comparison, threshold)
            record.update({"value": value, "status": "PASS" if passed else "FAIL"})
            failed = failed or not passed
        except Exception as exc:
            # Deliberately omit the full expression and endpoint from evidence/logs.
            record.update({"status": "FAIL", "errorType": type(exc).__name__})
            failed = True
        checks.append(record)

    output = {
        "schemaVersion": 1,
        "generatedAt": utc_now(),
        "stage": args.stage,
        "releaseTrack": args.track,
        "window": window,
        "status": "FAIL" if failed else "PASS",
        "checks": checks,
    }
    out = pathlib.Path(args.output)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"stage": args.stage, "track": args.track, "status": output["status"]}, sort_keys=True))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
