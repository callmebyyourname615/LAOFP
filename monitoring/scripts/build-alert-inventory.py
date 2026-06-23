#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path

import yaml


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    root = Path(args.root).resolve()

    alerts: list[dict] = []
    for path in sorted((root / "monitoring/prometheus").glob("*.yaml")):
        for document in yaml.safe_load_all(path.read_text(encoding="utf-8")):
            if not isinstance(document, dict):
                continue
            groups = ((document.get("spec") or {}).get("groups") or []) \
                if document.get("kind") == "PrometheusRule" else (document.get("groups") or [])
            for group in groups:
                for rule in group.get("rules") or []:
                    if not rule.get("alert"):
                        continue
                    alerts.append({
                        "name": rule["alert"],
                        "severity": (rule.get("labels") or {}).get("severity"),
                        "hold": rule.get("for"),
                        "runbookUrl": (rule.get("annotations") or {}).get("runbook_url"),
                        "source": path.relative_to(root).as_posix(),
                    })

    names = [item["name"] for item in alerts]
    duplicates = sorted({name for name in names if names.count(name) > 1})
    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "passed": bool(alerts) and not duplicates,
        "alertCount": len(alerts),
        "duplicates": duplicates,
        "alerts": sorted(alerts, key=lambda item: item["name"]),
    }
    output = Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"Alert inventory: {'PASS' if document['passed'] else 'FAIL'} ({len(alerts)} alerts)")
    return 0 if document["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
