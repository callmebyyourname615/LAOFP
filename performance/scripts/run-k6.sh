#!/usr/bin/env bash
set -Eeuo pipefail
scenario="${1:-smoke}"
case "$scenario" in smoke|sustained-2k-tps|burst-10k-tps|vpa-500-concurrent|qr-200-concurrent|webhook-10k|soak-8h) ;; *) echo "Unknown scenario: $scenario" >&2; exit 2;; esac
result_dir="${RESULT_DIR:-performance/results}"
mkdir -p "$result_dir"
stamp=$(date -u +%Y%m%dT%H%M%SZ)
summary="$result_dir/${scenario}-${stamp}.json"
image="${K6_IMAGE:-grafana/k6:2.0.0}"
# Docker must mount the result directory by absolute path because certification
# stores each release's evidence outside performance/results.
result_abs="$(cd "$result_dir" && pwd)"
docker run --rm --network "${K6_DOCKER_NETWORK:-host}" \
  -v "$PWD/performance:/performance:ro" -v "$result_abs:/results" \
  -e BASE_URL -e API_KEY -e PSP_ID -e SOURCE_BANK -e DESTINATION_BANK \
  -e TARGET_TPS -e DURATION -e PREALLOCATED_VUS -e MAX_VUS -e VUS \
  -e VPA_TYPE -e VPA_VALUE -e QR_ID -e QR_AMOUNT -e WEBHOOK_ID -e ITERATIONS \
  "$image" run --summary-export "/results/$(basename "$summary")" "/performance/scenarios/${scenario}.js"
python3 performance/scripts/analyze-k6.py "$summary"
