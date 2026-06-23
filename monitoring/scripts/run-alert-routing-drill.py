#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import ssl
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path


def request(url: str, method: str = "GET", body: object | None = None,
            token: str | None = None) -> tuple[int, bytes]:
    headers = {"Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10, context=ssl.create_default_context()) as response:
            return response.status, response.read(1024 * 1024)
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read(1024 * 1024)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--alertmanager-url", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--bearer-token-file", type=Path)
    args = parser.parse_args()

    token = args.bearer_token_file.read_text(encoding="utf-8").strip() if args.bearer_token_file else None
    base = args.alertmanager_url.rstrip("/")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    now = datetime.now(timezone.utc)
    labels = {
        "alertname": "SwitchingPhase60SyntheticRoute",
        "severity": "warning",
        "service": "switching-api",
        "phase": "60I",
        "phase60_run_id": args.run_id,
    }
    annotations = {
        "summary": "Phase 60 synthetic Alertmanager routing drill",
        "description": "Synthetic UAT alert used to verify routing and acknowledgement evidence.",
        "runbook_url": "docs/runbooks/ALERT_DELIVERY_DRILL.md",
    }
    firing = [{
        "labels": labels,
        "annotations": annotations,
        "startsAt": now.isoformat().replace("+00:00", "Z"),
        "endsAt": (now + timedelta(minutes=10)).isoformat().replace("+00:00", "Z"),
        "generatorURL": "phase60://alert-routing-drill",
    }]
    status, body = request(base + "/api/v2/alerts", "POST", firing, token)
    (args.output_dir / "post-firing-response.txt").write_bytes(body)
    if status not in {200, 202}:
        raise SystemExit(f"Alertmanager rejected synthetic alert with HTTP {status}")

    query = urllib.parse.urlencode({"filter": f'phase60_run_id="{args.run_id}"'})
    status, body = request(base + "/api/v2/alerts?" + query, token=token)
    (args.output_dir / "active-alerts.json").write_bytes(body)
    if status != 200:
        raise SystemExit(f"Unable to query Alertmanager alerts: HTTP {status}")
    active = json.loads(body)
    if not any((item.get("labels") or {}).get("phase60_run_id") == args.run_id for item in active):
        raise SystemExit("Synthetic alert was not observable through Alertmanager API")

    resolved = [{
        **firing[0],
        "endsAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }]
    status, body = request(base + "/api/v2/alerts", "POST", resolved, token)
    (args.output_dir / "post-resolve-response.txt").write_bytes(body)
    if status not in {200, 202}:
        raise SystemExit(f"Alertmanager rejected resolution update with HTTP {status}")

    result = {
        "schemaVersion": 1,
        "runId": args.run_id,
        "alertName": labels["alertname"],
        "posted": True,
        "observable": True,
        "resolutionPosted": True,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "receiverDelivery": "AWAITING_OPERATOR_ATTESTATION"
    }
    (args.output_dir / "alert-routing-result.json").write_text(
        json.dumps(result, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print("Synthetic Alertmanager route drill: PASS (receiver delivery requires operator attestation)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
