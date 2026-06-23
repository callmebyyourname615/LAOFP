#!/usr/bin/env bash
# Action #5 — Inventory runtime evidence. Reports what's collected vs missing.
set -euo pipefail
cd "$(dirname "$0")/../.."

EV="${EVIDENCE_DIR:-scripts/execute-and-verify/evidence/manual}"
mkdir -p "$EV"

echo "Runtime evidence inventory:"
echo

check() {
  local label="$1" path="$2"
  if compgen -G "$path" >/dev/null 2>&1; then
    echo "  ✅ $label : $(ls -d $path 2>/dev/null | head -1)"
  else
    echo "  ❌ $label : MISSING ($path)"
  fi
}

check "B1 Test surefire reports"        "target/surefire-reports/*.xml"
check "B2 Performance k6 results"       "performance/results/**/*summary*.json"
check "B3 Backup drill evidence"        "backup/evidence/*"
check "B4 DR drill evidence"            "dr/evidence/*"
check "B5 Sanctions sync evidence"      "compliance/evidence/sanctions*"
check "B6 Vault rotation drill log"     "security/evidence/vault-rotation*"
check "B7 Alert firing test"            "monitoring/evidence/alert-firing*"
check "B8 Static verifier logs"         "scripts/execute-and-verify/evidence/*"

echo
echo "Next steps to collect missing evidence:"
echo "  B1: ./mvnw verify"
echo "  B2: performance/scripts/run-k6.sh smoke && ... sustained2k && ... sustained10k && ... burst20k && ... soak8h"
echo "  B3: backup/bin/full-backup.sh && backup/bin/restore-drill.sh"
echo "  B4: dr/scripts/verify-recovery.sh (run each scenario in dr/scripts/)"
echo "  B5: compliance/sanctions/run-sync-drill.sh (see runbook)"
echo "  B6: security/scripts/vault-transit-key-rotation-drill.sh"
echo "  B7: monitoring/scripts/fire-test-alerts.sh"
echo
echo "Each step must be run on the corresponding env (UAT for perf, prod-like for DR)."
echo "Evidence bundle goes to: $EV"
echo "  Phase 60 full runner: TARGET_ENVIRONMENT=uat PHASE60_EXECUTE_RUNTIME=true ./scripts/phase60/run_phase60.sh --full"
