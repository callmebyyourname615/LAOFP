#!/usr/bin/env bash
set -Eeuo pipefail

scenario="${1:-smoke}"
case "$scenario" in
  sustained2k) scenario="sustained-2k-tps" ;;
  sustained10k) scenario="sustained-10k-tps" ;;
  burst10k) scenario="burst-10k-tps" ;;
  burst20k) scenario="burst-20k-tps" ;;
  soak8h) scenario="soak-8h" ;;
esac
case "$scenario" in
  smoke|sustained-2k-tps|sustained-10k-tps|burst-10k-tps|burst-20k-tps|vpa-500-concurrent|qr-200-concurrent|webhook-10k|soak-8h) ;;
  *) echo "Unknown scenario: $scenario" >&2; exit 2 ;;
esac

result_root="${RESULT_DIR:-performance/results}"
if [[ -n "${RUN_ID:-}" ]]; then
  [[ "$RUN_ID" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "RUN_ID contains unsafe characters" >&2; exit 2; }
  result_dir="$result_root/$RUN_ID"
else
  result_dir="$result_root"
fi
mkdir -p "$result_dir"

stamp="$(date -u +%Y%m%dT%H%M%SZ)"
summary="$result_dir/${scenario}-${stamp}.json"
metadata="$result_dir/${scenario}-${stamp}.metadata.env"
image="${K6_IMAGE:-grafana/k6:2.0.0}"
commit="$(git rev-parse HEAD 2>/dev/null || printf unknown)"
started="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

cat > "$metadata" <<META
scenario=$scenario
started_at_utc=$started
git_commit=$commit
k6_image=$image
base_url=${BASE_URL:-unset}
target_tps=${TARGET_TPS:-scenario-default}
duration=${DURATION:-scenario-default}
META

finish_metadata() {
  local exit_code=$?
  {
    echo "finished_at_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "exit_code=$exit_code"
  } >> "$metadata"
  return "$exit_code"
}
trap finish_metadata EXIT

# Docker must mount the result directory by absolute path because certification
# stores each release's evidence outside performance/results.
result_abs="$(cd "$result_dir" && pwd)"
docker run --rm --network "${K6_DOCKER_NETWORK:-host}" \
  -v "$PWD/performance:/performance:ro" -v "$result_abs:/results" \
  -e BASE_URL -e API_KEY -e PSP_ID -e SOURCE_BANK -e DESTINATION_BANK \
  -e TARGET_TPS -e START_TPS -e DURATION -e RAMP_DURATION \
  -e PREALLOCATED_VUS -e MAX_VUS -e VUS \
  -e VPA_TYPE -e VPA_VALUE -e QR_ID -e QR_AMOUNT -e WEBHOOK_ID -e ITERATIONS \
  "$image" run --summary-export "/results/$(basename "$summary")" "/performance/scenarios/${scenario}.js"
python3 performance/scripts/analyze-k6.py "$summary"
