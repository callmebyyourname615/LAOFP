#!/usr/bin/env bash
set -Eeuo pipefail
# shellcheck source=common.sh
source /opt/switching-backup/bin/common.sh

require_command pg_receivewal
require_command psql
require_env PGHOST PGUSER PGPASSWORD S3_BUCKET BACKUP_AGE_RECIPIENT
slot="${WAL_REPLICATION_SLOT:-switching_backup_slot}"
[[ "$slot" =~ ^[a-z0-9_]{1,63}$ ]] || die "invalid WAL_REPLICATION_SLOT"
spool="${WAL_SPOOL_DIR:-/var/lib/switching-backup/incoming}"
mkdir -p "$spool"
connection="$(postgres_connection_uri postgres)"

if [[ "$(psql "$connection" -AtX -v ON_ERROR_STOP=1 -c "SELECT count(*) FROM pg_replication_slots WHERE slot_name='${slot}'")" == "0" ]]; then
  log INFO "creating physical replication slot slot=${slot}"
  psql "$connection" -v ON_ERROR_STOP=1 -c "SELECT pg_create_physical_replication_slot('${slot}', true);"
fi

wal-uploader.sh &
uploader_pid=$!
trap 'kill "$uploader_pid" 2>/dev/null || true; wait "$uploader_pid" 2>/dev/null || true' EXIT TERM INT

backoff=1
while true; do
  log INFO "starting pg_receivewal slot=${slot}"
  if pg_receivewal \
      --dbname="$connection" \
      --directory="$spool" \
      --slot="$slot" \
      --synchronous \
      --verbose \
      --no-loop; then
    backoff=1
  else
    log ERROR "pg_receivewal exited unexpectedly retry_seconds=${backoff}"
    sleep "$backoff"
    (( backoff < 60 )) && backoff=$((backoff * 2))
  fi
done
