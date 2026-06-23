#!/usr/bin/env python3
"""Static repository contract for Phase 62A-62J.

Strict mode requires the authoritative Phase II V91-V96 migrations. Delivery mode
exists only to validate this changed-files package against an older ZIP baseline;
it never reports the migration inventory as production-ready.
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
LATEST = 106
AUTHORITATIVE_COUNT = 99
RESERVED_GAPS = {88, 89, 90, 98, 99, 102, 103}
BASELINE_MISSING_PHASE_II = {91, 92, 93, 94, 95, 96}

REQUIRED = [
    "scripts/phase62/run_phase62.sh",
    "scripts/phase62/62A-test-blocker-regression.sh",
    "scripts/phase62/62B-full-verification-closure.sh",
    "scripts/phase62/62C-smos-completion.sh",
    "scripts/phase62/62D-read-replica-routing.sh",
    "scripts/phase62/62E-financial-precision.sh",
    "scripts/phase62/62F-hikari-monitoring.sh",
    "scripts/phase62/62G-dashboard-hardening.sh",
    "scripts/phase62/62H-promotion-integrity.sh",
    "scripts/phase62/62I-nplus1-pagination.sh",
    "scripts/phase62/62J-distributed-tracing.sh",
    "src/main/resources/db/migration/V104__standardize_financial_numeric_precision.sql",
    "src/main/resources/db/migration/V105__promotion_budget_and_funder_ledger_controls.sql",
    "src/main/resources/db/migration/V106__distributed_trace_correlation.sql",
    "src/main/java/com/example/switching/config/TransactionRoutingDataSource.java",
    "src/main/java/com/example/switching/promotion/service/PromotionBudgetService.java",
    "src/main/java/com/example/switching/observability/sql/NPlusOneStatementInspector.java",
    "src/main/java/com/example/switching/observability/tracing/TraceContextSupport.java",
    "docs/security/SMOS_PERMISSION_MATRIX.md",
    "docs/openapi/smos-api.yaml",
    "docs/architecture/FINANCIAL_PRECISION_POLICY.md",
    "monitoring/prometheus/phase62-database-pool-rules.yaml",
    "monitoring/grafana/dashboards/switching-database-pools.json",
    "docs/runbooks/HIKARI_POOL_SATURATION.md",
]

VERIFIERS = [
    "verify_test_blocker_fixes.py",
    "verify_smos_endpoint_security.py",
    "verify_read_replica_routing.py",
    "verify_financial_precision.py",
    "verify_hikari_monitoring.py",
    "verify_dashboard_hardening.py",
    "verify_promotion_integrity.py",
    "verify_pagination_and_nplus1.py",
    "verify_distributed_tracing.py",
]


def command(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, check=False, timeout=60)


def migration_inventory(errors: list[str], warnings: list[str], allow_baseline: bool) -> dict:
    versions: dict[int, str] = {}
    duplicate: list[int] = []
    for path in sorted((ROOT / "src/main/resources/db/migration").glob("V*__*.sql")):
        match = re.fullmatch(r"V(\d+)__.+\.sql", path.name)
        if not match:
            errors.append(f"invalid migration filename: {path.name}")
            continue
        version = int(match.group(1))
        if version in versions:
            duplicate.append(version)
        versions[version] = path.name
    if duplicate:
        errors.append(f"duplicate migration versions: {sorted(set(duplicate))}")
    ordered = sorted(versions)
    missing = set(range(1, LATEST + 1)) - set(ordered)
    if not ordered or ordered[-1] != LATEST:
        errors.append(f"latest migration is {ordered[-1] if ordered else None}, expected {LATEST}")
    if allow_baseline:
        allowed = RESERVED_GAPS | BASELINE_MISSING_PHASE_II
        if missing != allowed:
            errors.append(f"delivery baseline gaps are {sorted(missing)}, expected {sorted(allowed)}")
        if BASELINE_MISSING_PHASE_II <= missing:
            warnings.append("authoritative Phase II migrations V91-V96 are absent from the supplied baseline; strict certification remains blocked")
    else:
        if missing != RESERVED_GAPS:
            errors.append(f"migration gaps are {sorted(missing)}, expected reserved gaps {sorted(RESERVED_GAPS)}")
        if len(ordered) != AUTHORITATIVE_COUNT:
            errors.append(f"migration count is {len(ordered)}, expected {AUTHORITATIVE_COUNT}")
    return {"count": len(ordered), "latest": ordered[-1] if ordered else None,
            "gaps": sorted(missing)}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--allow-missing-authoritative-phase-ii", action="store_true",
                        help="delivery-package validation only; strict Go-Live gates must not use this")
    parser.add_argument("--json-output")
    args = parser.parse_args()

    errors: list[str] = []
    warnings: list[str] = []
    details: dict[str, object] = {}

    for rel in REQUIRED:
        if not (ROOT / rel).is_file():
            errors.append(f"missing required file: {rel}")

    for rel in VERIFIERS:
        result = command(sys.executable, f"scripts/phase62/{rel}")
        details[rel] = {"exitCode": result.returncode, "output": result.stdout.strip()}
        if result.returncode:
            errors.append(f"{rel} failed: {result.stdout.strip()}")

    for script in sorted((ROOT / "scripts/phase62").glob("*.sh")):
        result = command("bash", "-n", str(script.relative_to(ROOT)))
        if result.returncode:
            errors.append(f"bash syntax failed: {script.relative_to(ROOT)}: {result.stdout.strip()}")
    for script in sorted((ROOT / "scripts/phase62").glob("*.py")):
        try:
            compile(script.read_text(encoding="utf-8"), str(script), "exec")
        except SyntaxError as exc:
            errors.append(f"python syntax failed: {script.relative_to(ROOT)}: {exc}")

    for rel in [
        ".env.prod.example", "config/production-environment-contract.yaml",
        "k8s/configmap.yaml", "k8s/external-secrets/application-secrets.yaml",
        "monitoring/prometheus/phase62-database-pool-rules.yaml",
        "docs/openapi/smos-api.yaml",
    ]:
        path = ROOT / rel
        try:
            if path.suffix in {".yaml", ".yml"}:
                list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
        except Exception as exc:
            errors.append(f"YAML parse failed for {rel}: {exc}")
    try:
        json.loads((ROOT / "monitoring/grafana/dashboards/switching-database-pools.json").read_text())
    except Exception as exc:
        errors.append(f"Grafana JSON parse failed: {exc}")

    details["migrationInventory"] = migration_inventory(
        errors, warnings, args.allow_missing_authoritative_phase_ii)

    diff = command("git", "diff", "--check")
    if diff.returncode:
        errors.append("git diff --check failed: " + diff.stdout.strip())

    status = "PASS" if not errors else "FAIL"
    result = {"phase": "62A-62J", "status": status, "errors": errors,
              "warnings": warnings, "details": details,
              "deliveryBaselineMode": args.allow_missing_authoritative_phase_ii}
    if args.json_output:
        output = Path(args.json_output)
        if not output.is_absolute():
            output = ROOT / output
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")

    print(f"Phase 62 static contract: {status}")
    for warning in warnings:
        print("  WARN:", warning)
    for error in errors:
        print("  ERROR:", error)
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
