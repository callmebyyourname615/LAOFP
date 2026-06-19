#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
require_dr_confirmation
"$(dirname "$0")/capture-baseline.sh"
read -r -a scenarios <<<"${DR_SCENARIOS:-kill-application-pod network-partition}"
for scenario in "${scenarios[@]}"; do
  case "$scenario" in
    kill-application-pod|kafka-broker-failure|object-storage-failure|network-partition|deployment-rollback|external-timeout) ;;
    *) echo "Unsupported DR scenario: $scenario" >&2; exit 2 ;;
  esac
  "$(dirname "$0")/${scenario}.sh"
done
"$(dirname "$0")/verify-recovery.sh"
python3 "$(dirname "$0")/build-evidence.py" "$EVIDENCE_DIR"
