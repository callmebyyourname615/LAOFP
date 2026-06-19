#!/usr/bin/env python3
"""Static acceptance checks for Switching phases 13-22.

Uses only the Python standard library so it can run before Maven dependency resolution.
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class CheckFailure(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise CheckFailure(message)


def text(path: str) -> str:
    candidate = ROOT / path
    require(candidate.is_file(), f"missing required file: {path}")
    return candidate.read_text(encoding="utf-8")


def run(*args: str, cwd: Path | None = None, expect: int = 0) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(args, cwd=cwd or ROOT, text=True, capture_output=True)
    if result.returncode != expect:
        raise CheckFailure(
            f"command failed ({result.returncode}, expected {expect}): {' '.join(args)}\n"
            f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}"
        )
    return result


def check_required_files() -> None:
    required = [
        ".github/workflows/release-evidence.yml",
        ".github/workflows/progressive-deploy.yml",
        ".github/workflows/slo-gate.yml",
        ".github/workflows/event-schema.yml",
        ".github/workflows/database-maintenance-deploy.yml",
        ".github/workflows/participant-certification.yml",
        ".github/workflows/continuous-resilience.yml",
        "k8s/canary/networkpolicy.yaml",
        "monitoring/prometheus/slo-recording-rules.yaml",
        "schemas/events/v1/outbox-dispatch.json",
        "src/main/resources/db/migration/V47__outbox_dead_letter_quarantine.sql",
        "src/main/resources/db/migration/V48__database_maintenance_runs.sql",
        "src/main/resources/db/migration/V49__legal_holds_and_retention.sql",
        "src/main/resources/db/migration/V50__privileged_access_sessions.sql",
        "src/main/resources/db/migration/V51__configuration_change_approval.sql",
        "src/main/resources/db/migration/V52__participant_certifications.sql",
        "docs/runbooks/RB-20_PARTICIPANT_CERTIFICATION.md",
    ]
    for path in required:
        require((ROOT / path).is_file(), f"missing required file: {path}")


def check_json_contracts() -> None:
    for path in [
        "compliance/release-evidence.schema.json",
        "schemas/events/schema-registry.json",
        "schemas/events/v1/outbox-dispatch.json",
        "schemas/events/baseline/outbox-dispatch.v1.json",
        "certification/spec/participant-certification.json",
    ]:
        json.loads(text(path))
    run(sys.executable, "scripts/schema/verify_event_compatibility.py")


def check_release_evidence_round_trip() -> None:
    commit = "a" * 40
    digest = "sha256:" + "b" * 64
    with tempfile.TemporaryDirectory(prefix="switching-release-evidence-") as tmp:
        root = Path(tmp)
        (root / "pom.xml").write_text("<project/>\n", encoding="utf-8")
        output = root / "release.json"
        run(
            sys.executable,
            str(ROOT / "scripts/release/build-release-evidence.py"),
            "--root", str(root),
            "--commit", commit,
            "--image-reference", f"ghcr.io/example/switching-api@{digest}",
            "--image-digest", digest,
            "--include", "pom.xml",
            "--output", str(output),
            cwd=ROOT,
        )
        run(
            sys.executable,
            str(ROOT / "scripts/release/verify-release-evidence.py"),
            str(output), "--root", str(root),
            "--expected-commit", commit, "--expected-digest", digest,
            cwd=ROOT,
        )
        (root / "pom.xml").write_text("<project>tampered</project>\n", encoding="utf-8")
        run(
            sys.executable,
            str(ROOT / "scripts/release/verify-release-evidence.py"),
            str(output), "--root", str(root),
            expect=1,
            cwd=ROOT,
        )


def check_security_and_governance_invariants() -> None:
    prod = text("src/main/resources/application-prod.yml")
    staging = text("src/main/resources/application-staging.yml")
    require("allow-legacy-messages: false" in prod, "prod must reject legacy events")
    require("allow-legacy-messages: false" in staging, "staging must reject legacy events")

    validator = text("src/main/java/com/example/switching/config/ProductionStartupValidator.java")
    require("outboxAllowLegacyMessages" in validator, "prod startup validator must reject legacy events")

    hold_repo = text("src/main/java/com/example/switching/compliance/legalhold/repository/LegalHoldRepository.java")
    require("LegalHoldStatus.RELEASE_REQUESTED" in hold_repo,
            "release-requested legal holds must continue blocking retention")

    break_glass = text("src/main/java/com/example/switching/security/breakglass/filter/BreakGlassFilter.java")
    for endpoint in ["execute-replay", "discard", "approve-release", "config-changes/[^/]+/execute"]:
        require(endpoint in break_glass, f"break-glass protection missing: {endpoint}")

    participant = text("src/main/java/com/example/switching/participant/service/ParticipantManagementService.java")
    require("ParticipantStatus.INACTIVE" in participant, "new participants must default inactive")
    require("four-eyes configuration workflow" in participant,
            "direct participant activation/status changes must be rejected")

    certification = text("src/main/java/com/example/switching/certification/service/ParticipantCertificationService.java")
    require("findFirstByBankCodeOrderByExecutedAtDescIdDesc" in certification,
            "activation must evaluate the latest certification, not any historical PASS")
    require("sensitiveDataSanitizer.sanitizeJson" in certification,
            "certification details must be sanitized before persistence")

    security = text("src/main/java/com/example/switching/security/config/SecurityConfig.java")
    require('.requestMatchers(HttpMethod.PATCH, "/api/participants/**").denyAll()' in security,
            "direct participant PATCH must be denied")
    require("BreakGlassFilter" in security, "break-glass filter is not installed")


def check_immutable_and_isolated_kubernetes() -> None:
    canary = text("k8s/canary/deployment.yaml")
    maintenance = text("k8s/database/maintenance-cronjob.yaml")
    require("@sha256:REPLACE_WITH_IMAGE_DIGEST" in canary, "canary image must be digest-pinned")
    require("@sha256:REPLACE_WITH_BACKUP_IMAGE_DIGEST" in maintenance,
            "maintenance image must be digest-pinned")
    require("readOnlyRootFilesystem: true" in canary, "canary root filesystem must be read-only")
    require("readOnlyRootFilesystem: true" in maintenance, "maintenance root filesystem must be read-only")
    require("fsGroup: 999" in maintenance, "maintenance writable volume ownership is not configured")
    require((ROOT / "k8s/canary/networkpolicy.yaml").is_file(), "canary NetworkPolicy missing")
    require("networkpolicy.yaml" in text("k8s/canary/kustomization.yaml"),
            "canary NetworkPolicy missing from kustomization")


def check_database_lifecycle() -> None:
    configmap = text("k8s/database/maintenance-configmap.yaml")
    require("database_maintenance_runs" in configmap, "CronJob must record maintenance evidence")
    require("pg_try_advisory_lock" in configmap, "maintenance concurrency lock missing")
    require("/scripts-sql/" not in configmap, "maintenance ConfigMap references an unmounted path")
    partition = text("src/main/java/com/example/switching/maintenance/service/PartitionMaintenanceService.java")
    require("@Transactional" in partition, "partition drop and evidence logging must be transactional")
    require("retention_execution_log" in partition, "retention evidence is not written")


def check_script_syntax_and_permissions() -> None:
    shell_scripts = [
        "scripts/release/progressive-rollout.sh",
        "database/maintenance/run-maintenance.sh",
    ]
    for path in shell_scripts:
        run("bash", "-n", path)
        require(os.access(ROOT / path, os.X_OK), f"script is not executable: {path}")
    python_scripts = [
        "scripts/release/build-release-evidence.py",
        "scripts/release/verify-release-evidence.py",
        "scripts/release/prometheus-gate.py",
        "scripts/slo/check_error_budget.py",
        "scripts/schema/verify_event_compatibility.py",
        "certification/scripts/run-certification.py",
    ]
    run(sys.executable, "-m", "py_compile", *python_scripts)


def check_migration_sequence() -> None:
    migrations = sorted((ROOT / "src/main/resources/db/migration").glob("V*.sql"))
    versions = []
    for migration in migrations:
        match = re.match(r"V(\d+)__", migration.name)
        if match:
            versions.append(int(match.group(1)))
    for version in range(47, 53):
        require(versions.count(version) == 1, f"expected exactly one V{version} migration")


def main() -> int:
    checks = [
        check_required_files,
        check_json_contracts,
        check_release_evidence_round_trip,
        check_security_and_governance_invariants,
        check_immutable_and_isolated_kubernetes,
        check_database_lifecycle,
        check_script_syntax_and_permissions,
        check_migration_sequence,
    ]
    for check in checks:
        check()
        print(f"PASS {check.__name__}")
    print(f"PASS phases 13-22 static acceptance ({len(checks)} checks)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except CheckFailure as exc:
        print(f"FAIL {exc}", file=sys.stderr)
        raise SystemExit(1)
