#!/usr/bin/env python3
"""Fast repository-level acceptance checks for Implementation Guide items 1-3."""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise AssertionError(message)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"required file missing: {relative}")
    return path.read_text(encoding="utf-8")


def main() -> int:
    ET.parse(ROOT / "pom.xml")

    migration_profile = read("src/main/resources/application-migration.yml")
    migration_app = read("src/main/java/com/example/switching/migration/MigrationApplication.java")
    migration_job = read("k8s/migration-job.yaml")
    deployment = read("k8s/deployment.yaml")

    for token in ("FLYWAY_URL", "FLYWAY_USERNAME", "FLYWAY_PASSWORD", "clean-disabled: true"):
        if token not in migration_profile:
            fail(f"migration profile missing {token}")

    for token in ("WebApplicationType.NONE", "SpringApplication.exit", "HibernateJpaAutoConfiguration", "KafkaAutoConfiguration"):
        if token not in migration_app:
            fail(f"migration application missing {token}")

    if "initContainers:" in deployment or "flyway-migrate" in deployment:
        fail("Deployment still contains per-pod Flyway init container")

    image_placeholder = (
        "ghcr.io/REPLACE_WITH_GITHUB_REPOSITORY/"
        "switching-api@sha256:REPLACE_WITH_IMAGE_DIGEST"
    )
    if image_placeholder not in deployment or image_placeholder not in migration_job:
        fail("Deployment and migration Job must share the digest placeholder")
    if ":latest" in deployment or ":latest" in migration_job:
        fail("mutable latest tag found in Kubernetes runtime manifests")

    for token in (
        "SPRING_PROFILES_ACTIVE",
        "migration",
        "PropertiesLauncher",
        "backoffLimit: 0",
        "activeDeadlineSeconds: 300",
        "readOnlyRootFilesystem: true",
    ):
        if token not in migration_job:
            fail(f"migration Job missing {token}")

    profiled_classes = [
        "src/main/java/com/example/switching/aml/service/SanctionsListSyncService.java",
        "src/main/java/com/example/switching/aml/service/StrGenerationService.java",
        "src/main/java/com/example/switching/dispute/service/DisputeSlaEnforcementService.java",
        "src/main/java/com/example/switching/liquidity/service/LiquidityAlertService.java",
        "src/main/java/com/example/switching/maintenance/service/AggregationScheduler.java",
        "src/main/java/com/example/switching/maintenance/service/ArchiveWorkerService.java",
        "src/main/java/com/example/switching/maintenance/service/PartitionMaintenanceService.java",
        "src/main/java/com/example/switching/outbox/queue/OutboxQueueConfig.java",
        "src/main/java/com/example/switching/outbox/queue/OutboxQueueConsumer.java",
        "src/main/java/com/example/switching/outbox/worker/OutboxDispatchWorker.java",
        "src/main/java/com/example/switching/outbox/worker/OutboxRecoveryWorker.java",
        "src/main/java/com/example/switching/settlement/service/SettlementCutoffScheduler.java",
        "src/main/java/com/example/switching/webhook/service/WebhookRetryService.java",
    ]
    for relative in profiled_classes:
        if '@Profile("!migration")' not in read(relative):
            fail(f"migration profile guard missing: {relative}")

    # Future schedulers/listeners must not silently bypass the migration isolation contract.
    for java_file in (ROOT / "src/main/java").rglob("*.java"):
        source = java_file.read_text(encoding="utf-8")
        if "@Scheduled" in source or "@KafkaListener" in source:
            if '@Profile("!migration")' not in source:
                fail(f"runtime side-effect bean lacks migration guard: {java_file.relative_to(ROOT)}")

    ci = read(".github/workflows/ci.yml")
    if not re.search(r"\./mvnw[^\n]*test|\./mvnw[\s\S]{0,160}\btest\b", ci):
        fail("CI does not run Maven test")
    if "-Dtest=" in ci:
        fail("CI still contains a test allowlist")
    if "target/surefire-reports" not in ci:
        fail("CI does not upload Surefire reports")
    for token in ("security-gates", "needs: [test, security-gates]"):
        if token not in ci:
            fail(f"CI aggregate gate missing {token}")

    security = read(".github/workflows/security.yml")
    for token in ("gitleaks", "dependency-check-maven", "trivy-action", "codeql-action"):
        if token not in security.lower():
            fail(f"security workflow missing {token}")

    build = read(".github/workflows/build.yml")
    for token in ("workflow_run.head_sha", "verify_migration_image.sh", "docker push", "sha256:"):
        if token not in build:
            fail(f"immutable build workflow missing {token}")
    if "switching-api:latest" in build:
        fail("immutable build workflow publishes latest")

    deploy = read(".github/workflows/deploy.yml")
    if deploy.index("migration-job.yaml") > deploy.index("deployment.yaml"):
        fail("deploy workflow does not render migration before Deployment")
    for token in ("condition=complete", "rollout status", "image_digest"):
        if token not in deploy:
            fail(f"deploy workflow missing {token}")

    for script in sorted((ROOT / "scripts").glob("*.sh")):
        subprocess.run(["bash", "-n", str(script)], check=True)

    print("Phase 1 static acceptance checks: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, ET.ParseError, subprocess.CalledProcessError) as exc:
        print(f"Phase 1 static acceptance checks: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
