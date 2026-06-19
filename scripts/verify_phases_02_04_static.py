#!/usr/bin/env python3
"""Repository-level acceptance checks for Implementation Guide phases 2-4."""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]


def fail(message: str) -> None:
    raise AssertionError(message)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"required file missing: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, *tokens: str) -> str:
    content = read(relative)
    for token in tokens:
        if token not in content:
            fail(f"{relative} missing required token: {token}")
    return content


def parse_yaml_documents(relative: str) -> None:
    path = ROOT / relative
    try:
        list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
    except yaml.YAMLError as exc:
        fail(f"invalid YAML in {relative}: {exc}")


def main() -> int:
    ET.parse(ROOT / "pom.xml")

    # Phase 2 — immutable image.
    require(
        "pom.xml",
        "<goal>build-info</goal>",
        "<commit>${build.commit}</commit>",
        "<image>${build.image}</image>",
    )
    require(
        "Dockerfile",
        "ARG BUILD_COMMIT",
        "org.opencontainers.image.revision",
        "USER switching",
    )
    deployment = require(
        "k8s/deployment.yaml",
        "switching-api@sha256:REPLACE_WITH_IMAGE_DIGEST",
        "imagePullPolicy: IfNotPresent",
    )
    migration_job = require(
        "k8s/migration-job.yaml",
        "switching-api@sha256:REPLACE_WITH_IMAGE_DIGEST",
        "configMapRef:",
        "secretRef:",
    )
    if ":latest" in deployment or ":latest" in migration_job:
        fail("mutable :latest image reference found in Kubernetes manifests")
    require(
        ".github/workflows/build.yml",
        "workflow_run.head_sha",
        "docker push",
        "digest: sha256:",
        "verify_migration_image.sh",
    )
    require(
        ".github/workflows/deploy.yml",
        "image_digest",
        "condition=complete",
        "rollout status",
        "EXPECTED_IMAGE",
    )
    require(".github/workflows/rollback.yml", "rollout undo", "rollout status")

    # Phase 3 — full test/security gate.
    ci = require(
        ".github/workflows/ci.yml",
        "./mvnw --batch-mode --no-transfer-progress test",
        "target/surefire-reports/",
        "needs: [test, security-gates]",
    )
    if "-Dtest=" in ci:
        fail("CI contains a test allowlist")
    security = read(".github/workflows/security.yml").lower()
    for token in ("gitleaks", "dependency-check-maven", "trivy-action", "codeql-action"):
        if token not in security:
            fail(f"security workflow missing {token}")

    # Phase 4 — webhook secret encryption and rotation.
    require(
        "src/main/resources/db/migration/V43__webhook_secret_encryption.sql",
        "secret_ciphertext",
        "secret_key_id",
        "previous_secret_ciphertext",
        "previous_secret_expires_at",
    )
    require(
        "src/main/resources/db/migration/V44__drop_webhook_secret_plain.sql",
        "refusing to drop secret_plain",
        "DROP COLUMN secret_plain",
        "secret_ciphertext SET NOT NULL",
    )
    require(
        "src/main/java/com/example/switching/migration/MigrationLifecycleRunner.java",
        'MigrationVersion.fromVersion("43")',
        "backfillService.backfill()",
        "finalFlyway.migrate()",
    )
    require(
        "src/main/java/com/example/switching/migration/WebhookSecretBackfillService.java",
        "SELECT id, secret_plain",
        "secretEncryptionService.encrypt",
        "secret_ciphertext = ?",
    )
    require(
        "src/main/java/com/example/switching/webhook/crypto/EnvelopeSecretEncryptionService.java",
        "AES/GCM/NoPadding",
        "keyEncryptionService.wrapKey",
        "keyEncryptionService.unwrapKey",
        "Arrays.fill(dek",
    )
    require(
        "src/main/java/com/example/switching/webhook/crypto/VaultTransitKeyEncryptionService.java",
        "X-Vault-Token",
        "/encrypt/",
        "/decrypt/",
        "Vault Transit request failed",
    )
    entity = require(
        "src/main/java/com/example/switching/webhook/entity/WebhookRegistrationEntity.java",
        'name = "secret_ciphertext"',
        'name = "secret_key_id"',
        'name = "secret_version"',
    )
    if "secret_plain" in entity or "secretPlain" in entity:
        fail("JPA entity still maps plaintext webhook secret")

    service = require(
        "src/main/java/com/example/switching/webhook/service/WebhookDeliveryService.java",
        "secretEncryptionService.decrypt",
        "SecretEncryptionException",
        "Signing secret unavailable",
    )
    if "getSecretPlain" in service:
        fail("delivery service still reads plaintext secret")
    require(
        "src/main/java/com/example/switching/webhook/service/WebhookSecretRotationService.java",
        "WEBHOOK_SECRET_ROTATED",
        "setPreviousSecretCiphertext",
        "setPreviousSecretExpiresAt",
        "auditLogService.log",
    )
    require(
        "src/main/java/com/example/switching/webhook/service/WebhookHttpSender.java",
        "X-Webhook-Signature-Previous",
    )
    require(
        "src/main/java/com/example/switching/config/ProductionStartupValidator.java",
        "must be vault-transit in production",
        "secret_plain still exists",
        "contains rows without encrypted signing secrets",
    )
    prod = require(
        "src/main/resources/application-prod.yml",
        "enabled: false",
        "provider: ${WEBHOOK_ENCRYPTION_PROVIDER:vault-transit}",
        "auth-method: ${WEBHOOK_VAULT_AUTH_METHOD:kubernetes}",
    )
    if "token: ${VAULT_TOKEN" in prod:
        fail("production profile must not expose static Vault token configuration")
    if "WEBHOOK_LOCAL_MASTER_KEY_BASE64" in prod:
        fail("production profile exposes local webhook master-key configuration")

    # No application code may persist/read the legacy column after V44. The only
    # allowed references are the V20/V43/V44 migration path and migration backfill.
    allowed_plaintext_references = {
        Path("src/main/resources/db/migration/V20__webhook_tables.sql"),
        Path("src/main/resources/db/migration/V43__webhook_secret_encryption.sql"),
        Path("src/main/resources/db/migration/V44__drop_webhook_secret_plain.sql"),
        Path("src/main/java/com/example/switching/migration/WebhookSecretBackfillService.java"),
        Path("src/main/java/com/example/switching/config/ProductionStartupValidator.java"),
        Path("src/test/java/com/example/switching/migration/MigrationApplicationIntegrationTest.java"),
    }
    for path in (ROOT / "src").rglob("*"):
        if not path.is_file():
            continue
        relative = path.relative_to(ROOT)
        text = path.read_text(encoding="utf-8", errors="ignore")
        if ("secret_plain" in text or "secretPlain" in text) and relative not in allowed_plaintext_references:
            fail(f"unexpected plaintext webhook secret reference: {relative}")

    for relative in (
        "src/test/java/com/example/switching/webhook/crypto/EnvelopeSecretEncryptionServiceTest.java",
        "src/test/java/com/example/switching/webhook/service/WebhookSecretRotationServiceTest.java",
        "src/test/java/com/example/switching/webhook/service/WebhookHttpSenderTest.java",
        "src/test/java/com/example/switching/migration/MigrationApplicationIntegrationTest.java",
    ):
        read(relative)

    for relative in (
        "src/main/resources/application.yml",
        "src/main/resources/application-prod.yml",
        "src/main/resources/application-staging.yml",
        "src/main/resources/application-migration.yml",
        "src/test/resources/application-test.yml",
        "k8s/configmap.yaml",
        "k8s/secret.yaml",
        "k8s/deployment.yaml",
        "k8s/migration-job.yaml",
        ".github/workflows/ci.yml",
        ".github/workflows/build.yml",
        ".github/workflows/deploy.yml",
        ".github/workflows/rollback.yml",
        ".github/workflows/security.yml",
    ):
        parse_yaml_documents(relative)

    for script in sorted((ROOT / "scripts").glob("*.sh")):
        subprocess.run(["bash", "-n", str(script)], check=True)

    # Catch obvious unresolved image/config placeholders in generated source, but
    # retain explicit REPLACE_* templates in example Kubernetes files.
    if not re.search(r"sha256:\[a-f0-9\]\{64\}", read("scripts/render_k8s_image.sh")):
        fail("image renderer does not validate a full sha256 digest")

    print("Phases 02-04 static acceptance checks: PASS")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, ET.ParseError, subprocess.CalledProcessError) as exc:
        print(f"Phases 02-04 static acceptance checks: FAIL: {exc}", file=sys.stderr)
        raise SystemExit(1)
