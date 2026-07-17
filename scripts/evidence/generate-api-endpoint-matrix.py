#!/usr/bin/env python3
"""Create a complete endpoint evidence matrix from API_ENDPOINTS.txt.

The inventory is generated from controller annotations.  This script turns it
into a reviewable test register; it intentionally does not call UAT so a test
owner must explicitly decide which mutation scenarios are safe to execute.
"""

from __future__ import annotations

import argparse
import csv
import json
import re
from collections import Counter, defaultdict
from datetime import UTC, datetime
from pathlib import Path


LINE = re.compile(r"^(GET|POST|PUT|PATCH|DELETE)\s+(/\S+)\s+(.+)$")
MUTATING_METHODS = {"POST", "PUT", "PATCH", "DELETE"}


def domain_for(path: str) -> str:
    parts = [part for part in path.split("/") if part]
    if not parts:
        return "root"
    if parts[0] == "api" and len(parts) > 1:
        return parts[1]
    if parts[0] == "v1" and len(parts) > 1:
        return f"v1-{parts[1]}"
    return parts[0]


def risk_for(method: str, path: str) -> str:
    if method not in MUTATING_METHODS:
        return "read"
    sensitive = ("auth", "certificates", "credentials", "break-glass", "config-changes",
                 "settlement", "disputes", "transfers", "outbox", "legal-holds")
    return "high" if any(token in path for token in sensitive) else "standard"


def evidence_type(method: str, risk: str) -> str:
    if method not in MUTATING_METHODS:
        return "authenticated read + unauthorized negative"
    if risk == "high":
        return "happy path + authorization + validation + audit + rollback/cleanup"
    return "happy path + authorization + validation + cleanup"


def parse_inventory(path: Path, verified: dict[str, dict[str, str]]) -> list[dict[str, str]]:
    endpoints: list[dict[str, str]] = []
    for raw in path.read_text(encoding="utf-8").splitlines():
        match = LINE.match(raw.strip())
        if not match:
            continue
        method, endpoint_path, handler = match.groups()
        risk = risk_for(method, endpoint_path)
        key = f"{method} {endpoint_path}"
        verification = verified.get(key, {})
        endpoints.append({
            "method": method,
            "path": endpoint_path,
            "handler": handler,
            "domain": domain_for(endpoint_path),
            "risk": risk,
            "evidence_required": evidence_type(method, risk),
            "status": verification.get("status", "NOT_TESTED"),
            "evidence_path": verification.get("evidence_path", ""),
            "notes": verification.get("notes", ""),
        })
    return endpoints


def write_csv(path: Path, endpoints: list[dict[str, str]]) -> None:
    fields = list(endpoints[0]) if endpoints else []
    with path.open("w", newline="", encoding="utf-8") as output:
        writer = csv.DictWriter(output, fieldnames=fields)
        writer.writeheader()
        writer.writerows(endpoints)


def write_markdown(path: Path, source: Path, endpoints: list[dict[str, str]]) -> None:
    domains = defaultdict(list)
    for endpoint in endpoints:
        domains[endpoint["domain"]].append(endpoint)
    method_count = Counter(endpoint["method"] for endpoint in endpoints)
    risk_count = Counter(endpoint["risk"] for endpoint in endpoints)
    passed_count = sum(endpoint["status"] == "PASS" for endpoint in endpoints)

    lines = [
        "# API Endpoint Evidence Matrix",
        "",
        f"Generated: {datetime.now(UTC).strftime('%Y-%m-%dT%H:%M:%SZ')}",
        f"Inventory source: `{source}`",
        f"Total endpoints: {len(endpoints)}",
        "",
        "## Test Rules",
        "",
        "- Every endpoint needs an authenticated expected-result record or an explicit N/A decision.",
        "- Every mutating endpoint needs a validation/negative record and post-test cleanup where applicable.",
        "- High-risk mutations also require authorization, audit, and rollback/cleanup evidence.",
        "- Never store passwords, JWTs, refresh tokens, private keys, or break-glass tokens in evidence.",
        "",
        "## Summary",
        "",
        f"- Methods: {', '.join(f'`{name}` {method_count[name]}' for name in sorted(method_count))}",
        f"- Risk: {', '.join(f'`{name}` {risk_count[name]}' for name in sorted(risk_count))}",
        f"- Domains: {len(domains)}",
        f"- Verified on UAT: {passed_count}/{len(endpoints)}",
        "",
    ]
    for domain in sorted(domains):
        items = domains[domain]
        lines.extend([
            f"## {domain} ({len(items)})",
            "",
            "| Done | Method | Path | Risk | Required Evidence | Evidence |",
            "| --- | --- | --- | --- | --- | --- |",
        ])
        for item in items:
            done = "[x]" if item["status"] == "PASS" else "[ ]"
            evidence = f"`{item['evidence_path']}`" if item["evidence_path"] else "-"
            lines.append(
                f"| {done} | {item['method']} | `{item['path']}` | {item['risk']} | "
                f"{item['evidence_required']} | {evidence} |"
            )
        lines.append("")
    path.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inventory", default="API_ENDPOINTS.txt")
    parser.add_argument("--output-dir", default="runtime-evidence/api-endpoint-matrix")
    parser.add_argument("--status-file", default="scripts/evidence/api-endpoint-status.json")
    args = parser.parse_args()

    inventory = Path(args.inventory).resolve()
    output_dir = Path(args.output_dir).resolve()
    status_file = Path(args.status_file).resolve()
    verified = json.loads(status_file.read_text(encoding="utf-8")) if status_file.exists() else {}
    endpoints = parse_inventory(inventory, verified)
    if not endpoints:
        raise SystemExit(f"No endpoints parsed from {inventory}")
    output_dir.mkdir(parents=True, exist_ok=True)
    write_csv(output_dir / "endpoint-evidence-matrix.csv", endpoints)
    write_markdown(output_dir / "ENDPOINT_EVIDENCE_MATRIX.md", inventory, endpoints)
    print(output_dir)


if __name__ == "__main__":
    main()
