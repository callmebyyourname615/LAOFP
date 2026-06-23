#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path


def parse_suite(path: Path) -> dict:
    root = ET.parse(path).getroot()
    return {
        "file": path.as_posix(),
        "name": root.attrib.get("name", path.stem),
        "tests": int(float(root.attrib.get("tests", "0"))),
        "failures": int(float(root.attrib.get("failures", "0"))),
        "errors": int(float(root.attrib.get("errors", "0"))),
        "skipped": int(float(root.attrib.get("skipped", "0"))),
        "timeSeconds": float(root.attrib.get("time", "0") or 0),
    }


def latest_input_mtime(root: Path) -> float:
    candidates = [root / "pom.xml"]
    for source_root in (root / "src/main", root / "src/test"):
        if source_root.exists():
            candidates.extend(path for path in source_root.rglob("*") if path.is_file())
    return max((path.stat().st_mtime for path in candidates if path.is_file()), default=0.0)


def iso_from_mtime(value: float) -> str | None:
    if value <= 0:
        return None
    return datetime.fromtimestamp(value, timezone.utc).isoformat().replace("+00:00", "Z")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--output", required=True)
    parser.add_argument("--allow-empty", action="store_true")
    parser.add_argument(
        "--allow-stale", action="store_true",
        help="Inventory stale reports without treating them as current validation (preflight only)",
    )
    args = parser.parse_args()
    root = Path(args.root).resolve()
    output = Path(args.output).resolve()

    files = sorted((root / "target/surefire-reports").glob("TEST-*.xml"))
    files += sorted((root / "target/failsafe-reports").glob("TEST-*.xml"))
    suites: list[dict] = []
    parse_errors: list[str] = []
    for path in files:
        try:
            suite = parse_suite(path)
            suite["file"] = path.relative_to(root).as_posix()
            suites.append(suite)
        except Exception as exc:
            parse_errors.append(f"{path.relative_to(root)}: {exc}")

    totals = {
        key: sum(int(suite[key]) for suite in suites)
        for key in ("tests", "failures", "errors", "skipped")
    }
    totals["timeSeconds"] = round(sum(float(suite["timeSeconds"]) for suite in suites), 3)
    failing = [suite for suite in suites if suite["failures"] or suite["errors"]]
    latest_report = max((path.stat().st_mtime for path in files), default=0.0)
    latest_input = latest_input_mtime(root)
    stale = bool(files) and latest_input > latest_report + 1.0
    current_validation = bool(suites) and not stale
    passed = (
        not parse_errors
        and not failing
        and (bool(suites) or args.allow_empty)
        and (not stale or args.allow_stale)
    )
    document = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "passed": passed,
        "currentValidation": current_validation,
        "stale": stale,
        "latestSourceOrTestMtime": iso_from_mtime(latest_input),
        "latestReportMtime": iso_from_mtime(latest_report),
        "reportCount": len(suites),
        "totals": totals,
        "failingSuites": failing,
        "parseErrors": parse_errors,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    prefix = "STALE test inventory" if stale else "Tests run"
    print(
        f"{prefix}: {totals['tests']}, Failures: {totals['failures']}, "
        f"Errors: {totals['errors']}, Skipped: {totals['skipped']}"
    )
    if stale:
        print("  Existing XML reports predate current source/test changes and are not proof of the current revision.")
    for suite in failing:
        print(f"  HISTORICAL FAIL {suite['name']}: failures={suite['failures']} errors={suite['errors']}")
    for error in parse_errors:
        print(f"  XML ERROR {error}")
    if not suites and not args.allow_empty:
        print("  No Surefire/Failsafe XML reports found")
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
