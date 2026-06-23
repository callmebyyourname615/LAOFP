#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62D "Read replica routing and consistency policy"
phase_run "routing static contract" python3 scripts/phase62/verify_read_replica_routing.py
if phase_is_preflight; then phase_finalize PREPARED 0 "transaction-aware routing contract is ready"; exit 0; fi
phase_run "routing tests" ./mvnw -B -Dtest=TransactionRoutingDataSourceTest test
if [[ "${PHASE62_MODE:-repo}" == "full" ]]; then
  phase_require_uat
  phase_run "UAT primary/replica role certification" scripts/phase62/certify_read_replica_uat.sh
fi
phase_finalize PASS 0 "routing tests passed; UAT role certification executed in full mode"
