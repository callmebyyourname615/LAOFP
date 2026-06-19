#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54C 54F 54H
[[ "$CERTIFICATION_ENVIRONMENT" == uat || "$CERTIFICATION_ENVIRONMENT" == dr ]] || cert_die "54G requires UAT or DR"
[[ "${DR_CERTIFICATION_CONFIRMATION:-}" == I_UNDERSTAND_THIS_IS_DESTRUCTIVE ]] || cert_die "invalid DR_CERTIFICATION_CONFIRMATION"
phase_begin 54G "DR & Failure Recovery Certification"
failed=0
export DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export DR_ENVIRONMENT="$CERTIFICATION_ENVIRONMENT"
export DR_SCENARIOS="kill-application-pod kafka-broker-failure network-partition object-storage-failure external-timeout deployment-rollback"
export EVIDENCE_DIR="$PHASE_DIR/dr-evidence"
run_check dr-suite dr/scripts/run-dr-suite.sh || failed=1
run_check dr-summary python3 - "$PHASE_DIR/dr-summary.json" "$EVIDENCE_DIR" config/phase54-thresholds.yaml <<'PY' || failed=1
import datetime
import json
import pathlib
import sys
import yaml

out, root_text, config_path = sys.argv[1:]
root = pathlib.Path(root_text)
config = yaml.safe_load(pathlib.Path(config_path).read_text(encoding="utf-8"))["dr"]
required = config["requiredScenarios"]
max_recovery = int(config["maximumRecoverySeconds"])
reasons = []

if not root.is_dir():
    raise SystemExit("DR evidence directory is missing")

def load_json(name):
    path = root / name
    if not path.is_file():
        reasons.append(f"missing DR evidence: {name}")
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        reasons.append(f"invalid DR evidence {name}: {exc}")
        return {}

reconciliation = load_json("reconciliation.json")
replay = load_json("replay-integrity.json")
health = load_json("health.json")
package = load_json("evidence.json")

rows = []
timeline = root / "timeline.tsv"
if timeline.is_file():
    for line in timeline.read_text(encoding="utf-8", errors="replace").splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        try:
            timestamp = datetime.datetime.fromisoformat(parts[0].replace("Z", "+00:00"))
        except ValueError:
            continue
        rows.append((timestamp, parts[1], parts[2]))
else:
    reasons.append("missing DR timeline")

markers = {
    "kill-application-pod": (("APP_POD_KILL", None), ("APP_POD_RECOVERED", None)),
    "kafka-broker-failure": (("KAFKA_FAILURE", None), ("KAFKA_RECOVERED", None)),
    "network-partition": (("NETWORK_PARTITION", "APPLY"), ("NETWORK_PARTITION", "RESTORED")),
    "object-storage-failure": (("OBJECT_STORAGE_FAILURE", None), ("OBJECT_STORAGE_RECOVERED", None)),
    "external-timeout": (("EXTERNAL_TIMEOUT", "ENABLE"), ("EXTERNAL_TIMEOUT", "DISABLE")),
    "deployment-rollback": (("ROLLBACK_START", None), ("ROLLBACK_COMPLETE", None)),
}

def find(marker, after=None):
    event, detail = marker
    for timestamp, row_event, row_detail in rows:
        if after is not None and timestamp < after:
            continue
        if row_event == event and (detail is None or row_detail == detail):
            return timestamp
    return None

scenario_results = {}
for scenario in required:
    start_marker, end_marker = markers[scenario]
    start = find(start_marker)
    end = find(end_marker, start) if start else None
    duration = int((end - start).total_seconds()) if start and end else None
    passed = duration is not None and duration >= 0 and duration <= max_recovery
    if not passed:
        reasons.append(f"scenario evidence or recovery threshold failed: {scenario}")
    scenario_results[scenario] = {
        "startedAt": start.isoformat().replace("+00:00", "Z") if start else None,
        "recoveredAt": end.isoformat().replace("+00:00", "Z") if end else None,
        "recoverySeconds": duration,
        "maximumRecoverySeconds": max_recovery,
        "passed": passed,
    }

violations = reconciliation.get("violations", []) if isinstance(reconciliation, dict) else []
committed_loss = sum(max(0, int(item.get("before", 0)) - int(item.get("after", 0))) for item in violations)
duplicate_replay = int(replay.get("newDuplicateReplayCount", -1)) if isinstance(replay, dict) else -1
if reconciliation.get("passed") is not True or committed_loss != 0:
    reasons.append("committed row reconciliation failed")
if replay.get("passed") is not True or duplicate_replay != 0:
    reasons.append("duplicate replay integrity failed")
if health.get("status") != "UP":
    reasons.append("application health is not UP after recovery")
if package.get("status") != "AWAITING_HUMAN_SIGN_OFF":
    reasons.append("DR evidence package status is invalid")

files = sorted(str(path.relative_to(root)) for path in root.rglob("*") if path.is_file())
document = {
    "schemaVersion": 1,
    "requiredScenarios": required,
    "scenarios": scenario_results,
    "evidenceFiles": files,
    "committedTransactionLoss": committed_loss,
    "duplicateReplayCount": duplicate_replay,
    "postRecoveryHealth": health.get("status"),
    "machineEvidenceStatus": package.get("status"),
    "passed": not reasons,
    "failureReasons": reasons,
}
pathlib.Path(out).write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
if not document["passed"]:
    raise SystemExit(1)
PY
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
