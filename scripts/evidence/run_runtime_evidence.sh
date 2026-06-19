#!/usr/bin/env bash
set -Eeuo pipefail

MODE="${1:-preflight}"
case "$MODE" in preflight|performance|resilience|soak|full) ;; *) echo "Usage: $0 {preflight|performance|resilience|soak|full}" >&2; exit 64;; esac

: "${EVIDENCE_ENVIRONMENT:?EVIDENCE_ENVIRONMENT must be uat, performance, or dr}"
case "$EVIDENCE_ENVIRONMENT" in uat|performance|dr) ;; *) echo "Production execution is intentionally prohibited" >&2; exit 64;; esac
: "${RELEASE_GIT_COMMIT:?full 40-character release commit is required}"
: "${RELEASE_IMAGE_DIGEST:?sha256 image digest is required}"
: "${RELEASE_REFERENCE:?release/change reference is required}"
[[ "$RELEASE_GIT_COMMIT" =~ ^[a-f0-9]{40}$ ]] || { echo "Invalid RELEASE_GIT_COMMIT" >&2; exit 64; }
[[ "$RELEASE_IMAGE_DIGEST" =~ ^sha256:[a-f0-9]{64}$ ]] || { echo "Invalid RELEASE_IMAGE_DIGEST" >&2; exit 64; }

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="${EVIDENCE_DIR:-build/runtime-evidence/${RELEASE_REFERENCE//[^A-Za-z0-9._-]/_}-${STAMP}}"
mkdir -p "$EVIDENCE_DIR/logs" "$EVIDENCE_DIR/artifacts"
: > "$EVIDENCE_DIR/step-results.jsonl"

record() {
  local id="$1" status="$2" exit_code="$3" started="$4" ended="$5" log_path="$6"
  python3 - "$EVIDENCE_DIR/step-results.jsonl" "$id" "$status" "$exit_code" "$started" "$ended" "$log_path" <<'PY'
import json, pathlib, sys
path, control_id, status, exit_code, started, ended, log_path = sys.argv[1:]
row = {"id": control_id, "status": status, "exitCode": int(exit_code),
       "startedAt": started, "endedAt": ended, "logPath": log_path}
with pathlib.Path(path).open("a", encoding="utf-8") as stream:
    stream.write(json.dumps(row, sort_keys=True) + "\n")
PY
}

run_step() {
  local id="$1"; shift
  local log="$EVIDENCE_DIR/logs/$id.log"
  local started ended rc status
  started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "[$started] START $id" | tee "$log"
  set +e
  "$@" >>"$log" 2>&1
  rc=$?
  set -e
  ended="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  status=PASS; [[ $rc -eq 0 ]] || status=FAIL
  echo "[$ended] $status $id exit=$rc" | tee -a "$log"
  record "$id" "$status" "$rc" "$started" "$ended" "logs/$id.log"
  [[ $rc -eq 0 ]]
}

preflight() {
  run_step static-gates python3 scripts/verify_all_static.py || true
  run_step full-maven-verify ./mvnw --batch-mode --no-transfer-progress clean verify || true
  if [[ -d target/surefire-reports ]]; then
    tar -czf "$EVIDENCE_DIR/artifacts/surefire-reports.tar.gz" -C target surefire-reports
  fi
  run_step migration-v83-runtime ./mvnw --batch-mode --no-transfer-progress \
    -Dtest=MigrationApplicationIntegrationTest,V83PayloadSha256SchemaAlignmentIntegrationTest test || true
  run_step sanctions-mock-sync ./mvnw --batch-mode --no-transfer-progress -Dtest='Sanctions*' test || true
}

traffic() {
  [[ "${EVIDENCE_ALLOW_TRAFFIC:-}" == I_UNDERSTAND_THIS_GENERATES_LOAD ]] || {
    echo "Set EVIDENCE_ALLOW_TRAFFIC=I_UNDERSTAND_THIS_GENERATES_LOAD" >&2; return 64; }
  export RESULT_DIR="$EVIDENCE_DIR/artifacts/performance"
  mkdir -p "$RESULT_DIR"
  run_step performance-smoke performance/scripts/run-k6.sh smoke || true
  run_step performance-sustained-2k performance/scripts/run-k6.sh sustained-2k-tps || true
  run_step performance-burst-10k performance/scripts/run-k6.sh burst-10k-tps || true
  run_step vpa-500-concurrent performance/scripts/run-k6.sh vpa-500-concurrent || true
  run_step qr-200-concurrent performance/scripts/run-k6.sh qr-200-concurrent || true
  run_step webhook-10k performance/scripts/run-k6.sh webhook-10k || true
  run_step settlement-500k performance/settlement/run_settlement_benchmark.sh || true
  run_step capacity-snapshot performance/scripts/capture-capacity-evidence.sh || true
}

resilience() {
  [[ "${EVIDENCE_ALLOW_DISRUPTIVE:-}" == I_UNDERSTAND_THIS_IS_DESTRUCTIVE ]] || {
    echo "Set EVIDENCE_ALLOW_DISRUPTIVE=I_UNDERSTAND_THIS_IS_DESTRUCTIVE" >&2; return 64; }
  [[ "$EVIDENCE_ENVIRONMENT" != production ]]
  export DR_ENVIRONMENT="$EVIDENCE_ENVIRONMENT"
  export DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
  run_step backup-restore-drill backup/bin/restore-drill.sh || true
  run_step dr-suite dr/scripts/run-dr-suite.sh || true
  run_step vault-key-rotation security/scripts/vault-transit-key-rotation-drill.sh || true
  export ALERT_DELIVERY_OUTPUT="$EVIDENCE_DIR/artifacts/alert-delivery-results.json"
  run_step alert-delivery-drill scripts/monitoring/run_alert_delivery_drill.sh || true
}

soak() {
  [[ "${EVIDENCE_ALLOW_TRAFFIC:-}" == I_UNDERSTAND_THIS_GENERATES_LOAD ]] || {
    echo "Set EVIDENCE_ALLOW_TRAFFIC=I_UNDERSTAND_THIS_GENERATES_LOAD" >&2; return 64; }
  export RESULT_DIR="$EVIDENCE_DIR/artifacts/performance"
  mkdir -p "$RESULT_DIR"
  run_step soak-8h performance/scripts/run-k6.sh soak-8h || true
}

case "$MODE" in
  preflight) preflight ;;
  performance) traffic ;;
  resilience) resilience ;;
  soak) soak ;;
  full) preflight; traffic; resilience; soak ;;
esac

set +e
python3 scripts/evidence/build_runtime_evidence.py \
  --evidence-dir "$EVIDENCE_DIR" \
  --environment "$EVIDENCE_ENVIRONMENT" \
  --git-commit "$RELEASE_GIT_COMMIT" \
  --image-digest "$RELEASE_IMAGE_DIGEST" \
  --release-reference "$RELEASE_REFERENCE"
build_rc=$?
set -e
python3 scripts/evidence/verify_runtime_evidence.py "$EVIDENCE_DIR/manifest.json"
execution_failures="$(python3 - "$EVIDENCE_DIR/step-results.jsonl" <<'PY2'
import json, pathlib, sys
rows = [json.loads(line) for line in pathlib.Path(sys.argv[1]).read_text(encoding="utf-8").splitlines() if line.strip()]
print(sum(row.get("status") == "FAIL" for row in rows))
PY2
)"
echo "Runtime evidence: $EVIDENCE_DIR"
if [[ "$execution_failures" -gt 0 ]]; then
  exit 1
fi
if [[ "$MODE" == full ]]; then
  exit "$build_rc"
fi
# Partial evidence groups are valid artifacts but cannot independently claim Go-Live readiness.
[[ "$build_rc" -eq 0 || "$build_rc" -eq 3 ]] || exit "$build_rc"
exit 0
