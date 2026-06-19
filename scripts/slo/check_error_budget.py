#!/usr/bin/env python3
"""Production release gate based on Prometheus error-budget remaining."""
from __future__ import annotations
import argparse
import json
import math
import urllib.parse
import urllib.request


def fetch(url: str, expression: str, timeout: float) -> float:
    endpoint = url.rstrip('/') + '/api/v1/query?' + urllib.parse.urlencode({'query': expression})
    with urllib.request.urlopen(endpoint, timeout=timeout) as response:
        data = json.load(response)
    result = data.get('data', {}).get('result', [])
    if data.get('status') != 'success' or len(result) != 1:
        raise SystemExit(f'expected one Prometheus result, received: {data}')
    value = float(result[0]['value'][1])
    if not math.isfinite(value):
        raise SystemExit('non-finite error-budget result')
    return value


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument('--prometheus-url', required=True)
    p.add_argument('--minimum-remaining', type=float, default=0.20)
    p.add_argument('--change-class', choices=['standard', 'reliability', 'security', 'emergency'], default='standard')
    p.add_argument('--approved-exception', action='store_true')
    p.add_argument('--timeout', type=float, default=10.0)
    args = p.parse_args()
    if not 0 <= args.minimum_remaining <= 1:
        raise SystemExit('minimum remaining must be between 0 and 1')
    remaining = fetch(args.prometheus_url, 'switching:slo_error_budget_remaining:ratio30d', args.timeout)
    result = {'remaining': remaining, 'minimum': args.minimum_remaining, 'changeClass': args.change_class}
    print(json.dumps(result, sort_keys=True))
    exempt = args.change_class in {'reliability', 'security', 'emergency'} and args.approved_exception
    if remaining < args.minimum_remaining and not exempt:
        raise SystemExit('release blocked by error-budget policy')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
