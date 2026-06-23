#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import xml.etree.ElementTree as ET
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--root", default="target")
parser.add_argument("--output", required=True)
args = parser.parse_args()

root = Path(args.root)
totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0, "files": 0}
paths = sorted(root.rglob("TEST-*.xml")) if root.exists() else []
for path in paths:
    try:
        node = ET.parse(path).getroot()
    except ET.ParseError:
        continue
    totals["files"] += 1
    for key in ["tests", "failures", "errors", "skipped"]:
        totals[key] += int(float(node.attrib.get(key, 0)))
totals["passed"] = totals["tests"] > 0 and totals["failures"] == 0 and totals["errors"] == 0

out = Path(args.output)
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps(totals, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(totals, sort_keys=True))
raise SystemExit(0 if totals["passed"] else 1)
