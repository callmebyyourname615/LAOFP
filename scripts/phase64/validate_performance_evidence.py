#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import yaml


def metric(summary: dict[str, Any], name: str, key: str) -> float | None:
    value = (summary.get("metrics", {}).get(name, {}).get("values", {}) or {}).get(key)
    return float(value) if value is not None else None


def latest_summary(root: Path, prefix: str) -> Path | None:
    candidates = [
        path for path in root.rglob(f"{prefix}*.json")
        if not path.name.endswith(".report.json") and path.name not in {"manifest.json", "step-results.jsonl"}
    ]
    return sorted(candidates)[-1] if candidates else None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--evidence-root", type=Path, required=True)
    parser.add_argument("--reference", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--application-digest", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    config = yaml.safe_load(args.config.read_text(encoding="utf-8"))["performance"]["scenarios"]
    errors: list[str] = []
    rows: dict[str, Any] = {}
    for name, threshold in config.items():
        source = latest_summary(args.evidence_root, str(threshold["filePrefix"]))
        if source is None:
            errors.append(f"missing k6 summary for {name}")
            rows[name] = {"status": "MISSING"}
            continue
        try:
            summary = json.loads(source.read_text(encoding="utf-8"))
        except Exception as exc:
            errors.append(f"invalid k6 summary for {name}: {exc}")
            rows[name] = {"status": "INVALID", "source": str(source)}
            continue
        request_rate = metric(summary, "http_reqs", "rate")
        p95 = metric(summary, "http_req_duration", "p(95)")
        failure_rate = metric(summary, "http_req_failed", "rate")
        dropped = metric(summary, "dropped_iterations", "count") or 0.0
        checks = [
            (request_rate is not None and request_rate >= float(threshold["minimumRequestRate"]), "request rate"),
            (p95 is not None and p95 < float(threshold["maximumP95Milliseconds"]), "p95 latency"),
            (failure_rate is not None and failure_rate <= float(threshold["maximumFailureRate"]), "failure rate"),
            (dropped <= float(threshold["maximumDroppedIterations"]), "dropped iterations"),
        ]
        failed_checks = [label for passed, label in checks if not passed]
        if failed_checks:
            errors.append(f"{name} threshold failure: {', '.join(failed_checks)}")
        rows[name] = {
            "status": "PASS" if not failed_checks else "FAIL",
            "source": str(source.resolve()),
            "requestRate": request_rate,
            "p95Milliseconds": p95,
            "failureRate": failure_rate,
            "droppedIterations": dropped,
            "thresholds": threshold,
            "failedChecks": failed_checks,
        }
    output = {
        "schemaVersion": 1,
        "release": {
            "reference": args.reference,
            "gitCommit": args.commit,
            "applicationImageDigest": args.application_digest,
        },
        "scenarios": rows,
        "passed": not errors,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    for error in errors:
        print(f"ERROR: {error}")
    print(f"Performance evidence: {'PASS' if not errors else 'FAIL'} ({len(rows)} scenarios)")
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
