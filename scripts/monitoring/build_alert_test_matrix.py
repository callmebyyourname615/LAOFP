#!/usr/bin/env python3
"""Generate a deterministic UAT alert-firing matrix from PrometheusRule files."""
from __future__ import annotations

import argparse
from pathlib import Path
import yaml


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", default="monitoring/ALERT_TEST_MATRIX.md")
    args = parser.parse_args()
    root = Path(args.root).resolve()
    rows = []
    for path in sorted((root / "monitoring/prometheus").glob("*.yaml")):
        for document in yaml.safe_load_all(path.read_text(encoding="utf-8")):
            if not isinstance(document, dict) or document.get("kind") != "PrometheusRule":
                continue
            for group in document.get("spec", {}).get("groups", []):
                for rule in group.get("rules", []):
                    if "alert" not in rule:
                        continue
                    annotations = rule.get("annotations") or {}
                    rows.append((
                        rule["alert"],
                        (rule.get("labels") or {}).get("severity", ""),
                        str(rule.get("for", "")),
                        annotations.get("runbook_url", ""),
                        path.relative_to(root).as_posix(),
                    ))
    rows.sort()
    output = root / args.output
    output.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# UAT Alert Firing and Routing Matrix",
        "",
        "For every row, trigger the condition in an isolated UAT namespace, capture Pending → Firing → Resolved, verify the configured receiver and open the linked runbook. Never inject a fault into production solely to test an alert.",
        "",
        "| Alert | Severity | Hold | Rule file | Runbook | Fired | Routed | Resolved | Evidence |",
        "|---|---|---:|---|---|---|---|---|---|",
    ]
    for alert, severity, hold, runbook, rule_file in rows:
        lines.append(f"| `{alert}` | {severity} | {hold} | `{rule_file}` | `{runbook}` | ☐ | ☐ | ☐ | |")
    lines.extend(["", f"Total alerts: **{len(rows)}**", ""])
    output.write_text("\n".join(lines), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
