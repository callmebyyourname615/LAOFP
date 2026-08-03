#!/usr/bin/env bash
set -Eeuo pipefail

image="${1:-}"
[[ -n "$image" ]] || { echo "Usage: $0 <backup-image>" >&2; exit 64; }
command -v docker >/dev/null 2>&1 || { echo "docker is required" >&2; exit 1; }

test_id="phase8-$RANDOM-$$"
network="${test_id}-net"
pg="${test_id}-pg"
minio="${test_id}-minio"
secondary_minio="${test_id}-minio-secondary"
work="$(mktemp -d)"
chmod 0777 "$work"
cleanup() {
  docker rm -f "$pg" "$minio" "$secondary_minio" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT

docker network create "$network" >/dev/null
docker run -d --name "$pg" --network "$network" \
  -e POSTGRES_PASSWORD=test-password \
  -e POSTGRES_HOST_AUTH_METHOD=trust \
  -e POSTGRES_DB=switching_db \
  postgres:16 \
  -c wal_level=replica -c max_wal_senders=5 -c max_replication_slots=5 >/dev/null

docker run -d --name "$minio" --network "$network" \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin123 \
  minio/minio:RELEASE.2025-04-22T22-12-26Z server /data >/dev/null

docker run -d --name "$secondary_minio" --network "$network" \
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

# The image's default replication HBA entry is password-based. The temporary
# test network has no externally reachable ports, so allow its backup worker.
docker exec "$pg" sh -ec '
  sed -i -E "/^host[[:space:]]+replication[[:space:]]/c\\host replication all all trust" "$PGDATA/pg_hba.conf"
  psql -U postgres -d switching_db -v ON_ERROR_STOP=1 -c "SELECT pg_reload_conf()" >/dev/null
'

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
secondary_minio_ready=false
for _ in $(seq 1 60); do
  if docker run --rm --network "$network" minio/mc:RELEASE.2025-04-16T18-13-26Z \
      alias set secondary http://"$secondary_minio":9000 minioadmin minioadmin123 >/dev/null 2>&1; then
    secondary_minio_ready=true
    break
  fi
  sleep 1
done
if [[ "$secondary_minio_ready" != true ]]; then
  docker logs "$secondary_minio" >&2 || true
  echo "Secondary MinIO did not become ready" >&2
  exit 1
fi
docker run --rm --network "$network" --entrypoint /bin/sh minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec "mc alias set primary http://${minio}:9000 minioadmin minioadmin123 >/dev/null; mc alias set secondary http://${secondary_minio}:9000 minioadmin minioadmin123 >/dev/null; mc mb --ignore-existing primary/switching-backups; mc mb --ignore-existing secondary/switching-backups-dr"

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
docker run --rm -v "$work:/work" --entrypoint age-keygen "$image" -o /work/wrong-age-identity.txt >/dev/null
mkdir -p "$work/backup-work" "$work/metrics" "$work/restore"
chmod -R 0777 "$work/backup-work" "$work/metrics" "$work/restore"

common_env=(
  -e PGHOST="$pg" -e PGPORT=5432 -e PGDATABASE=switching_db
  -e PGUSER=postgres -e PGPASSWORD=test-password -e PGSSLMODE=disable
  -e S3_ENDPOINT="http://${minio}:9000" -e S3_REGION=us-east-1
  -e S3_BUCKET=switching-backups -e S3_PREFIX=switching
  -e AWS_ACCESS_KEY_ID=minioadmin -e AWS_SECRET_ACCESS_KEY=minioadmin123
  -e S3_SERVER_SIDE_ENCRYPTION=none
  -e SECONDARY_S3_ENABLED=true -e SECONDARY_S3_ENDPOINT="http://${secondary_minio}:9000"
  -e SECONDARY_S3_REGION=us-east-1 -e SECONDARY_S3_BUCKET=switching-backups-dr
  -e SECONDARY_AWS_ACCESS_KEY_ID=minioadmin -e SECONDARY_AWS_SECRET_ACCESS_KEY=minioadmin123
)

if docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_RECIPIENT="$recipient" \
  -e BACKUP_AGE_IDENTITY_FILE=/var/run/secrets/backup/wrong-age-identity.txt \
  -v "$work/wrong-age-identity.txt:/var/run/secrets/backup/wrong-age-identity.txt:ro" \
  "$image" /opt/switching-backup/bin/full-backup.sh >"$work/mismatched-identity.log" 2>&1; then
  echo "full backup accepted a mismatched age identity" >&2
  exit 1
fi
grep -Fq 'backup\ identity\ does\ not\ match\ BACKUP_AGE_RECIPIENT' "$work/mismatched-identity.log"
echo "Backup identity mismatch rejection: PASS"

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_RECIPIENT="$recipient" \
  -e BACKUP_AGE_IDENTITY_FILE=/var/run/secrets/backup/age-identity.txt \
  -v "$work/age-identity.txt:/var/run/secrets/backup/age-identity.txt:ro" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  -v "$work/metrics:/var/lib/switching-backup/metrics" \
  "$image" /opt/switching-backup/bin/full-backup.sh

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  "$image" /opt/switching-backup/bin/verify-backup.sh
echo "Secondary offsite replication: PASS"

docker run --rm --network "$network" \
  "${common_env[@]}" \
  "$image" /opt/switching-backup/bin/apply-retention.sh

docker run --rm --network "$network" \
  "${common_env[@]}" \
  --entrypoint /bin/sh "$image" -ec '
    policy_check="
      ([.Rules[].ID] | sort) ==
        [\"AbortIncompleteMultipartUploads\", \"ExpireSwitchingBaseBackups\", \"ExpireSwitchingWalAfterRecoveryWindow\"]
      and ([.Rules[] | select(.ID == \"ExpireSwitchingBaseBackups\").Expiration.Days][0] == 35)
      and ([.Rules[] | select(.ID == \"ExpireSwitchingWalAfterRecoveryWindow\").Expiration.Days][0] == 35)
      and ([.Rules[] | select(.ID == \"AbortIncompleteMultipartUploads\").AbortIncompleteMultipartUpload.DaysAfterInitiation][0] == 1)
    "

    aws --endpoint-url "$S3_ENDPOINT" s3api get-bucket-lifecycle-configuration \
      --bucket "$S3_BUCKET" | jq -e "$policy_check" >/dev/null

    AWS_ACCESS_KEY_ID="$SECONDARY_AWS_ACCESS_KEY_ID" \
    AWS_SECRET_ACCESS_KEY="$SECONDARY_AWS_SECRET_ACCESS_KEY" \
    AWS_DEFAULT_REGION="$SECONDARY_S3_REGION" \
      aws --endpoint-url "$SECONDARY_S3_ENDPOINT" s3api get-bucket-lifecycle-configuration \
        --bucket "$SECONDARY_S3_BUCKET" | jq -e "$policy_check" >/dev/null
  '
echo "Backup retention lifecycle: PASS"

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_IDENTITY_FILE=/var/run/secrets/backup/age-identity.txt \
  -e RESTORE_DRILL_DB_USER=postgres \
  -e RESTORE_TARGET_DIR=/var/lib/switching-backup/restore-drill/data \
  -e RESTORE_DRILL_TIMEOUT_SECONDS=300 \
  -e RESTORE_DRILL_RTO_TARGET_SECONDS=300 \
  -v "$work/age-identity.txt:/var/run/secrets/backup/age-identity.txt:ro" \
  -v "$work/restore:/var/lib/switching-backup/restore-drill" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  -v "$work/metrics:/var/lib/switching-backup/metrics" \
  "$image" /opt/switching-backup/bin/restore-drill.sh

archive_key="$(docker run --rm --network "$network" \
  "${common_env[@]}" \
  --entrypoint /bin/sh "$image" -ec '
    metadata_key="$(aws --endpoint-url "$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$S3_PREFIX/base/latest.json" - | jq -er .metadataKey)"
    aws --endpoint-url "$S3_ENDPOINT" s3 cp "s3://$S3_BUCKET/$metadata_key" - | jq -er .archiveKey
  ')"

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e ARCHIVE_KEY="$archive_key" \
  --entrypoint /bin/sh "$image" -ec '
    printf tampered >/tmp/tampered-archive
    aws --endpoint-url "$S3_ENDPOINT" s3 cp /tmp/tampered-archive "s3://$S3_BUCKET/$ARCHIVE_KEY" --only-show-errors
  '

if docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_IDENTITY_FILE=/var/run/secrets/backup/age-identity.txt \
  -e RESTORE_TARGET_DIR=/var/lib/switching-backup/restore-drill/tampered-data \
  -v "$work/age-identity.txt:/var/run/secrets/backup/age-identity.txt:ro" \
  -v "$work/restore:/var/lib/switching-backup/restore-drill" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  "$image" /opt/switching-backup/bin/restore-basebackup.sh >"$work/tampered-archive.log" 2>&1; then
  echo "restore accepted a tampered backup archive" >&2
  exit 1
fi
grep -Fq 'base\ backup\ checksum\ mismatch' "$work/tampered-archive.log"
echo "Backup archive tamper rejection: PASS"

docker run --rm --network "$network" \
  "${common_env[@]}" \
  --entrypoint /bin/sh "$image" -ec '
    aws --endpoint-url "$S3_ENDPOINT" s3 rm "s3://$S3_BUCKET/$S3_PREFIX/base" --recursive --only-show-errors
  '

docker run --rm --network "$network" \
  "${common_env[@]}" \
  -e BACKUP_AGE_IDENTITY_FILE=/var/run/secrets/backup/age-identity.txt \
  -e RESTORE_DRILL_DB_USER=postgres \
  -e RESTORE_TARGET_DIR=/var/lib/switching-backup/restore-drill/secondary-fallback-data \
  -e RESTORE_DRILL_TIMEOUT_SECONDS=300 \
  -e RESTORE_DRILL_RTO_TARGET_SECONDS=300 \
  -v "$work/age-identity.txt:/var/run/secrets/backup/age-identity.txt:ro" \
  -v "$work/restore:/var/lib/switching-backup/restore-drill" \
  -v "$work/backup-work:/var/lib/switching-backup/work" \
  -v "$work/metrics:/var/lib/switching-backup/metrics" \
  "$image" /opt/switching-backup/bin/restore-drill.sh
echo "Secondary restore fallback: PASS"

docker run --rm --network "$network" --entrypoint /bin/sh minio/mc:RELEASE.2025-04-16T18-13-26Z \
  -ec "mc alias set primary http://${minio}:9000 minioadmin minioadmin123 >/dev/null; mc alias set secondary http://${secondary_minio}:9000 minioadmin minioadmin123 >/dev/null; mc stat secondary/switching-backups-dr/switching/base/latest.json >/dev/null; mc find primary/switching-backups/switching/drill-evidence --name '*.json' >/tmp/drill-evidence.txt; test -s /tmp/drill-evidence.txt"

echo "Phase 8 PostgreSQL/MinIO backup and restore smoke test: PASS"
