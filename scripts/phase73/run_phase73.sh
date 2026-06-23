#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname "$0")/../.."
mode=preflight
case "${1:-}" in
  ""|--preflight) mode=preflight ;;
  --full) mode=full ;;
  --help|-h)
    cat <<'USAGE'
Usage: scripts/phase73/run_phase73.sh [--preflight|--full]

--preflight  Validate policy, schemas, manifests and safety controls. No cluster changes.
--full       Execute approved Chaos Mesh experiments against UAT only.
USAGE
    exit 0 ;;
  *) echo "Unknown mode: $1" >&2; exit 64 ;;
esac
export PHASE73_RUN_ID="${PHASE73_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
export PHASE73_EVIDENCE_ROOT="${PHASE73_EVIDENCE_ROOT:-$PWD/build/phase73-evidence}"
policy_exports="$(python3 scripts/phase73/export_policy_env.py --policy "${PHASE73_POLICY:-$PWD/config/phase73-chaos-policy.yaml}")"
eval "$policy_exports"
export PHASE73_NAMESPACE PHASE73_APP_LABEL PHASE73_DEPLOYMENT_NAME PHASE73_BASE_URL PHASE73_HEALTH_URL
export PHASE73_EXPERIMENT_DURATION PHASE73_STABILIZATION_SECONDS PHASE73_CLEANUP_TIMEOUT_SECONDS PHASE73_COMMAND_TIMEOUT_SECONDS
export PHASE73_DATABASE_CIDRS_JSON PHASE73_KAFKA_CIDRS_JSON PHASE73_OBJECT_STORAGE_CIDRS_JSON PHASE73_EXTERNAL_API_CIDRS_JSON
export PHASE73_DNS_PATTERNS_JSON PHASE73_CPU_WORKERS PHASE73_CPU_LOAD_PERCENT PHASE73_MEMORY_WORKERS PHASE73_MEMORY_SIZE
export PHASE73_PRODUCTION_EXECUTION_ALLOWED PHASE73_APPROVAL_MAX_AGE_MINUTES PHASE73_REQUIRED_SCENARIOS_JSON PHASE73_POLICY_ENV_READY=true
if [[ "$mode" == preflight ]]; then
  export PHASE73_PREFLIGHT_ONLY=true
else
  export PHASE73_PREFLIGHT_ONLY=false
fi
phases=(
  73A-chaos-platform-readiness.sh
  73B-pod-kill.sh
  73C-database-network-loss.sh
  73D-kafka-network-delay.sh
  73E-object-storage-network-loss.sh
  73F-external-api-delay.sh
  73G-dns-error.sh
  73H-resource-exhaustion-certification.sh
  73I-financial-integrity-verification.sh
  73J-resilience-bundle.sh
)
for script in "${phases[@]}"; do
  echo; echo "=== ${script%%-*} ($mode) ==="
  "scripts/phase73/$script"
done
printf '\nPhase 73 run complete: %s\nEvidence: %s/%s\n' "$mode" "$PHASE73_EVIDENCE_ROOT" "$PHASE73_RUN_ID"
