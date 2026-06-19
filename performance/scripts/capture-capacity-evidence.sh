#!/usr/bin/env bash
set -Eeuo pipefail
namespace="${PERFORMANCE_NAMESPACE:-switching}"
result_dir="${RESULT_DIR:-performance/results/capacity-$(date -u +%Y%m%dT%H%M%SZ)}"
mkdir -p "$result_dir"
command -v kubectl >/dev/null || { echo 'kubectl is required' >&2; exit 2; }

kubectl -n "$namespace" get deployment switching-api -o yaml > "$result_dir/deployment.yaml"
kubectl -n "$namespace" get pods -l app=switching-api -o wide > "$result_dir/pods.txt"
kubectl -n "$namespace" get hpa -o yaml > "$result_dir/hpa.yaml" 2>/dev/null || true
kubectl -n "$namespace" top pod -l app=switching-api --containers > "$result_dir/pod-resource-usage.txt" 2>/dev/null || true
kubectl -n "$namespace" get events --sort-by=.lastTimestamp > "$result_dir/events.txt"

if [[ -n "${PROMETHEUS_URL:-}" ]]; then
  base="${PROMETHEUS_URL%/}"
  auth=()
  [[ -n "${PROMETHEUS_BEARER_TOKEN:-}" ]] && auth=(-H "Authorization: Bearer $PROMETHEUS_BEARER_TOKEN")
  queries=(
    'sum(rate(http_server_requests_seconds_count{application="switching-api"}[5m]))'
    'histogram_quantile(0.95,sum(rate(http_server_requests_seconds_bucket{application="switching-api"}[5m])) by (le))'
    'histogram_quantile(0.99,sum(rate(http_server_requests_seconds_bucket{application="switching-api"}[5m])) by (le))'
    'max(hikaricp_connections_active{application="switching-api"})'
    'max(jvm_gc_pause_seconds_max{application="switching-api"})'
    'sum(jvm_memory_used_bytes{application="switching-api",area="heap"})'
    'sum(jvm_memory_max_bytes{application="switching-api",area="heap"})'
    'sum(switching_outbox_backlog)'
  )
  : > "$result_dir/prometheus-snapshots.jsonl"
  index=0
  for query in "${queries[@]}"; do
    response="$result_dir/.prometheus-response-$index.json"
    curl --fail-with-body -sS --get "${auth[@]}" --data-urlencode "query=$query" \
      "$base/api/v1/query" > "$response"
    python3 - "$query" "$response" >> "$result_dir/prometheus-snapshots.jsonl" <<'PY'
import json, pathlib, sys
query, response_path = sys.argv[1:]
response = json.loads(pathlib.Path(response_path).read_text(encoding="utf-8"))
print(json.dumps({"query": query, "response": response}, sort_keys=True))
PY
    rm -f "$response"
    index=$((index + 1))
  done
fi

python3 - "$result_dir" > "$result_dir/metadata.json" <<'PY'
import datetime, json, os, pathlib, sys
root = pathlib.Path(sys.argv[1])
print(json.dumps({
    "capturedAt": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
    "namespace": os.environ.get("PERFORMANCE_NAMESPACE", "switching"),
    "releaseDigest": os.environ.get("RELEASE_IMAGE_DIGEST", os.environ.get("RELEASE_DIGEST", "UNKNOWN")),
    "files": sorted(p.name for p in root.iterdir() if p.is_file()),
}, indent=2, sort_keys=True))
PY
printf '%s\n' "$result_dir"
