#!/usr/bin/env python3
"""Fail a canary stage when Prometheus error or latency thresholds are exceeded."""
from __future__ import annotations
import argparse
import json
import math
import urllib.parse
import urllib.request


def query(base: str, expression: str, timeout: float) -> float:
    url = base.rstrip("/") + "/api/v1/query?" + urllib.parse.urlencode({"query": expression})
    with urllib.request.urlopen(url, timeout=timeout) as response:
        payload = json.load(response)
    if payload.get("status") != "success":
        raise RuntimeError(f"Prometheus query failed: {payload}")
    result = payload.get("data", {}).get("result", [])
    if not result:
        raise RuntimeError(f"Prometheus returned no data for: {expression}")
    value = float(result[0]["value"][1])
    if not math.isfinite(value):
        raise RuntimeError(f"Prometheus returned non-finite value: {value}")
    return value


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--prometheus-url", required=True)
    p.add_argument("--window", default="5m")
    p.add_argument("--max-error-ratio", type=float, default=0.01)
    p.add_argument("--max-p95-seconds", type=float, default=1.0)
    p.add_argument("--timeout", type=float, default=10.0)
    args = p.parse_args()
    window = args.window
    errors = query(args.prometheus_url, f'sum(rate(http_server_requests_seconds_count{{application="switching-api",release_track="canary",status=~"5.."}}[{window}])) / clamp_min(sum(rate(http_server_requests_seconds_count{{application="switching-api",release_track="canary"}}[{window}])), 0.001)', args.timeout)
    p95 = query(args.prometheus_url, f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{application="switching-api",release_track="canary"}}[{window}])))', args.timeout)
    print(json.dumps({"errorRatio": errors, "p95Seconds": p95}, sort_keys=True))
    if errors > args.max_error_ratio:
        raise SystemExit(f"canary error ratio {errors:.6f} exceeds {args.max_error_ratio:.6f}")
    if p95 > args.max_p95_seconds:
        raise SystemExit(f"canary p95 {p95:.3f}s exceeds {args.max_p95_seconds:.3f}s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
