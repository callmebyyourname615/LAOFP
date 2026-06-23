#!/usr/bin/env python3
from __future__ import annotations

import json
import py_compile
import re
import subprocess
import tempfile
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
FAILURES: list[str] = []

PHASE_SCRIPTS = [
    f"scripts/phase60/60{letter}-{name}.sh"
    for letter, name in zip(
        "ABCDEFGHIJ",
        (
            "repository-baseline",
            "build-test-closure",
            "migration-certification",
            "smos-security-e2e",
            "dashboard-promotion-acceptance",
            "secret-rotation-readiness",
            "uat-infrastructure-contract",
            "performance-capacity",
            "resilience-evidence",
            "assemble-evidence-bundle",
        ),
    )
]

REQUIRED_FILES = [
    *PHASE_SCRIPTS,
    "scripts/phase60/common.sh",
    "scripts/phase60/run_phase60.sh",
    "scripts/phase60/write_phase_result.py",
    "scripts/phase60/verify_repository_baseline.py",
    "scripts/phase60/summarize_test_reports.py",
    "scripts/phase60/verify_migration_inventory.py",
    "scripts/phase60/probe_uat_infrastructure.py",
    "scripts/phase60/verify_secret_rotation_attestation.py",
    "scripts/phase60/verify_performance_evidence.py",
    "scripts/phase60/verify_resilience_attestation.py",
    "scripts/phase60/build_evidence_manifest.py",
    "scripts/phase60/verify_evidence_manifest.py",
    "monitoring/scripts/build-alert-inventory.py",
    "monitoring/scripts/run-alert-routing-drill.py",
    "security/rotation/phase60-secret-rotation-inventory.yaml",
    "schemas/phase60-result.schema.json",
    "schemas/phase60-evidence-manifest.schema.json",
    "docs/PHASE_60A_60J_READINESS.md",
    "docs/templates/PHASE60_SECRET_ROTATION_ATTESTATION.example.json",
    "docs/templates/PHASE60_PERFORMANCE_ATTESTATION.example.json",
    "docs/templates/PHASE60_RESILIENCE_ATTESTATION.example.json",
    "docs/templates/PHASE60_UAT_ENTRY_ATTESTATION.example.json",
    ".github/workflows/phase60-readiness.yml",
    "src/test/java/com/example/switching/migration/V97SmosUserAccessMigrationIntegrationTest.java",
    "src/test/java/com/example/switching/migration/V100CurrentStatusReportingRepairIntegrationTest.java",
    "src/main/resources/db/migration/V100__repair_current_status_reporting.sql",
    "src/test/java/com/example/switching/usermgmt/service/SmosSecurityCertificationIntegrationTest.java",
    "src/test/java/com/example/switching/dashboard/CriticalDashboardDataAcceptanceIntegrationTest.java",
]


def require_file(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        FAILURES.append(f"missing file: {relative}")
        return ""
    return path.read_text(encoding="utf-8")


def require_text(relative: str, *needles: str) -> None:
    content = require_file(relative)
    for needle in needles:
        if needle not in content:
            FAILURES.append(f"{relative}: missing contract text {needle!r}")


for relative in REQUIRED_FILES:
    require_file(relative)

for relative in PHASE_SCRIPTS + ["scripts/phase60/common.sh", "scripts/phase60/run_phase60.sh"]:
    path = ROOT / relative
    if path.is_file():
        completed = subprocess.run(["bash", "-n", str(path)], cwd=ROOT, check=False, capture_output=True, text=True)
        if completed.returncode != 0:
            FAILURES.append(f"{relative}: bash syntax failed: {completed.stderr.strip()}")
        if not (path.stat().st_mode & 0o100):
            FAILURES.append(f"{relative}: script is not executable")

with tempfile.TemporaryDirectory(prefix="phase60-pycompile-") as compile_dir:
    for index, path in enumerate(sorted((ROOT / "scripts/phase60").glob("*.py")) + sorted((ROOT / "monitoring/scripts").glob("*.py"))):
        try:
            py_compile.compile(str(path), cfile=str(Path(compile_dir) / f"{index}.pyc"), doraise=True)
        except py_compile.PyCompileError as exc:
            FAILURES.append(f"{path.relative_to(ROOT)}: Python compile failed: {exc.msg}")

for relative in (
    "schemas/phase60-result.schema.json",
    "schemas/phase60-evidence-manifest.schema.json",
    "docs/templates/PHASE60_SECRET_ROTATION_ATTESTATION.example.json",
    "docs/templates/PHASE60_PERFORMANCE_ATTESTATION.example.json",
    "docs/templates/PHASE60_RESILIENCE_ATTESTATION.example.json",
    "docs/templates/PHASE60_UAT_ENTRY_ATTESTATION.example.json",
):
    try:
        json.loads((ROOT / relative).read_text(encoding="utf-8"))
    except Exception as exc:
        FAILURES.append(f"{relative}: invalid JSON: {exc}")

try:
    inventory = yaml.safe_load((ROOT / "security/rotation/phase60-secret-rotation-inventory.yaml").read_text(encoding="utf-8"))
    ids = [item.get("id") for item in inventory.get("credentials", [])]
    if ids != [
        "POSTGRES_PASSWORD", "REPLICATION_PASSWORD", "DB_APP_PASSWORD",
        "FLYWAY_PASSWORD", "ARCHIVE_POSTGRES_PASSWORD", "MINIO_ROOT_PASSWORD"
    ]:
        FAILURES.append("secret rotation inventory must contain the six controlled credentials in canonical order")
except Exception as exc:
    FAILURES.append(f"secret rotation inventory is invalid: {exc}")

for relative in ("scripts/phase60/60H-performance-capacity.sh", "scripts/phase60/60I-resilience-evidence.sh"):
    require_text(relative, "phase_require_runtime_confirmation")
require_text("scripts/phase60/common.sh", "TARGET_ENVIRONMENT", "PHASE60_EXECUTE_RUNTIME", "CONFIRM_UAT_DRILLS")
require_text("scripts/phase60/60G-uat-infrastructure-contract.sh", "TARGET_ENVIRONMENT", "UAT_ENV_FILE", "UAT_BASE_URL")
require_text("scripts/phase60/60J-assemble-evidence-bundle.sh", "APPLICATION_IMAGE_DIGEST", "MIGRATION_IMAGE_DIGEST", "UAT_ENTRY_ATTESTATION")
require_text("scripts/phase60/run_phase60.sh", "--preflight", "--repo", "--full")

require_text(
    "src/main/resources/db/migration/V100__repair_current_status_reporting.sql",
    "IF delta > 0", "GREATEST(0", "rebuild_current_status_reporting",
    "LOCK TABLE transactions, inquiries, outbox_messages IN SHARE MODE",
)
require_text(
    "src/main/java/com/example/switching/settlement/service/SettlementBatchService.java",
    "ON CONFLICT (cycle_id, transaction_ref, bank_code, direction, settlement_date)",
    "if (debitInserted == 1)", "if (creditInserted == 1)",
)
require_text(
    "src/main/java/com/example/switching/aml/sanctions/SanctionsNameNormalizer.java",
    "preserveMarks", "Character.UnicodeScript", "Normalizer.Form.NFC",
)

promotion = require_file("src/main/java/com/example/switching/promotion/service/PromotionEligibilityEvaluator.java")
for forbidden in ("SpelExpressionParser", "ExpressionParser", "StandardEvaluationContext", "Runtime.getRuntime"):
    if forbidden in promotion:
        FAILURES.append(f"promotion eligibility evaluator contains executable expression surface: {forbidden}")
for required in ("MAX_CONDITIONS", "MAX_IN_VALUES", "requiredNumber", "minimum must not exceed maximum"):
    if required not in promotion:
        FAILURES.append(f"promotion evaluator missing bounded DSL guard: {required}")

migration_files = list((ROOT / "src/main/resources/db/migration").glob("V*__*.sql"))
versions = []
for path in migration_files:
    match = re.match(r"V(\d+)__", path.name)
    if match:
        versions.append(int(match.group(1)))
if len(versions) != 90 or max(versions, default=0) != 101:
    FAILURES.append(f"migration inventory must be 90 files through V101, got {len(versions)} through V{max(versions, default=0)}")
if sorted(set(range(1, 102)) - set(versions)) != [88, 89, 90, 91, 92, 93, 94, 95, 96, 98, 99]:
    FAILURES.append("current baseline reserves V88-V96 and V98-V99; Phase II V91-V96 remain a separate blocker")

for sensitive in (".env.bak", "new.txt"):
    if (ROOT / sensitive).exists():
        FAILURES.append(f"sensitive/prohibited file still exists: {sensitive}")

completed = subprocess.run(["git", "diff", "--check"], cwd=ROOT, check=False, capture_output=True, text=True)
if completed.returncode != 0:
    FAILURES.append("git diff --check failed")

alert_contract = subprocess.run(
    [sys.executable, str(ROOT / "scripts/monitoring/verify_alert_runbooks.py")],
    cwd=ROOT, check=False, capture_output=True, text=True, timeout=60,
)
if alert_contract.returncode != 0:
    FAILURES.append("alert/runbook contract failed: " + (alert_contract.stderr.strip() or alert_contract.stdout.strip()))

workflow = require_file(".github/workflows/phase60-readiness.yml")
for marker in ("verify_phase60_static.py", "clean verify", "upload-artifact"):
    if marker not in workflow:
        FAILURES.append(f"phase60 workflow missing {marker!r}")

if FAILURES:
    for failure in FAILURES:
        print(f"FAIL: {failure}")
    print(f"Phase 60 static contract: FAIL ({len(FAILURES)} issue(s))")
    sys.exit(1)
print("Phase 60 static contract: PASS")
