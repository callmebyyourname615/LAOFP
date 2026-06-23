#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--required", nargs="+", required=True)
    parser.add_argument("--category", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    data = json.loads(args.manifest.read_text(encoding="utf-8"))
    controls = {item.get("id"): item for item in data.get("controls", [])}
    rows = []
    errors = []
    for control_id in args.required:
        row = controls.get(control_id)
        if row is None:
            errors.append(f"missing runtime control: {control_id}")
            rows.append({"id": control_id, "status": "MISSING"})
            continue
        rows.append(row)
        if row.get("status") != "PASS":
            errors.append(f"runtime control is not PASS: {control_id}={row.get('status')}")
    output = {
        "schemaVersion": 1,
        "category": args.category,
        "release": data.get("release"),
        "controls": rows,
        "passed": not errors,
        "errors": errors,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        return 1
    print(f"Runtime controls verified: {args.category} ({len(rows)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
