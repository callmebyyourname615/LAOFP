#!/usr/bin/env python3
"""Dependency-light structural checks for production hardening phases 53C-53J."""
from __future__ import annotations

import json
import re
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def text(path: str) -> str:
    candidate = ROOT / path
    if not candidate.is_file():
        errors.append(f"missing file: {path}")
        return ""
    return candidate.read_text(encoding="utf-8")


def require(path: str, *needles: str) -> None:
    content = text(path)
    for needle in needles:
        if needle not in content:
            errors.append(f"{path}: missing contract token {needle!r}")


# 53C migration isolation
require("src/main/java/com/example/switching/migration/MigrationApplication.java",
        "KafkaAutoConfiguration", "TaskSchedulingAutoConfiguration")
for source in (ROOT / "src/main/java").rglob("*.java"):
    content = source.read_text(encoding="utf-8")
    if "@Scheduled" in content or "@KafkaListener" in content:
        if '@Profile("!migration")' not in content:
            errors.append(f"runtime side effect is active in migration profile: {source.relative_to(ROOT)}")

# 53D operational metrics
require("src/main/java/com/example/switching/observability/OperationalMetricsConfiguration.java",
        '@Profile("!migration")', "matchIfMissing = true", "OperationalMetricsCollector")
require("src/main/resources/application-prod.yml", "OPERATIONAL_METRICS_ENABLED:true")

# 53E migration integration test at the repository latest version
require("src/test/java/com/example/switching/migration/MigrationApplicationIntegrationTest.java",
        'isEqualTo("106")', "KafkaTemplate", "outboxDispatchWorker")

# 53F unified gates and restored runbooks
for path in [
    "docs/runbooks/RB-08_MONITORING_AND_API_SLO.md",
    "docs/runbooks/RB-09_DATABASE_AND_QUEUE_PRESSURE.md",
    "docs/runbooks/RB-10_TRANSACTION_SETTLEMENT_AND_WEBHOOK.md",
    "docs/runbooks/RB-11_AML_SANCTIONS_AND_STR.md",
    "docs/aml/SANCTIONS_PROVIDER_ONBOARDING.md",
    "docs/implementation/PHASES_05_TO_07_IMPLEMENTATION.md",
    ".github/workflows/production-readiness-gates.yml",
]:
    text(path)

# 53G release gate must run before kubectl configuration.
deploy = text(".github/workflows/deploy.yml")
gate = deploy.find("Enforce production change window and freeze gate")
kubectl = deploy.find("Configure kubectl")
if gate < 0 or kubectl < 0 or gate > kubectl:
    errors.append("deploy workflow does not enforce release gate before cluster access")
require("scripts/release/check_change_window.sh", "release_gate_decision", "HARD", "evidence_hash")

# 53H production environment contract.
contract_path = ROOT / "config/production-environment-contract.yaml"
try:
    contract = yaml.safe_load(contract_path.read_text(encoding="utf-8"))
    if contract.get("schemaVersion") != 1 or len(contract.get("variables", {})) < 20:
        errors.append("production environment contract is incomplete")
except Exception as exc:
    errors.append(f"invalid production environment contract: {exc}")
require("src/main/java/com/example/switching/config/ProductionStartupValidator.java",
        "operational-metrics.enabled must be true", "json-initiation.enabled must be false")

# 53I every alert has a resolvable runbook.
require("scripts/monitoring/verify_alert_runbooks.py", "runbook_url", "anchor")
require("scripts/monitoring/run_alert_delivery_drill.sh", "ALERT_EXPECTED_RECEIVER", "drill")

# 53J evidence contract and fail-closed readiness.
plan_path = ROOT / "config/runtime-evidence-plan.yaml"
try:
    plan = yaml.safe_load(plan_path.read_text(encoding="utf-8"))
    ids = [row.get("id") for row in plan.get("controls", [])]
    required = {
        "full-maven-verify", "migration-v83-runtime", "performance-sustained-2k",
        "performance-burst-10k", "settlement-500k", "soak-8h",
        "backup-restore-drill", "dr-suite", "vault-key-rotation", "alert-delivery-drill",
    }
    missing = sorted(required - set(ids))
    if missing:
        errors.append(f"runtime evidence plan missing controls: {', '.join(missing)}")
    if len(ids) != len(set(ids)):
        errors.append("runtime evidence plan has duplicate control ids")
except Exception as exc:
    errors.append(f"invalid runtime evidence plan: {exc}")
try:
    json.loads(text("schemas/runtime-evidence-manifest.schema.json"))
except Exception as exc:
    errors.append(f"invalid runtime evidence JSON schema: {exc}")
require("scripts/evidence/build_runtime_evidence.py", "go_live_ready", '"NOT_RUN"')
require("scripts/evidence/verify_runtime_evidence.py", "--require-go-live-ready", "hash mismatch")
require("scripts/evidence/run_runtime_evidence.sh", "Production execution is intentionally prohibited",
        "I_UNDERSTAND_THIS_IS_DESTRUCTIVE")

# Branch protection must include the consolidated static job.
require("scripts/configure_branch_protection.sh", "Production Readiness Static Gates")

if errors:
    print(f"Phase 53C-53J static verification: FAIL ({len(errors)} issue(s))", file=sys.stderr)
    for error in errors:
        print(f"  - {error}", file=sys.stderr)
    raise SystemExit(1)
print("Phase 53C-53J static verification: PASS")
