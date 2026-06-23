#!/usr/bin/env python3
import json
import os
import pathlib
import re
import subprocess
import sys
import tempfile

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required: python -m pip install PyYAML==6.0.2") from exc

ROOT = pathlib.Path(__file__).resolve().parents[1]
required = [
    "AGENT/PHASE_73A_73J_IMPLEMENTATION_CHECKLIST.md",
    "config/phase73-chaos-policy.yaml",
    "schemas/phase73/phase73-result.schema.json",
    "schemas/phase73/chaos-approval.schema.json",
    "schemas/phase73/chaos-attestation.schema.json",
    "schemas/phase73/resilience-bundle.schema.json",
    "scripts/phase73/common.sh",
    "scripts/phase73/execute_experiment.sh",
    "scripts/phase73/run_phase73.sh",
    "scripts/phase73/73A-chaos-platform-readiness.sh",
    "scripts/phase73/73B-pod-kill.sh",
    "scripts/phase73/73C-database-network-loss.sh",
    "scripts/phase73/73D-kafka-network-delay.sh",
    "scripts/phase73/73E-object-storage-network-loss.sh",
    "scripts/phase73/73F-external-api-delay.sh",
    "scripts/phase73/73G-dns-error.sh",
    "scripts/phase73/73H-resource-exhaustion-certification.sh",
    "scripts/phase73/73I-financial-integrity-verification.sh",
    "scripts/phase73/73J-resilience-bundle.sh",
    "docs/phase73/PHASE73_OVERVIEW.md",
    "docs/phase73/PHASE73_OPERATOR_RUNBOOK.md",
    "docs/phase73/PHASE73_EXIT_CRITERIA.md",
    ".github/workflows/phase73-chaos-certification.yml",
]
errors = []
for rel in required:
    if not (ROOT / rel).is_file():
        errors.append(f"missing required file: {rel}")

for schema in (ROOT / "schemas/phase73").glob("*.json"):
    try:
        json.loads(schema.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"invalid JSON schema {schema.relative_to(ROOT)}: {exc}")

policy_path = ROOT / "config/phase73-chaos-policy.yaml"
if policy_path.is_file():
    try:
        policy = yaml.safe_load(policy_path.read_text(encoding="utf-8"))
        if policy.get("environment") != "uat": errors.append("policy environment must be uat")
        if policy.get("productionExecutionAllowed") is not False: errors.append("production execution must be denied")
        if int(policy["execution"]["maximumConcurrentExperiments"]) != 1: errors.append("maximumConcurrentExperiments must be 1")
        scenarios = policy.get("scenarios") or []
        ids = [item.get("id") for item in scenarios]
        if len(ids) != 8 or len(set(ids)) != 8: errors.append("policy must define 8 unique scenarios")
        for item in scenarios:
            path = ROOT / str(item.get("manifest", ""))
            if not path.is_file(): errors.append(f"missing scenario manifest: {item.get('manifest')}")
        thresholds = policy.get("thresholds") or {}
        for key in ["maximumDataLossCount", "maximumDuplicateReplayCount", "maximumBalanceMismatchCount", "maximumOutboxBacklogGrowth"]:
            if thresholds.get(key) != 0: errors.append(f"{key} must remain zero")
    except Exception as exc:
        errors.append(f"invalid Phase 73 policy: {exc}")

shells = sorted((ROOT / "scripts/phase73").glob("*.sh"))
for shell in shells:
    result = subprocess.run(["bash", "-n", str(shell)], capture_output=True, text=True)
    if result.returncode:
        errors.append(f"shell syntax failed {shell.relative_to(ROOT)}: {result.stderr.strip()}")

render_env = os.environ.copy()
render_env.update({
    "PHASE73_NAMESPACE": "switching",
    "PHASE73_APP_LABEL": "switching-api",
    "PHASE73_BASE_URL": "http://switching-api.switching.svc.cluster.local:8080",
    "PHASE73_RUN_ID": "static-check",
    "PHASE73_RUN_ID_SAFE": "static-check",
    "PHASE73_EXPERIMENT_DURATION": "1",
    "PHASE73_COMMAND_TIMEOUT_SECONDS": "30",
    "PHASE73_APPROVAL_MAX_AGE_MINUTES": "120",
    "PHASE73_REQUIRED_SCENARIOS_JSON": "[]",
    "PHASE73_DATABASE_CIDRS_JSON": '["10.0.0.10/32"]',
    "PHASE73_KAFKA_CIDRS_JSON": '["10.0.0.20/32"]',
    "PHASE73_OBJECT_STORAGE_CIDRS_JSON": '["10.0.0.30/32"]',
    "PHASE73_EXTERNAL_API_CIDRS_JSON": '["10.0.0.40/32"]',
    "PHASE73_DNS_PATTERNS_JSON": '["*.uat.internal"]',
    "PHASE73_CPU_WORKERS": "1",
    "PHASE73_CPU_LOAD_PERCENT": "70",
    "PHASE73_MEMORY_WORKERS": "1",
    "PHASE73_MEMORY_SIZE": "256MB",
})
for manifest in sorted((ROOT / "chaos/phase73/experiments").glob("*.yaml")):
    try:
        source = manifest.read_text(encoding="utf-8")
        pattern = re.compile(r"\$\{([A-Z0-9_]+)\}")
        missing = sorted({name for name in pattern.findall(source) if name not in render_env})
        if missing:
            errors.append(f"manifest variables missing {manifest.relative_to(ROOT)}: {missing}")
            continue
        rendered = pattern.sub(lambda match: render_env[match.group(1)], source)
        doc = yaml.safe_load(rendered)
        if not isinstance(doc, dict):
            errors.append(f"rendered manifest is not an object: {manifest.name}")
            continue
        if doc.get("apiVersion") != "chaos-mesh.org/v1alpha1":
            errors.append(f"wrong apiVersion: {manifest.name}")
        if doc.get("kind") not in {"PodChaos", "NetworkChaos", "DNSChaos", "StressChaos"}:
            errors.append(f"unsupported kind: {manifest.name}")
        if doc.get("metadata", {}).get("namespace") != "switching":
            errors.append(f"wrong rendered namespace: {manifest.name}")
    except Exception as exc:
        errors.append(f"invalid rendered YAML {manifest.name}: {exc}")

common_text = (ROOT / "scripts/phase73/common.sh").read_text(encoding="utf-8")
execute_text = (ROOT / "scripts/phase73/execute_experiment.sh").read_text(encoding="utf-8")
if "TARGET_ENVIRONMENT" not in common_text or "PHASE73_EXECUTE_CHAOS" not in common_text:
    errors.append("missing environment safety guard in common.sh")
if "phase73_require_execution_approval" not in execute_text:
    errors.append("execute_experiment.sh does not enforce execution approval")
if "productionExecutionAllowed: false" not in policy_path.read_text(encoding="utf-8"):
    errors.append("production execution deny flag missing")

if errors:
    print(json.dumps({"status": "FAIL", "errors": errors}, indent=2))
    raise SystemExit(1)
print(json.dumps({"status": "PASS", "requiredFiles": len(required), "shellFiles": len(shells), "experimentManifests": 8}, indent=2))
