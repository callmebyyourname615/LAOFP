#!/usr/bin/env python3
"""Summarize Surefire/Failsafe and JaCoCo evidence and enforce certification thresholds."""
from __future__ import annotations
import argparse, json, pathlib, xml.etree.ElementTree as ET
import yaml


def number(value: str | None) -> int:
    try: return int(float(value or "0"))
    except ValueError: return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--project-root", default=".")
    ap.add_argument("--thresholds", default="config/phase54-thresholds.yaml")
    ap.add_argument("--allowlist", default="config/certification-skipped-tests-allowlist.txt")
    ap.add_argument("--output", required=True)
    args = ap.parse_args()
    root = pathlib.Path(args.project_root)
    cfg = yaml.safe_load(pathlib.Path(args.thresholds).read_text(encoding="utf-8"))["build"]
    allow = set()
    for line in pathlib.Path(args.allowlist).read_text(encoding="utf-8").splitlines():
        value = line.strip()
        if value and not value.startswith("#"): allow.add(value.split()[0])
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    skipped = []
    xml_files = sorted((root / "target/surefire-reports").glob("TEST-*.xml")) + sorted((root / "target/failsafe-reports").glob("TEST-*.xml"))
    for path in xml_files:
        suite = ET.parse(path).getroot()
        for key in totals: totals[key] += number(suite.attrib.get(key))
        for case in suite.findall(".//testcase"):
            if case.find("skipped") is not None:
                cls, name = case.attrib.get("classname", ""), case.attrib.get("name", "")
                skipped.append({"id": f"{cls}#{name}", "class": cls, "name": name, "allowed": f"{cls}#{name}" in allow or cls in allow})
    jacoco = root / "target/site/jacoco/jacoco.xml"
    coverage = {"linePercent": 0.0, "branchPercent": 0.0, "reportPresent": jacoco.is_file()}
    if jacoco.is_file():
        report = ET.parse(jacoco).getroot()
        counters = {c.attrib["type"]: c for c in report.findall("counter")}
        for kind, key in (("LINE", "linePercent"), ("BRANCH", "branchPercent")):
            counter = counters.get(kind)
            if counter is not None:
                missed, covered = number(counter.attrib.get("missed")), number(counter.attrib.get("covered"))
                coverage[key] = round(100.0 * covered / max(1, missed + covered), 2)
    unapproved = [row for row in skipped if not row["allowed"]]
    reasons = []
    if not xml_files: reasons.append("no Surefire/Failsafe XML reports")
    if totals["failures"] or totals["errors"]: reasons.append("test failures or errors detected")
    if not cfg.get("allowSkippedTests", False) and unapproved: reasons.append("unapproved skipped tests detected")
    if totals["skipped"] > int(cfg.get("maximumSkippedTests", 0)) and unapproved: reasons.append("skipped test limit exceeded")
    if not coverage["reportPresent"]: reasons.append("JaCoCo XML report missing")
    if coverage["linePercent"] < float(cfg["minimumLineCoveragePercent"]): reasons.append("line coverage below threshold")
    if coverage["branchPercent"] < float(cfg["minimumBranchCoveragePercent"]): reasons.append("branch coverage below threshold")
    doc = {"schemaVersion": 1, "reports": [str(p) for p in xml_files], "totals": totals, "skippedTests": skipped,
           "coverage": coverage, "thresholds": cfg, "passed": not reasons, "failureReasons": reasons}
    pathlib.Path(args.output).write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps(doc, indent=2, sort_keys=True))
    return 0 if doc["passed"] else 1

if __name__ == "__main__": raise SystemExit(main())
