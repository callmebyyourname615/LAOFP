#!/usr/bin/env python3
import argparse
import json
import os
import pathlib
import shlex

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

parser = argparse.ArgumentParser()
parser.add_argument("--policy", required=True)
args = parser.parse_args()
p = yaml.safe_load(pathlib.Path(args.policy).read_text(encoding="utf-8"))
values = {
    "PHASE73_NAMESPACE": p["namespace"],
    "PHASE73_APP_LABEL": p["application"]["appLabel"],
    "PHASE73_DEPLOYMENT_NAME": p["application"]["deploymentName"],
    "PHASE73_BASE_URL": p["application"]["baseUrl"],
    "PHASE73_HEALTH_URL": p["application"]["healthUrl"],
    "PHASE73_EXPERIMENT_DURATION": p["execution"]["experimentDurationSeconds"],
    "PHASE73_STABILIZATION_SECONDS": p["execution"]["stabilizationSeconds"],
    "PHASE73_CLEANUP_TIMEOUT_SECONDS": p["execution"]["cleanupTimeoutSeconds"],
    "PHASE73_COMMAND_TIMEOUT_SECONDS": p["execution"]["commandTimeoutSeconds"],
    "PHASE73_DATABASE_CIDRS_JSON": json.dumps(p["externalTargets"]["databaseCidrs"], separators=(",", ":")),
    "PHASE73_KAFKA_CIDRS_JSON": json.dumps(p["externalTargets"]["kafkaCidrs"], separators=(",", ":")),
    "PHASE73_OBJECT_STORAGE_CIDRS_JSON": json.dumps(p["externalTargets"]["objectStorageCidrs"], separators=(",", ":")),
    "PHASE73_EXTERNAL_API_CIDRS_JSON": json.dumps(p["externalTargets"]["externalApiCidrs"], separators=(",", ":")),
    "PHASE73_DNS_PATTERNS_JSON": json.dumps(p["dnsPatterns"], separators=(",", ":")),
    "PHASE73_CPU_WORKERS": p["resourceStress"]["cpuWorkers"],
    "PHASE73_CPU_LOAD_PERCENT": p["resourceStress"]["cpuLoadPercent"],
    "PHASE73_MEMORY_WORKERS": p["resourceStress"]["memoryWorkers"],
    "PHASE73_MEMORY_SIZE": p["resourceStress"]["memorySize"],
    "PHASE73_PRODUCTION_EXECUTION_ALLOWED": str(bool(p["productionExecutionAllowed"])).lower(),
    "PHASE73_APPROVAL_MAX_AGE_MINUTES": p["approval"]["maximumAgeMinutes"],
    "PHASE73_REQUIRED_SCENARIOS_JSON": json.dumps([item["id"] for item in p["scenarios"] if item.get("required", False)], separators=(",", ":")),
}
for key, value in values.items():
    effective = os.environ.get(key, str(value))
    print(f"{key}={shlex.quote(str(effective))}")
