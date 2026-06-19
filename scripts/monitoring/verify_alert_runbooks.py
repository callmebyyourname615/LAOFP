#!/usr/bin/env python3
"""Validate every Prometheus alert and its repository runbook target."""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

import yaml


def github_slug(text: str) -> str:
    text = text.strip().lower()
    text = re.sub(r"[^\w\- ]", "", text, flags=re.UNICODE)
    return re.sub(r"[ ]+", "-", text)


def markdown_anchors(path: Path) -> set[str]:
    anchors: set[str] = set()
    counts: dict[str, int] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^#{1,6}\s+(.+?)\s*#*\s*$", line)
        if not match:
            continue
        base = github_slug(match.group(1))
        index = counts.get(base, 0)
        counts[base] = index + 1
        anchors.add(base if index == 0 else f"{base}-{index}")
    return anchors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    args = parser.parse_args()
    root = Path(args.root).resolve()

    alerts: dict[str, Path] = {}
    errors: list[str] = []
    rule_files = sorted((root / "monitoring/prometheus").glob("*.yaml"))
    for rule_file in rule_files:
        try:
            documents = list(yaml.safe_load_all(rule_file.read_text(encoding="utf-8")))
        except yaml.YAMLError as exc:
            errors.append(f"{rule_file.relative_to(root)}: invalid YAML: {exc}")
            continue
        for document in documents:
            if not isinstance(document, dict) or document.get("kind") != "PrometheusRule":
                continue
            for group in document.get("spec", {}).get("groups", []):
                for rule in group.get("rules", []):
                    alert = rule.get("alert")
                    if not alert:
                        continue
                    if alert in alerts:
                        errors.append(
                            f"duplicate alert {alert} in {alerts[alert].relative_to(root)} and {rule_file.relative_to(root)}")
                    alerts[alert] = rule_file
                    if not rule.get("expr"):
                        errors.append(f"{alert}: expression is missing")
                    if not rule.get("for"):
                        errors.append(f"{alert}: hold duration is missing")
                    severity = (rule.get("labels") or {}).get("severity")
                    if severity not in {"warning", "critical"}:
                        errors.append(f"{alert}: severity must be warning or critical")
                    annotations = rule.get("annotations") or {}
                    for key in ("summary", "description", "runbook_url"):
                        if not annotations.get(key):
                            errors.append(f"{alert}: annotation {key} is missing")
                    runbook_url = annotations.get("runbook_url")
                    if not runbook_url:
                        continue
                    relative, separator, anchor = runbook_url.partition("#")
                    if relative.startswith(("http://", "https://", "/")) or ".." in Path(relative).parts:
                        errors.append(f"{alert}: runbook_url must be a safe repository-relative path")
                        continue
                    runbook = root / relative
                    if not runbook.is_file():
                        errors.append(f"{alert}: runbook does not exist: {relative}")
                        continue
                    if separator and anchor not in markdown_anchors(runbook):
                        errors.append(f"{alert}: runbook anchor does not exist: {runbook_url}")

    if not alerts:
        errors.append("no Prometheus alert rules found")
    if errors:
        print(f"Alert/runbook verification: FAIL ({len(errors)} issue(s))", file=sys.stderr)
        for error in errors:
            print(f"  - {error}", file=sys.stderr)
        return 1
    print(f"Alert/runbook verification: PASS ({len(alerts)} unique alerts)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
