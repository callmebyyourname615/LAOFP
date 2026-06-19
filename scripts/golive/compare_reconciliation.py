#!/usr/bin/env python3
"""Compare pre/post cutover aggregates and enforce financial invariants."""
from __future__ import annotations
import argparse, decimal, json, pathlib

D = decimal.Decimal
NUMERIC_KEYS = ("count", "amount", "debit", "credit", "net_position", "accounts", "balance")


def number(value) -> D:
    return D(str(value or 0))


def totals(rows: list[dict]) -> dict[str, D]:
    result = {key: D(0) for key in NUMERIC_KEYS}
    for row in rows:
        for key in NUMERIC_KEYS:
            if key in row:
                result[key] += number(row[key])
    return result


def balanced(rows: list[dict]) -> bool:
    for row in rows:
        if "net_position" in row and number(row["net_position"]) != 0:
            return False
        if "debit" in row and "credit" in row and number(row["debit"]) != number(row["credit"]):
            return False
    return True


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--baseline", required=True)
    ap.add_argument("--current", required=True)
    ap.add_argument("--output", required=True)
    args = ap.parse_args()
    before = json.loads(pathlib.Path(args.baseline).read_text(encoding="utf-8"))
    after = json.loads(pathlib.Path(args.current).read_text(encoding="utf-8"))
    errors, checks = [], []
    if before.get("queryDefinitionSha256") != after.get("queryDefinitionSha256"):
        errors.append("query definition changed between captures")
    for name, baseline in before.get("results", {}).items():
        current = after.get("results", {}).get(name)
        if current is None:
            errors.append(f"missing current result: {name}")
            continue
        mode = baseline.get("mode")
        b_rows, c_rows = baseline.get("rows", []), current.get("rows", [])
        passed, detail = True, "verified"
        if mode == "aggregate-nondecreasing":
            b, c = totals(b_rows), totals(c_rows)
            for key in NUMERIC_KEYS:
                if key in ("debit", "credit", "net_position"):
                    continue
                if c[key] < b[key]:
                    passed, detail = False, f"aggregate {key} decreased"
                    break
        elif mode == "balanced":
            passed = balanced(c_rows)
            detail = "financial totals balanced" if passed else "financial totals are not balanced"
        elif mode == "monotonic":
            passed = totals(c_rows)["count"] >= totals(b_rows)["count"]
            detail = "aggregate count did not decrease" if passed else "aggregate count decreased"
        elif mode == "no-increase":
            passed = totals(c_rows)["count"] <= totals(b_rows)["count"]
            detail = "exception count did not increase" if passed else "exception count increased"
        elif mode == "zero":
            passed = totals(c_rows)["count"] == 0
            detail = "count is zero" if passed else "non-zero count detected"
        else:
            passed, detail = False, f"unsupported comparison mode: {mode}"
        checks.append({"id": name, "mode": mode, "status": "PASS" if passed else "FAIL", "detail": detail})
        if not passed:
            errors.append(f"{name}: {detail}")
    report = {
        "schemaVersion": 1,
        "baselineLabel": before.get("label"),
        "currentLabel": after.get("label"),
        "status": "PASS" if not errors else "FAIL",
        "checks": checks,
        "errors": errors,
    }
    pathlib.Path(args.output).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "checks": len(checks)}, sort_keys=True))
    return 0 if not errors else 2


if __name__ == "__main__":
    raise SystemExit(main())
