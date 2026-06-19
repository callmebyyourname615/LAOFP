#!/usr/bin/env bash
set -Eeuo pipefail

image="${1:-}"
[[ -n "$image" ]] || { echo "Usage: $0 <backup-image>" >&2; exit 64; }
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }

test_id="phase8-$RANDOM-$$"
network="${test_id}-net"
pg="${test_id}-pg"
minio="${test_id}-minio"
work="$(mktemp -d)"
chmod 0777 "$work"
cleanup() {
  docker rm -f "$pg" "$minio" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

docker network create "$network" >/dev/null
docker run -d --name "$pg" --network "$network" \
  -e POSTGRES_PASSWORD=test-password \
  -e POSTGRES_DB=switching_db \
  postgres:16 \
  -c wal_level=replica -c max_wal_senders=5 -c max_replication_slots=5 >/dev/null

docker run -d --name "$minio" --network "$network" \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin123 \
  minio/minio:RELEASE.2025-04-22T22-12-26Z server /data >/dev/null

pg_ready=false
for _ in $(seq 1 60); do
  if docker exec "$pg" pg_isready -U postgres -d switching_db >/dev/null 2>&1; then
    pg_ready=true
    break
  fi
  sleep 1
done
if [[ "$pg_ready" != true ]]; then
  docker logs "$pg" >&2 || true
  echo "PostgreSQL did not become ready" >&2
  exit 1
fi

minio_ready=false
for _ in $(seq 1 60); do
  if docker run --rm --network "$network" minio/mc:RELEASE.2025-04-16T18-13-26Z \
      alias set local http://"$minio":9000 minioadmin minioadmin123 >/dev/null 2>&1; then
    minio_ready=true
    break
  fi
  sleep 1
done
if [[ "$minio_ready" != true ]]; then
  docker logs "$minio" >&2 || true
  echo "MinIO did not become ready" >&2
  exit 1
fi
docker run --rm --network "$network" --entrypoint /bin/sh minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec "mc alias set local http://${minio}:9000 minioadmin minioadmin123 >/dev/null; mc mb --ignore-existing local/switching-backups"

docker exec -i "$pg" psql -U postgres -d switching_db -v ON_ERROR_STOP=1 <<'SQL'
CREATE TABLE flyway_schema_history (installed_rank integer, success boolean);
INSERT INTO flyway_schema_history VALUES (1, true);
CREATE TABLE transactions (id bigint generated always as identity, created_at timestamp NOT NULL DEFAULT now());
INSERT INTO transactions DEFAULT VALUES;
CREATE TABLE outbox_messages (id bigint generated always as identity, created_at timestamp NOT NULL DEFAULT now());
SQL

docker run --rm -v "$work:/work" --entrypoint age-keygen "$image" -o /work/age-identity.txt >/dev/null
recipient="$(docker run --rm -v "$work:/work:ro" --entrypoint age-keygen "$image" -y /work/age-identity.txt)"
[[ "$recipient" == age1* ]]
mkdir -p "$work/backup-work" "$work/metrics" "$work/restore"
chmod -R 0777 "$work/backup-work" "$work/metrics" "$work/restore"

common_env=(
  -e PGHOST="$pg" -e PGPORT=5432 -e PGDATABASE=switching_db
  -e PGUSER=postgres -e PGPASSWORD=test-password -e PGSSLMODE=disable
  -e S3_ENDPOINT="http://${minio}:9000" -e S3_REGION=us-east-1
  -e S3_BUCKET=switching-backups -e S3_PREFIX=switching
  -e AWS_ACCESS_KEY_ID=minioadmin -e AWS_SECRET_ACCESS_KEY=minioadmin123
  -e S3_SERVER_SIDE_ENCRYPTION=none -e SECONDARY_S3_ENABLED=false
)

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_RECIPIENT="$recipient" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  -v "$work/metrics:/var/lib/switching-backup/metrics" \
  "$image" /opt/switching-backup/bin/full-backup.sh

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_IDENTITY_FILE=/var/run/secrets/backup/age-identity.txt \
  -e RESTORE_TARGET_DIR=/var/lib/switching-backup/restore-drill/data \
  -e RESTORE_DRILL_TIMEOUT_SECONDS=300 \
  -e RESTORE_DRILL_RTO_TARGET_SECONDS=300 \
  -v "$work/age-identity.txt:/var/run/secrets/backup/age-identity.txt:ro" \
  -v "$work/restore:/var/lib/switching-backup/restore-drill" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  -v "$work/metrics:/var/lib/switching-backup/metrics" \
  "$image" /opt/switching-backup/bin/restore-drill.sh

docker run --rm --network "$network" --entrypoint /bin/sh minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec "mc alias set local http://${minio}:9000 minioadmin minioadmin123 >/dev/null; mc stat local/switching-backups/switching/base/latest.json >/dev/null; mc find local/switching-backups/switching/drill-evidence --name '*.json' | grep -q ."

echo "Phase 8 PostgreSQL/MinIO backup and restore smoke test: PASS"
