#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/lib.sh"
require_dr_confirmation
: "${REGION_FAILOVER_COMMAND:?REGION_FAILOVER_COMMAND is required}"
: "${REGION_FAILBACK_COMMAND:?REGION_FAILBACK_COMMAND is required}"
record REGION_FAILOVER START
trap 'record REGION_FAILBACK START; bash -lc "$REGION_FAILBACK_COMMAND" | tee -a "$EVIDENCE_DIR/region-failback.log"; record REGION_FAILBACK COMPLETE' EXIT
bash -lc "$REGION_FAILOVER_COMMAND" | tee "$EVIDENCE_DIR/region-failover.log"
wait_deployment
record REGION_FAILOVER COMPLETE
