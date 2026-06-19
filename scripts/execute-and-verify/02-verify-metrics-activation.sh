#!/usr/bin/env bash
# Action #2 — Verify OperationalMetricsCollector activates in non-migration profile.
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "[1/2] Checking @Profile on OperationalMetricsCollector…"
grep -E '@Profile\("!migration"\)' \
  src/main/java/com/example/switching/observability/OperationalMetricsCollector.java \
  || { echo "FAIL: collector not @Profile(\"!migration\")"; exit 1; }
echo "  OK"

echo "[2/2] Checking metrics config bean is enabled by default…"
grep -E '@Profile\("!migration"\)' \
  src/main/java/com/example/switching/observability/OperationalMetricsConfiguration.java \
  || { echo "FAIL: config not @Profile(\"!migration\")"; exit 1; }
echo "  OK"
