#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
require_dr_confirmation
"$(dirname "$0")/capture-baseline.sh"
if [[ $# -gt 0 ]]; then
  scenarios=("$@")
else
  read -r -a scenarios <<<"${DR_SCENARIOS:-kill-application-pod network-partition}"
fi
for scenario in "${scenarios[@]}"; do
  case "$scenario" in
    pod-kill) scenario="kill-application-pod" ;;
    kafka-fail) scenario="kafka-broker-failure" ;;
    s3-down) scenario="object-storage-failure" ;;
    net-partition) scenario="network-partition" ;;
    ext-timeout) scenario="external-timeout" ;;
    region-failover) scenario="region-failover" ;;
  esac
  case "$scenario" in
    kill-application-pod|kafka-broker-failure|object-storage-failure|network-partition|deployment-rollback|external-timeout|region-failover) ;;
    *) echo "Unsupported DR scenario: $scenario" >&2; exit 2 ;;
  esac
  "$(dirname "$0")/${scenario}.sh"
done
"$(dirname "$0")/verify-recovery.sh"
python3 "$(dirname "$0")/build-evidence.py" "$EVIDENCE_DIR"
