#!/usr/bin/env bash
# Action #3 — Static verifiers + Maven full test suite.
set -euo pipefail
cd "$(dirname "$0")/../.."

echo "[1/3] Running scripts/verify_all_static.py…"
if [ -f scripts/verify_all_static.py ]; then
  python3 scripts/verify_all_static.py
else
  echo "  SKIP: scripts/verify_all_static.py not present"
fi

echo "[2/3] Running phase53/54/55 verifiers…"
for v in scripts/verify_phases_53c_53j.py scripts/verify_phases_54a_54j.py scripts/verify_phase55_static.py; do
  if [ -f "$v" ]; then
    echo "  -> $v"
    python3 "$v"
  fi
done

echo "[3/3] Running Maven test suite (this is the long one)…"
if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
  echo "  ⚠️  Docker not running — skipping integration tests, running unit only"
  ./mvnw -q test -DskipITs=true
else
  ./mvnw -q verify
fi
