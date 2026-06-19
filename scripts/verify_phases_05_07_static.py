#!/usr/bin/env python3
"""Static acceptance checks for phases 05-07.

This script deliberately avoids network/cluster dependencies. CI remains authoritative for
Maven/Testcontainers and live Vault/ESO/Prometheus reconciliation.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.is_file():
        fail(f"missing file: {relative}")
        return ""
    return path.read_text(encoding="utf-8", errors="strict")


def require(relative: str, *needles: str) -> str:
    text = read(relative)
    for needle in needles:
        if needle not in text:
            fail(f"{relative} missing required content: {needle}")
    return text


def parse_yaml(relative: str) -> list[object]:
    text = read(relative)
    try:
        return list(yaml.safe_load_all(text))
    except Exception as exc:  # noqa: BLE001
        fail(f"invalid YAML {relative}: {exc}")
        return []


def parse_json(relative: str) -> object:
    text = read(relative)
    try:
        return json.loads(text)
    except Exception as exc:  # noqa: BLE001
        fail(f"invalid JSON {relative}: {exc}")
        return {}


# Phase 05 — provider safety and atomic import.
for relative in (
    "src/main/java/com/example/switching/aml/sanctions/provider/SanctionsProvider.java",
    "src/main/java/com/example/switching/aml/sanctions/provider/BolFiuSanctionsProvider.java",
    "src/main/java/com/example/switching/aml/sanctions/provider/OfacSanctionsProvider.java",
    "src/main/java/com/example/switching/aml/sanctions/provider/UnSanctionsProvider.java",
    "src/main/java/com/example/switching/aml/sanctions/SanctionsImportService.java",
    "src/main/java/com/example/switching/aml/sanctions/SanctionsFreshnessMonitor.java",
    "src/main/java/com/example/switching/aml/sanctions/SanctionsFreshnessHealthIndicator.java",
    "src/main/resources/db/migration/V45__sanctions_provider_import.sql",
):
    read(relative)

http_client = require(
    "src/main/java/com/example/switching/aml/sanctions/provider/SanctionsHttpClient.java",
    "BodyHandlers.ofInputStream",
    "readNBytes(limit + 1)",
    "connectTimeout",
    "Retryable HTTP",
    "Sanctions provider must use HTTPS",
)
if "BodyHandlers.ofByteArray" in http_client:
    fail("sanctions HTTP client buffers an unbounded body before enforcing the limit")

secure_xml = require(
    "src/main/java/com/example/switching/aml/sanctions/parser/SecureXml.java",
    "XMLInputFactory.SUPPORT_DTD",
    "XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES",
    "DTD and entity declarations are forbidden",
)
if "true" in re.sub(r"//.*", "", secure_xml).split("IS_SUPPORTING_EXTERNAL_ENTITIES", 1)[-1][:80]:
    fail("external XML entities appear enabled")

import_service = require(
    "src/main/java/com/example/switching/aml/sanctions/SanctionsImportService.java",
    "sanctions_import_staging",
    "pg_advisory_xact_lock",
    "ON CONFLICT (list_type, provider_uid)",
    "is_active = FALSE",
    "recordRejected",
)
if "TRUNCATE sanctions_lists" in import_service.upper():
    fail("sanctions import truncates the live table")

migration = require(
    "src/main/resources/db/migration/V45__sanctions_provider_import.sql",
    "CREATE TABLE sanctions_import_runs",
    "CREATE TABLE sanctions_import_staging",
    "CREATE UNIQUE INDEX ux_sanctions_provider_uid",
    "USING GIN(aliases_normalized)",
    "'ERROR'",
)
if "DROP TABLE sanctions_lists" in migration.upper():
    fail("sanctions migration drops the live table")

sync_service = require(
    "src/main/java/com/example/switching/aml/service/SanctionsListSyncService.java",
    "retaining last-known-good data",
    "provider.fetchSnapshot()",
    "importService.importSnapshot",
)
require(
    "src/main/java/com/example/switching/config/ProductionStartupValidator.java",
    "Complete a successful provider import before production",
    "sanctions data is stale",
    "must use HTTPS in production",
)

# Fixtures and coverage.
for relative in (
    "src/test/resources/sanctions/ofac-sample.xml",
    "src/test/resources/sanctions/un-sample.xml",
    "src/test/resources/sanctions/bol-sample.json",
    "src/test/java/com/example/switching/aml/sanctions/parser/OfacXmlSanctionsParserTest.java",
    "src/test/java/com/example/switching/aml/sanctions/parser/UnXmlSanctionsParserTest.java",
    "src/test/java/com/example/switching/aml/sanctions/parser/BolFiuJsonSanctionsParserTest.java",
    "src/test/java/com/example/switching/aml/sanctions/SanctionsImportIntegrationTest.java",
    "src/test/java/com/example/switching/aml/sanctions/SanctionsAliasScreeningIntegrationTest.java",
):
    read(relative)
require(
    "src/test/java/com/example/switching/aml/sanctions/parser/OfacXmlSanctionsParserTest.java",
    "rejectsExternalEntityPayload",
)
require(
    "src/test/java/com/example/switching/aml/sanctions/SanctionsImportIntegrationTest.java",
    "SoftDeletes",
    "LastKnownGood",
)

# Phase 06 — external secret boundaries and transport hardening.
for relative in (
    "k8s/external-secrets/vault-secret-store.yaml",
    "k8s/external-secrets/application-secrets.yaml",
    "k8s/external-secrets/migration-secrets.yaml",
    "k8s/external-secrets/trust-bundle.yaml",
):
    documents = parse_yaml(relative)
    if not documents:
        continue
    if "external-secrets.io/v1" not in read(relative):
        fail(f"{relative} must use external-secrets.io/v1")

secret_guard = require("k8s/secret.yaml", "kind: List", "items: []")
if re.search(r"(?m)^kind:\s*Secret\s*$", secret_guard):
    fail("k8s/secret.yaml still contains a raw Secret")

for relative in (
    "k8s/external-secrets/application-secrets.yaml",
    "k8s/external-secrets/migration-secrets.yaml",
    "k8s/external-secrets/trust-bundle.yaml",
):
    require(relative, "creationPolicy: Orphan", "deletionPolicy: Retain")

store = require(
    "k8s/external-secrets/vault-secret-store.yaml",
    "serviceAccountRef:",
    "audiences:",
    "- vault",
    "mountPath:",
)
app_secret = read("k8s/external-secrets/application-secrets.yaml")
migration_secret = read("k8s/external-secrets/migration-secrets.yaml")
if "FLYWAY_PASSWORD" in app_secret:
    fail("application ExternalSecret exposes Flyway password")
if "DB_PASSWORD" in migration_secret:
    fail("migration ExternalSecret exposes application DB password")

prod = require(
    "src/main/resources/application-prod.yml",
    "ssl.endpoint.identification.algorithm",
    "SASL_SSL",
    "auth-method: ${WEBHOOK_VAULT_AUTH_METHOD:kubernetes}",
)
if "token: ${VAULT_TOKEN" in prod:
    fail("production profile exposes a static Vault token property")
configmap = require(
    "k8s/configmap.yaml",
    "sslmode=verify-full",
    "sslrootcert=",
    "KAFKA_SECURITY_PROTOCOL: \"SASL_SSL\"",
    "KAFKA_SSL_ENDPOINT_IDENTIFICATION_ALGORITHM: \"https\"",
    "VAULT_ADDR: \"https://REPLACE_WITH_VAULT_HOST:8200\"",
)
require(
    "src/main/java/com/example/switching/webhook/crypto/KubernetesVaultTokenProvider.java",
    "service-account JWT",
    "lease_duration",
    "renewalSkew",
)
require(
    "scripts/check_prod_config.sh",
    "sslmode=verify-full",
    "must not contain a static VAULT_TOKEN",
    "external-secrets.io/v1",
)
for relative in (
    "scripts/bootstrap_vault_kubernetes_auth.sh",
    "scripts/migrate_to_external_secrets.sh",
    "scripts/wait_for_external_secrets.sh",
):
    path = ROOT / relative
    read(relative)
    if path.exists() and not path.stat().st_mode & 0o111:
        fail(f"script is not executable: {relative}")

# Phase 07 — metrics, dashboards, alerts and runbooks.
collector = require(
    "src/main/java/com/example/switching/observability/OperationalMetricsCollector.java",
    "switching.ops.transactions.pending",
    "switching.ops.outbox.pending",
    "switching.ops.settlement.failed.today",
    "switching.ops.aml.str.pending",
    "switching.ops.webhook.failed.final",
    "switching.ops.metrics.refresh.success",
    "retaining last-known-good values",
)
metric_names = set(re.findall(r'"(switching\.ops\.[a-z0-9.]+)"', collector))
if len(metric_names) < 15:  # 13 business gauges + 2 refresh gauges
    fail(f"expected at least 15 operational metric names, found {len(metric_names)}")

service_monitor_docs = parse_yaml("monitoring/prometheus/servicemonitor.yaml")
if not service_monitor_docs or service_monitor_docs[0].get("kind") != "ServiceMonitor":
    fail("ServiceMonitor manifest missing/invalid")
# Selector must match only the private management Service.
require("monitoring/prometheus/servicemonitor.yaml", "monitoring: enabled", "port: management")
require("k8s/service.yaml", "name: switching-api-management", "monitoring: enabled")

rule_docs = parse_yaml("monitoring/prometheus/prometheus-rules.yaml")
alerts: list[str] = []
if rule_docs:
    for group in rule_docs[0].get("spec", {}).get("groups", []):
        for rule in group.get("rules", []):
            if "alert" in rule:
                alerts.append(rule["alert"])
if len(alerts) != 11:
    fail(f"expected exactly 11 alert rules, found {len(alerts)}")
if len(set(alerts)) != len(alerts):
    fail("duplicate alert names found")
for alert in alerts:
    if alert not in read("monitoring/prometheus/prometheus-rules.yaml"):
        fail(f"alert missing from rules: {alert}")
if read("monitoring/prometheus/prometheus-rules.yaml").count("runbook_url:") != 11:
    fail("every alert must have a runbook_url")

expected_dashboards = {
    "switching-api.json",
    "switching-transactions.json",
    "switching-settlement.json",
    "switching-aml.json",
}
actual_dashboards = {path.name for path in (ROOT / "monitoring/grafana/dashboards").glob("*.json")}
missing_dashboards = expected_dashboards - actual_dashboards
if missing_dashboards:
    fail(f"required Phase 7 dashboards missing: {sorted(missing_dashboards)}")
for name in expected_dashboards:
    dashboard = parse_json(f"monitoring/grafana/dashboards/{name}")
    if not isinstance(dashboard, dict) or len(dashboard.get("panels", [])) < 8:
        fail(f"dashboard {name} must contain at least eight panels")
    if dashboard.get("refresh") != "30s":
        fail(f"dashboard {name} refresh must be 30s")

for relative in (
    "docs/runbooks/RB-08_MONITORING_AND_API_SLO.md",
    "docs/runbooks/RB-09_DATABASE_AND_QUEUE_PRESSURE.md",
    "docs/runbooks/RB-10_TRANSACTION_SETTLEMENT_AND_WEBHOOK.md",
    "docs/runbooks/RB-11_AML_SANCTIONS_AND_STR.md",
    "docs/aml/SANCTIONS_PROVIDER_ONBOARDING.md",
    "docs/security/VAULT_EXTERNAL_SECRETS_RUNBOOK.md",
    "docs/implementation/PHASES_05_TO_07_IMPLEMENTATION.md",
):
    read(relative)

# Parse every changed YAML/JSON and check all shell scripts syntactically.
for path in [*ROOT.glob("src/main/resources/application*.yml"), *ROOT.rglob("k8s/**/*.yaml"), *ROOT.rglob("monitoring/**/*.yaml")]:
    if path.is_file():
        try:
            list(yaml.safe_load_all(path.read_text(encoding="utf-8")))
        except Exception as exc:  # noqa: BLE001
            fail(f"invalid YAML {path.relative_to(ROOT)}: {exc}")
for path in (ROOT / "monitoring/grafana/dashboards").glob("*.json"):
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:  # noqa: BLE001
        fail(f"invalid dashboard JSON {path.name}: {exc}")
for path in (ROOT / "scripts").glob("*.sh"):
    result = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
    if result.returncode:
        fail(f"shell syntax failed for {path.name}: {result.stderr.strip()}")

# Compile/run the pure-JDK OFAC/UN parser slice. This catches record/lambda/type errors even
# when Maven dependencies are unavailable in the execution environment.
try:
    with tempfile.TemporaryDirectory() as temp_name:
        temp = Path(temp_name)
        source = temp / "src"
        classes = temp / "classes"
        (source / "org/springframework/stereotype").mkdir(parents=True)
        (source / "org/springframework/stereotype/Component.java").write_text(
            "package org.springframework.stereotype; public @interface Component {}\n",
            encoding="utf-8",
        )
        copies = [
            "src/main/java/com/example/switching/aml/sanctions/model/SanctionsEntityType.java",
            "src/main/java/com/example/switching/aml/sanctions/model/SanctionsEntry.java",
            "src/main/java/com/example/switching/aml/sanctions/model/SanctionsSnapshot.java",
            "src/main/java/com/example/switching/aml/sanctions/parser/SecureXml.java",
            "src/main/java/com/example/switching/aml/sanctions/parser/OfacXmlSanctionsParser.java",
            "src/main/java/com/example/switching/aml/sanctions/parser/UnXmlSanctionsParser.java",
            "src/main/java/com/example/switching/aml/sanctions/provider/SanctionsProviderException.java",
        ]
        for relative in copies:
            target = source / Path(relative).relative_to("src/main/java")
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((ROOT / relative).read_bytes())
        harness = source / "Harness.java"
        harness.write_text(
            '''import java.nio.file.*; import com.example.switching.aml.sanctions.parser.*;\n'''
            '''public class Harness { public static void main(String[] a) throws Exception {\n'''
            '''var o=new OfacXmlSanctionsParser().parse(Files.readAllBytes(Path.of(a[0])),"O");\n'''
            '''if(o.size()!=2||!o.get(0).providerUid().equals("OFAC:12345"))throw new AssertionError(o);\n'''
            '''var u=new UnXmlSanctionsParser().parse(Files.readAllBytes(Path.of(a[1])),"U");\n'''
            '''if(u.size()!=2||!u.get(0).providerUid().equals("UN:QDi.100"))throw new AssertionError(u);\n'''
            '''String x="<?xml version=\\"1.0\\"?><!DOCTYPE x [<!ENTITY e SYSTEM \\"file:///etc/passwd\\">]><sdnList/>";\n'''
            '''boolean r=false;try{new OfacXmlSanctionsParser().parse(x.getBytes(),"X");}catch(RuntimeException e){r=true;}\n'''
            '''if(!r)throw new AssertionError("XXE accepted"); }}\n''',
            encoding="utf-8",
        )
        classes.mkdir()
        java_files = [str(path) for path in source.rglob("*.java")]
        compile_result = subprocess.run(
            ["javac", "--release", "21", "-d", str(classes), *java_files],
            capture_output=True,
            text=True,
        )
        if compile_result.returncode:
            fail(f"pure-JDK parser compile failed: {compile_result.stderr.strip()}")
        else:
            run_result = subprocess.run(
                [
                    "java", "-cp", str(classes), "Harness",
                    str(ROOT / "src/test/resources/sanctions/ofac-sample.xml"),
                    str(ROOT / "src/test/resources/sanctions/un-sample.xml"),
                ],
                capture_output=True,
                text=True,
            )
            if run_result.returncode:
                fail(f"pure-JDK parser harness failed: {run_result.stderr.strip()}")
except FileNotFoundError as exc:
    fail(f"Java 21 toolchain unavailable for parser check: {exc}")

if errors:
    print("Phases 05-07 static acceptance checks: FAIL", file=sys.stderr)
    for error in errors:
        print(f"  - {error}", file=sys.stderr)
    raise SystemExit(1)

print("Phases 05-07 static acceptance checks: PASS")
