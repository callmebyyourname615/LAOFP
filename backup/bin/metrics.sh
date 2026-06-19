#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh

metric_file() {
  local job="$1"
  mkdir -p "${BACKUP_METRICS_DIR:-/var/lib/switching-backup/metrics}"
  printf '%s/%s.prom' "${BACKUP_METRICS_DIR:-/var/lib/switching-backup/metrics}" "$job"
}

publish_metrics() {
  local job="$1" payload_file="$2"
  local destination
  destination="$(metric_file "$job")"
  install -m 0640 "$payload_file" "${destination}.tmp"
  mv -f "${destination}.tmp" "$destination"

  if [[ -n "${PUSHGATEWAY_URL:-}" ]]; then
    local instance="${BACKUP_INSTANCE:-switching-db}"
    curl --fail --silent --show-error \
      --connect-timeout 3 --max-time 10 \
      --data-binary @"$payload_file" \
      "${PUSHGATEWAY_URL%/}/metrics/job/${job}/instance/${instance}" >/dev/null
  fi
}

publish_failure_metric() {
  local job="$1" metric_name="$2"
  local now file
  now="$(date +%s)"
  file="$(mktemp)"
  cat >"$file" <<METRICS
# TYPE ${metric_name} gauge
${metric_name}{database="${PGDATABASE:-switching_db}"} 1
# TYPE switching_backup_last_attempt_timestamp_seconds gauge
switching_backup_last_attempt_timestamp_seconds{job="${job}",database="${PGDATABASE:-switching_db}"} ${now}
METRICS
  publish_metrics "$job" "$file" || true
  rm -f "$file"
}
