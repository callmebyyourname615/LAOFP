#!/usr/bin/env python3
import argparse
import json
import pathlib

try:
    import yaml
except ImportError as exc:
    raise SystemExit("PyYAML is required") from exc

parser = argparse.ArgumentParser()
parser.add_argument("--policy", required=True)
parser.add_argument("--evidence-root", required=True)
parser.add_argument("--output", required=True)
args = parser.parse_args()
policy = yaml.safe_load(pathlib.Path(args.policy).read_text(encoding="utf-8"))
root = pathlib.Path(args.evidence_root)
thresholds = policy["thresholds"]
required = [s["id"] for s in policy["scenarios"] if s.get("required", False)]
results = []
errors = []
for scenario in required:
    candidates = sorted(root.rglob(f"{scenario}/attestation.json"))
    if not candidates:
        errors.append(f"missing attestation: {scenario}")
        continue
    doc = json.loads(candidates[-1].read_text(encoding="utf-8"))
    integrity = doc.get("integrity") or {}
    checks = {
        "status": doc.get("status") == "PASS",
        "cleanup": doc.get("cleanupStatus") == "PASS",
        "recovery": int(doc.get("recoveryTimeSeconds", 10**9)) <= int(thresholds["maximumRecoveryTimeSeconds"]),
        "dataLoss": int(integrity.get("dataLossCount", 10**9)) <= int(thresholds["maximumDataLossCount"]),
        "duplicates": int(integrity.get("duplicateReplayCount", 10**9)) <= int(thresholds["maximumDuplicateReplayCount"]),
        "balances": int(integrity.get("balanceMismatchCount", 10**9)) <= int(thresholds["maximumBalanceMismatchCount"]),
        "outbox": int(integrity.get("outboxBacklogGrowth", 10**9)) <= int(thresholds["maximumOutboxBacklogGrowth"]),
    }
    passed = all(checks.values())
    if not passed:
        errors.append(f"scenario failed thresholds: {scenario}: {checks}")
    results.append({"scenario": scenario, "passed": passed, "checks": checks, "attestation": str(candidates[-1])})
passed_count = sum(1 for item in results if item["passed"])
pass_percent = (passed_count * 100.0 / len(required)) if required else 0.0
if pass_percent < float(thresholds["minimumScenarioPassPercent"]):
    errors.append(f"scenario pass percent {pass_percent} below required {thresholds['minimumScenarioPassPercent']}")
summary = {"required": len(required), "passed": passed_count, "failed": len(required) - passed_count, "passPercent": pass_percent, "results": results, "errors": errors, "status": "PASS" if not errors else "FAIL"}
path = pathlib.Path(args.output)
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")
print(json.dumps(summary, sort_keys=True))
if errors:
    raise SystemExit(1)
