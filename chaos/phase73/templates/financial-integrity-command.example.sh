#!/usr/bin/env bash
set -Eeuo pipefail
output=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --output) output="${2:?missing --output value}"; shift 2 ;;
    *) echo "unknown argument: $1" >&2; exit 64 ;;
  esac
done
[[ -n "$output" ]] || { echo "--output is required" >&2; exit 64; }
cat >&2 <<'EOF'
Implement this adapter with production-equivalent read-only queries before use.
It must write JSON containing balanceMismatchCount and outboxBacklogGrowth.
Returning assumed zero values without querying the UAT system is prohibited.
EOF
exit 78
