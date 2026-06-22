#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; day2_require_identity; day2_phase_begin 56C "High Availability and Automated Failover"; day2_require_environment production dr
: "${HA_TOPOLOGY_SNAPSHOT:?HA_TOPOLOGY_SNAPSHOT is required}"; : "${HA_FAILOVER_REPORT:?HA_FAILOVER_REPORT is required}"; : "${HA_FAILBACK_REPORT:?HA_FAILBACK_REPORT is required}"
cp "$HA_TOPOLOGY_SNAPSHOT" "$PHASE_DIR/ha-topology.json"
day2_run_check topology python3 scripts/day2/verify_ha_topology.py --snapshot "$PHASE_DIR/ha-topology.json" --policy ha/ha-policy.yaml --output "$PHASE_DIR/ha-topology-verification.json" || true
day2_run_check failover python3 scripts/day2/verify_failover_report.py --report "$HA_FAILOVER_REPORT" --policy ha/ha-policy.yaml --mode failover --output "$PHASE_DIR/failover-report.json" || true
day2_run_check failback python3 scripts/day2/verify_failover_report.py --report "$HA_FAILBACK_REPORT" --policy ha/ha-policy.yaml --mode failback --output "$PHASE_DIR/failback-report.json" || true
day2_write_result
