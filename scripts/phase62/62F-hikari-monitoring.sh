#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"; cd "$PHASE62_ROOT"
phase_setup 62F "HikariCP monitoring and alerting"
phase_run "monitoring contract" python3 scripts/phase62/verify_hikari_monitoring.py
phase_finalize PASS 0 "Hikari metrics, dashboard, alerts and runbook are present"
