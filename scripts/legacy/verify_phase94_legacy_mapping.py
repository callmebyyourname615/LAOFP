#!/usr/bin/env python3
import csv
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CSV_PATH = ROOT / "docs" / "legacy" / "lmpscore-endpoint-coverage.csv"
DOC_PATH = ROOT / "docs" / "legacy" / "PHASE_94_LEGACY_ENDPOINT_COVERAGE_MAPPING.md"

EXPECTED_COUNTS = {
    "covered": 8,
    "partial": 38,
    "missing": 34,
    "deprecated": 4,
}

EXPECTED_ENDPOINTS = {
    "/": "covered",
    "/admin/shutdown": "deprecated",
    "/crossborder/upi/login": "deprecated",
    "/inquiry": "covered",
    "/transfer": "covered",
    "/settlementfile": "partial",
    "/finlink/inform_LIFMIS": "missing",
    "/roadtax/payment": "partial",
}

REQUIRED_HEADERS = {
    "legacy_endpoint",
    "status",
    "domain",
    "priority",
    "switching_coverage",
    "recommended_action",
}


def main() -> int:
    if not CSV_PATH.exists():
        raise SystemExit(f"missing mapping csv: {CSV_PATH}")
    if not DOC_PATH.exists():
        raise SystemExit(f"missing phase doc: {DOC_PATH}")

    with CSV_PATH.open(newline="", encoding="utf-8") as handle:
        reader = csv.DictReader(handle)
        headers = set(reader.fieldnames or [])
        missing_headers = REQUIRED_HEADERS - headers
        if missing_headers:
            raise SystemExit(f"missing csv headers: {sorted(missing_headers)}")
        rows = list(reader)

    if len(rows) != 84:
        raise SystemExit(f"expected 84 endpoint rows, found {len(rows)}")

    endpoints = [row["legacy_endpoint"] for row in rows]
    duplicates = sorted({endpoint for endpoint in endpoints if endpoints.count(endpoint) > 1})
    if duplicates:
        raise SystemExit(f"duplicate endpoint rows: {duplicates}")

    invalid = [
        row["legacy_endpoint"]
        for row in rows
        if not row["legacy_endpoint"].startswith("/")
        or not row["status"]
        or not row["domain"]
        or not row["priority"]
        or not row["switching_coverage"]
        or not row["recommended_action"]
    ]
    if invalid:
        raise SystemExit(f"incomplete endpoint rows: {invalid}")

    counts = Counter(row["status"] for row in rows)
    if dict(counts) != EXPECTED_COUNTS:
        raise SystemExit(f"unexpected status counts: {dict(counts)}")

    status_by_endpoint = {row["legacy_endpoint"]: row["status"] for row in rows}
    for endpoint, expected_status in EXPECTED_ENDPOINTS.items():
        actual = status_by_endpoint.get(endpoint)
        if actual != expected_status:
            raise SystemExit(f"{endpoint} expected {expected_status}, found {actual}")

    doc = DOC_PATH.read_text(encoding="utf-8")
    for required in ("36a3d979c877e86aaf5ba4113345971ab4d7d21675e82daef47791328cc9faf9", "Phase 95", "Phase 98"):
        if required not in doc:
            raise SystemExit(f"phase doc missing required marker: {required}")

    print("phase94 legacy mapping verified: 84 endpoints")
    print("covered=8 partial=38 missing=34 deprecated=4")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
