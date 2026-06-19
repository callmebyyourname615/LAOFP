#!/usr/bin/env python3
"""Static contract verifier for Phase 55A-J production Go-Live controls."""
from __future__ import annotations
import json, pathlib, py_compile, stat, subprocess, sys
try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

ROOT = pathlib.Path(__file__).resolve().parents[1]
ERRORS: list[str] = []


def fail(message: str) -> None:
    ERRORS.append(message)


def require(path: str) -> pathlib.Path:
    p = ROOT / path
    if not p.is_file():
        fail(f"missing file: {path}")
    return p


def main() -> int:
    plan_path = require("config/phase55-golive-plan.yaml")
    require("config/phase55-thresholds.yaml")
    require("config/phase55-stage-metrics.yaml")
    require("config/production-infrastructure-contract.yaml")
    require("config/phase55-reconciliation-queries.yaml")
    require("config/phase55-hypercare-policy.yaml")
    for path in [
        "docs/golive/MASTER_GO_LIVE_RUNBOOK.md",
        "docs/golive/CUTOVER_CHECKLIST.md",
        "docs/golive/ROLLBACK_CHECKLIST.md",
        "docs/golive/COMMAND_CENTER_AND_ESCALATION.md",
        "docs/golive/HYPERCARE_AND_BAU_HANDOVER.md",
        "docs/runbooks/PHASE55_PRODUCTION_GOLIVE.md",
        "k8s/production/kustomization.yaml",
        "k8s/production/default-deny-networkpolicy.yaml",
        "k8s/production/allow-ingress-networkpolicy.yaml",
        "k8s/production/break-glass-rbac.yaml",
    ]:
        require(path)

    plan = yaml.safe_load(plan_path.read_text(encoding="utf-8")) if plan_path.is_file() else {}
    phases = plan.get("phases", [])
    expected = [f"55{letter}" for letter in "ABCDEFGHIJ"]
    actual = [phase.get("id") for phase in phases]
    if actual != expected:
        fail(f"phase order mismatch: {actual}")
    if plan.get("minimumFlywayVersion") != "83":
        fail("minimum Flyway version must be 83")
    for phase in phases:
        runner = phase.get("runner", "")
        path = require(runner)
        if path.is_file() and not (path.stat().st_mode & stat.S_IXUSR):
            fail(f"runner is not executable: {runner}")
        if not phase.get("requiredForOperationalAcceptance"):
            fail(f"phase not required for operational acceptance: {phase.get('id')}")
        if not phase.get("evidence"):
            fail(f"phase has no evidence contract: {phase.get('id')}")

    shell_files = sorted((ROOT / "scripts/golive").glob("*.sh"))
    for path in shell_files:
        result = subprocess.run(["bash", "-n", str(path)], capture_output=True, text=True)
        if result.returncode:
            fail(f"shell syntax failed: {path.relative_to(ROOT)}: {result.stderr.strip()}")
        if not (path.stat().st_mode & stat.S_IXUSR):
            fail(f"shell script is not executable: {path.relative_to(ROOT)}")
    python_files = sorted((ROOT / "scripts/golive").glob("*.py")) + [ROOT / "scripts/verify_phase55_static.py"]
    for path in python_files:
        try:
            py_compile.compile(str(path), doraise=True)
        except Exception as exc:
            fail(f"Python compile failed: {path.relative_to(ROOT)}: {exc}")

    for path in sorted((ROOT / "config").glob("phase55*.yaml")) + [ROOT / "config/production-infrastructure-contract.yaml"]:
        try:
            yaml.safe_load(path.read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"YAML parse failed: {path.relative_to(ROOT)}: {exc}")
    for path in sorted((ROOT / "docs/templates").glob("phase55*.json")):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except Exception as exc:
            fail(f"JSON parse failed: {path.relative_to(ROOT)}: {exc}")

    production_text = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "k8s/production").glob("*.yaml"))
    if "0.0.0.0/0" in production_text or "::/0" in production_text:
        fail("production Kubernetes resources contain a world-open CIDR")
    if ":latest" in production_text:
        fail("production Kubernetes resources contain mutable latest tag")

    for manifest, expected_track in [(ROOT / "k8s/deployment.yaml", "stable"), (ROOT / "k8s/canary/deployment.yaml", "canary")]:
        text = manifest.read_text(encoding="utf-8") if manifest.is_file() else ""
        if "MANAGEMENT_METRICS_TAGS_APPLICATION" not in text or "value: switching-api" not in text:
            fail(f"missing application metrics tag: {manifest.relative_to(ROOT)}")
        if "MANAGEMENT_METRICS_TAGS_RELEASE_TRACK" not in text or f"value: {expected_track}" not in text:
            fail(f"missing release_track={expected_track} metrics tag: {manifest.relative_to(ROOT)}")


    canary_policy = (ROOT / "k8s/canary/networkpolicy.yaml").read_text(encoding="utf-8")
    if "0.0.0.0/0" in canary_policy or "namespaceSelector: {}" in canary_policy:
        fail("canary network policy contains broad egress")

    required_guards = {
        "55A": ["cosign verify", "syft", "verify_release_candidate.py"],
        "55B": ["live_require_production_confirmation", "verify_infrastructure_contract.py"],
        "55C": ["production-dry-run", "MigrationApplication", "migration-idempotent", "data-reconciliation.json", "snapshot-attestation-signature", "previous-application-v83-compatibility"],
        "55D": ["READS_PRODUCTION_FINANCIAL_AGGREGATES", "cutover-baseline.sha256"],
        "55E": ["render_production_network_policy.py", "collect_database_grants.sh", "verify_access_hardening.py", "secret-rotation-signature"],
        "55F": ["rollbackCommand", "two approvers", "command-center-approval-signature", "cutoverPlanSha256"],
        "55G": ["verify_decision.py", "canary-weight=5", "compare_reconciliation.py", "verify_stage_metrics.py", "render_canary_ingress.py", "render-canary-network-policy"],
        "55H": ["25 50 100", "rollback", "cosign verify-blob", "verify_stage_metrics.py", "change-window-$stage", "synthetic-$stage"],
        "55I": ["build_hypercare_report.py", "reconciliation-summary.json"],
        "55J": ["verify_operational_acceptances.py", "build_operational_acceptance.py", "scan_evidence.py", "acceptance-signature"],
    }
    by_id = {phase.get("id"): ROOT / phase.get("runner", "") for phase in phases}
    for phase, needles in required_guards.items():
        path = by_id.get(phase)
        text = path.read_text(encoding="utf-8") if path and path.is_file() else ""
        for needle in needles:
            if needle not in text:
                fail(f"{phase} missing required control marker: {needle}")

    if ERRORS:
        for error in ERRORS:
            print(f"FAIL: {error}", file=sys.stderr)
        print(f"Phase 55 static verification failed: {len(ERRORS)} issue(s)", file=sys.stderr)
        return 1
    print(f"OK: Phase 55 static contract verified ({len(phases)} phases, {len(shell_files)} shell scripts, {len(python_files)} Python files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
