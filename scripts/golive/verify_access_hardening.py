#!/usr/bin/env python3
"""Verify production Kubernetes hardening, DB privilege separation and rotation attestations."""
from __future__ import annotations
import argparse, datetime as dt, json, pathlib, re
try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc


def load_docs(path: pathlib.Path) -> list[dict]:
    return [doc for doc in yaml.safe_load_all(path.read_text(encoding="utf-8")) if isinstance(doc, dict)]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--deployment", required=True)
    ap.add_argument("--migration-job", required=True)
    ap.add_argument("--network-policy", required=True)
    ap.add_argument("--database-grants", required=True)
    ap.add_argument("--rotation-attestation", required=True)
    ap.add_argument("--reference", required=True)
    ap.add_argument("--rc-id", required=True)
    ap.add_argument("--git-commit", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--kubernetes-output", required=True)
    args = ap.parse_args()
    errors, checks = [], []

    def check(check_id: str, condition: bool, detail: str):
        checks.append({"id": check_id, "status": "PASS" if condition else "FAIL", "detail": detail})
        if not condition:
            errors.append(f"{check_id}: {detail}")

    deployment = load_docs(pathlib.Path(args.deployment))[0]
    pod = deployment["spec"]["template"]["spec"]
    containers = pod.get("containers", [])
    check("run-as-non-root", pod.get("securityContext", {}).get("runAsNonRoot") is True, "pod must run as non-root")
    check("service-account-token", pod.get("automountServiceAccountToken") is False, "default service-account token must be disabled")
    check("dedicated-service-account", pod.get("serviceAccountName") not in (None, "", "default"), "dedicated service account required")
    for index, container in enumerate(containers):
        security = container.get("securityContext", {})
        prefix = f"container-{index}"
        check(prefix + "-read-only-root", security.get("readOnlyRootFilesystem") is True, "read-only root filesystem required")
        check(prefix + "-no-privilege-escalation", security.get("allowPrivilegeEscalation") is False, "privilege escalation must be disabled")
        check(prefix + "-drop-all-capabilities", "ALL" in security.get("capabilities", {}).get("drop", []), "all Linux capabilities must be dropped")
        check(prefix + "-resources", bool(container.get("resources", {}).get("requests")) and bool(container.get("resources", {}).get("limits")), "requests and limits required")
        image = container.get("image", "")
        check(prefix + "-digest-pinned", bool(re.search(r"@sha256:[a-f0-9]{64}$", image)), "image must be digest pinned")

    migration = load_docs(pathlib.Path(args.migration_job))[0]
    check("migration-backoff-zero", migration.get("spec", {}).get("backoffLimit") == 0, "migration job must not retry implicitly")
    check("migration-deadline", 0 < int(migration.get("spec", {}).get("activeDeadlineSeconds", 0)) <= 1800, "migration deadline must be bounded")

    network = load_docs(pathlib.Path(args.network_policy))[0]
    serialized = json.dumps(network)
    check("network-no-world-cidr", "0.0.0.0/0" not in serialized and "::/0" not in serialized, "world-open CIDRs are forbidden")
    check("network-ingress-egress", set(network.get("spec", {}).get("policyTypes", [])) == {"Ingress", "Egress"}, "both ingress and egress must be governed")

    grants = json.loads(pathlib.Path(args.database_grants).read_text(encoding="utf-8"))
    check("db-app-no-ddl", grants.get("applicationUserHasDdl") is False, "application DB user must not have DDL")
    check("db-app-no-schema-create", grants.get("applicationUserCanCreateInPublic") is False, "application DB user must not CREATE in public schema")
    check("db-app-no-object-ownership", grants.get("applicationUserOwnsObjects") is False, "application DB user must not own database objects")
    check("db-flyway-schema-create", grants.get("flywayUserCanCreateInPublic") is True, "Flyway DB user must be able to CREATE in public schema")
    check("db-app-no-superuser", grants.get("applicationUserIsSuperuser") is False, "application DB user must not be superuser")
    check("db-flyway-separated", grants.get("applicationUser") != grants.get("flywayUser"), "application and Flyway users must differ")
    check("db-flyway-not-superuser", grants.get("flywayUserIsSuperuser") is False, "Flyway user must not be superuser")

    rotation = json.loads(pathlib.Path(args.rotation_attestation).read_text(encoding="utf-8"))
    check("rotation-release-identity", rotation.get("releaseReference") == args.reference and rotation.get("releaseCandidateId") == args.rc_id and rotation.get("gitCommit") == args.git_commit, "secret rotation attestation release identity must match")
    rotated = dt.datetime.fromisoformat(rotation.get("rotatedAt", "").replace("Z", "+00:00"))
    age_days = (dt.datetime.now(dt.timezone.utc) - rotated).total_seconds() / 86400
    approvers = {item for item in rotation.get("approvedBy", []) if item}
    check("rotation-recent", 0 <= age_days <= 90, "production secret rotation must be within 90 days")
    check("rotation-two-person", len(approvers) >= 2, "secret rotation requires two distinct approvers")
    check("rotation-no-secret-values", not any(key.lower() in {"password", "secret", "token", "value"} for key in rotation), "attestation must not contain secret values")
    try:
        break_glass_expiry = dt.datetime.fromisoformat(rotation.get("breakGlassAccessExpiresAt", "").replace("Z", "+00:00"))
        break_glass_hours = (break_glass_expiry - dt.datetime.now(dt.timezone.utc)).total_seconds() / 3600
        valid_break_glass = 0 < break_glass_hours <= 24
    except Exception:
        valid_break_glass = False
    check("break-glass-expiry", valid_break_glass, "break-glass access must expire within the next 24 hours")

    kube_report = {"schemaVersion": 1, "status": "PASS" if not [e for e in errors if e.startswith(("run-", "service-", "dedicated-", "container-", "migration-", "network-"))] else "FAIL", "checks": checks}
    pathlib.Path(args.kubernetes_output).write_text(json.dumps(kube_report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    report = {"schemaVersion": 1, "status": "PASS" if not errors else "FAIL", "checks": checks, "errors": errors}
    pathlib.Path(args.output).write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(json.dumps({"status": report["status"], "checks": len(checks)}, sort_keys=True))
    return 0 if not errors else 2


if __name__ == "__main__":
    raise SystemExit(main())
