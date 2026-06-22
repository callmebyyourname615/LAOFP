#!/usr/bin/env python3
"""Static production gate for Phase 53B (Flyway V83 schema alignment).

The verifier intentionally uses only the Python standard library so it can run in
pre-commit, CI, release jobs, and restricted migration-image build environments.
It does not execute SQL; the Testcontainers integration test covers runtime behavior.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import asdict, dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MIGRATION_DIR = ROOT / "src/main/resources/db/migration"
EXPECTED_LATEST_VERSION = 96
RESERVED_GAP = {88, 89, 90}  # reserved for future read-scaling extension


@dataclass(frozen=True)
class CheckResult:
    name: str
    passed: bool
    detail: str


def read(relative_path: str) -> str:
    path = ROOT / relative_path
    if not path.is_file():
        raise AssertionError(f"required file is missing: {relative_path}")
    return path.read_text(encoding="utf-8")


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def yaml_scalar(text: str, dotted_path: str) -> str | None:
    """Read a simple scalar from indentation-based YAML without external packages."""
    target = dotted_path.split(".")
    stack: list[tuple[int, str]] = []
    for raw_line in text.splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        indentation = len(raw_line) - len(raw_line.lstrip(" "))
        content = raw_line.strip()
        if ":" not in content:
            continue
        key, value = content.split(":", 1)
        key = key.strip()
        value = value.split("#", 1)[0].strip()
        while stack and stack[-1][0] >= indentation:
            stack.pop()
        current_path = [entry[1] for entry in stack] + [key]
        if current_path == target:
            return value.strip("'\"")
        if value == "":
            stack.append((indentation, key))
    return None


def migration_versions() -> list[int]:
    versions: list[int] = []
    seen: set[int] = set()
    for path in sorted(MIGRATION_DIR.glob("V*__*.sql")):
        match = re.fullmatch(r"V(\d+)__.+\.sql", path.name)
        require(match is not None, f"invalid Flyway filename: {path.name}")
        version = int(match.group(1))
        require(version not in seen, f"duplicate Flyway version: V{version}")
        seen.add(version)
        versions.append(version)
    return sorted(versions)


def check_migration_sequence() -> str:
    versions = migration_versions()
    expected = [n for n in range(1, EXPECTED_LATEST_VERSION + 1) if n not in RESERVED_GAP]
    require(versions == expected,
            f"Flyway sequence must be V1-V{EXPECTED_LATEST_VERSION} (gap V{sorted(RESERVED_GAP)} reserved); got {versions[:3]}...{versions[-3:] if versions else []}")
    return f"V1-V{EXPECTED_LATEST_VERSION} present (gap V{sorted(RESERVED_GAP)} reserved), {len(versions)} migrations"


def check_immutable_history() -> str:
    v47 = read("src/main/resources/db/migration/V47__outbox_dead_letter_quarantine.sql")
    v51 = read("src/main/resources/db/migration/V51__configuration_change_approval.sql")
    require("payload_sha256 CHAR(64) NOT NULL" in v47,
            "V47 must remain immutable; align the column only in V83")
    require("payload_sha256 CHAR(64) NOT NULL" in v51,
            "V51 must remain immutable; align the column only in V83")
    require("payload_sha256 VARCHAR(64)" not in v47,
            "V47 payload_sha256 was edited in place")
    require("payload_sha256 VARCHAR(64)" not in v51,
            "V51 payload_sha256 was edited in place")
    return "V47 and V51 retain their original CHAR(64) declarations"


def check_v83_contract() -> str:
    sql = read("src/main/resources/db/migration/V83__align_payload_sha256_to_varchar.sql")
    normalized = re.sub(r"\s+", " ", sql.upper())

    required_fragments = (
        "SET LOCAL LOCK_TIMEOUT",
        "SET LOCAL STATEMENT_TIMEOUT",
        "LOCK TABLE CONFIGURATION_CHANGE_REQUESTS, OUTBOX_DEAD_LETTERS IN ACCESS EXCLUSIVE MODE",
        "ALTER TABLE CONFIGURATION_CHANGE_REQUESTS ALTER COLUMN PAYLOAD_SHA256 TYPE VARCHAR(64)",
        "ALTER TABLE OUTBOX_DEAD_LETTERS ALTER COLUMN PAYLOAD_SHA256 TYPE VARCHAR(64)",
        "USING RTRIM(PAYLOAD_SHA256)::VARCHAR(64)",
        "CK_CONFIG_CHANGE_PAYLOAD_SHA256",
        "CK_OUTBOX_DLQ_PAYLOAD_SHA256",
        "VALIDATE CONSTRAINT CK_CONFIG_CHANGE_PAYLOAD_SHA256",
        "VALIDATE CONSTRAINT CK_OUTBOX_DLQ_PAYLOAD_SHA256",
        "CHARACTER_MAXIMUM_LENGTH = 64",
        "CONSTRAINT_METADATA.CONVALIDATED",
    )
    for fragment in required_fragments:
        require(fragment in normalized, f"V83 is missing required contract fragment: {fragment}")

    require(sql.count("ALTER COLUMN payload_sha256 TYPE VARCHAR(64)") == 2,
            "V83 must align exactly two payload_sha256 columns")
    require(sql.count("rtrim(payload_sha256) !~ '^[0-9A-Fa-f]{64}$'") == 2,
            "V83 must preflight both tables without printing digest values")
    require("DROP TABLE" not in normalized and "DROP COLUMN" not in normalized,
            "V83 must not drop tables or columns")
    require("DELETE FROM" not in normalized and "TRUNCATE" not in normalized,
            "V83 must not delete data")
    return "preflight, bounded locking, preserving conversion, constraints, and postconditions present"


def check_jpa_profiles() -> str:
    base = read("src/main/resources/application.yml")
    migration = read("src/main/resources/application-migration.yml")
    require(yaml_scalar(base, "spring.jpa.hibernate.ddl-auto") == "validate",
            "application.yml must set spring.jpa.hibernate.ddl-auto=validate")
    require(yaml_scalar(migration, "spring.jpa.hibernate.ddl-auto") == "none",
            "application-migration.yml must keep ddl-auto=none")
    return "runtime profile validates schema; one-shot migration profile does not start Hibernate DDL"


def check_entity_contracts() -> str:
    paths = (
        "src/main/java/com/example/switching/configchange/entity/ConfigurationChangeRequestEntity.java",
        "src/main/java/com/example/switching/outbox/deadletter/entity/OutboxDeadLetterEntity.java",
    )
    annotation = re.compile(
        r"@Column\(name\s*=\s*\"payload_sha256\",\s*nullable\s*=\s*false,\s*length\s*=\s*64\)\s*"
        r"private\s+String\s+payloadSha256;",
        re.S,
    )
    for relative_path in paths:
        source = read(relative_path)
        require(annotation.search(source) is not None,
                f"entity mapping contract missing from {relative_path}")
        require("columnDefinition = \"CHAR(64)\"" not in source,
                f"entity must not hard-code CHAR(64): {relative_path}")
    return "both JPA entities map payload_sha256 as required length=64 strings"


def check_test_contracts() -> str:
    integration = read(
        "src/test/java/com/example/switching/migration/V83PayloadSha256SchemaAlignmentIntegrationTest.java")
    mapping = read(
        "src/test/java/com/example/switching/migration/PayloadSha256EntityMappingContractTest.java")
    required = (
        'MigrationVersion.fromVersion("82")',
        'isInstanceOf(FlywayException.class)',
        'isEqualTo("82")',
        'isEqualTo("96")',
        'isEqualTo("character varying")',
        'isEqualTo(64)',
        'isEqualTo("23514")',
        "throughLatest.validate()",
        "throughLatest.info().pending()",
    )
    for token in required:
        require(token in integration, f"V83 integration test is missing assertion: {token}")
    require('getDeclaredField("payloadSha256")' in mapping,
            "entity mapping reflection test is incomplete")
    require("column.columnDefinition()" in mapping,
            "entity mapping test must reject a future CHAR columnDefinition")
    return "V82 upgrade path, data preservation, metadata, constraints, and entity mapping are covered"


def check_image_gate() -> str:
    script = read("scripts/verify_migration_image.sh")
    for token in (
        'EXPECTED_VERSION="${2:-83}"',
        "aligned_sha_column_count",
        "validated_sha_constraint_count",
        "current_version",
        "processExitCode=0Twice",
    ):
        require(token in script, f"migration image verifier missing: {token}")
    return "migration image gate expects V83 and validates both aligned columns and constraints"


def check_ci_and_branch_protection() -> str:
    workflow = read(".github/workflows/phase53b-schema-alignment.yml")
    branch = read("scripts/configure_branch_protection.sh")
    for token in (
        "name: Phase 53B Schema Alignment",
        "name: V83 Schema Alignment",
        "verify_phase53b_schema_alignment.py",
        "V83PayloadSha256SchemaAlignmentIntegrationTest",
        "target/surefire-reports",
    ):
        require(token in workflow, f"Phase 53B workflow missing: {token}")
    require('"V83 Schema Alignment"' in branch,
            "branch protection must require the V83 Schema Alignment job")
    return "PR/push CI gate and branch protection context are wired"



def check_operational_material() -> str:
    runbook = read("docs/runbooks/V83_SCHEMA_ALIGNMENT.md")
    evidence = read("docs/templates/V83_SCHEMA_ALIGNMENT_EVIDENCE.md")
    notes = read("PHASE_53B_DELIVERY_NOTES.md")
    for token in (
        "Pre-deployment checks",
        "Post-deployment verification",
        "Failure and rollback behavior",
        "V82 to V83",
        "forward migration",
    ):
        require(token.lower() in runbook.lower(), f"V83 runbook missing: {token}")
    for token in (
        "Artifact integrity",
        "Data preservation",
        "Constraint evidence",
        "Automated tests",
        "Sign-off",
    ):
        require(token in evidence, f"V83 evidence template missing: {token}")
    require("V83__align_payload_sha256_to_varchar.sql" in notes,
            "delivery notes must identify the exact migration")
    return "rollout, rollback/forward-fix, evidence, and sign-off material are present"

def run_checks() -> list[CheckResult]:
    checks = (
        ("migration-sequence", check_migration_sequence),
        ("immutable-history", check_immutable_history),
        ("v83-contract", check_v83_contract),
        ("jpa-profiles", check_jpa_profiles),
        ("entity-contracts", check_entity_contracts),
        ("test-contracts", check_test_contracts),
        ("migration-image-gate", check_image_gate),
        ("ci-branch-protection", check_ci_and_branch_protection),
        ("operational-material", check_operational_material),
    )
    results: list[CheckResult] = []
    for name, check in checks:
        try:
            detail = check()
            results.append(CheckResult(name, True, detail))
        except (AssertionError, OSError, AttributeError) as error:
            results.append(CheckResult(name, False, str(error)))
    return results


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    arguments = parser.parse_args()

    results = run_checks()
    passed = all(result.passed for result in results)

    if arguments.json:
        print(json.dumps({
            "phase": "53B",
            "expectedLatestMigration": EXPECTED_LATEST_VERSION,
            "passed": passed,
            "checks": [asdict(result) for result in results],
        }, indent=2, sort_keys=True))
    else:
        for result in results:
            marker = "PASS" if result.passed else "FAIL"
            print(f"[{marker}] {result.name}: {result.detail}")
        print(f"Phase 53B static verification: {'PASS' if passed else 'FAIL'}")

    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
