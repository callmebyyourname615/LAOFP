#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_REF="${1:-}"
if [[ -z "${IMAGE_REF}" ]]; then
  echo "Usage: $0 <container-image-ref>" >&2
  exit 64
fi

suffix="${RANDOM}-$$"
network="switching-migration-${suffix}"
postgres="switching-migration-postgres-${suffix}"
password="migration-test-password"

cleanup() {
  docker rm -f "${postgres}" >/dev/null 2>&1 || true
  docker network rm "${network}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker network create "${network}" >/dev/null
docker run -d --name "${postgres}" --network "${network}" \
  -e POSTGRES_DB=switching_migration \
  -e POSTGRES_USER=switching_migration \
  -e POSTGRES_PASSWORD="${password}" \
  postgres:16 >/dev/null

for _ in $(seq 1 60); do
  if docker exec "${postgres}" pg_isready -U switching_migration -d switching_migration >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

docker exec "${postgres}" pg_isready -U switching_migration -d switching_migration >/dev/null

run_migration() {
  docker run --rm --network "${network}" --read-only --tmpfs /tmp:rw,noexec,nosuid,size=64m \
    --entrypoint java \
    -e SPRING_PROFILES_ACTIVE=migration \
    -e FLYWAY_URL="jdbc:postgresql://${postgres}:5432/switching_migration" \
    -e FLYWAY_USERNAME=switching_migration \
    -e FLYWAY_PASSWORD="${password}" \
    -e WEBHOOK_ENCRYPTION_PROVIDER=local \
    -e WEBHOOK_LOCAL_MASTER_KEY_BASE64=NImwCmFwkSIeDgy8UJtzGq86A389puEEe6gi2Wdo9MM= \
    "${IMAGE_REF}" \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Dloader.main=com.example.switching.migration.MigrationApplication \
    -cp /app/app.jar \
    org.springframework.boot.loader.launch.PropertiesLauncher
}

echo "[migration-test] Running against an empty database"
run_migration

echo "[migration-test] Running again against an existing database"
run_migration

failed_count="$(docker exec "${postgres}" psql -U switching_migration -d switching_migration -Atc \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE")"
success_count="$(docker exec "${postgres}" psql -U switching_migration -d switching_migration -Atc \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE")"
current_version="$(docker exec "${postgres}" psql -U switching_migration -d switching_migration -Atc \
  "SELECT version FROM flyway_schema_history WHERE success = TRUE ORDER BY installed_rank DESC LIMIT 1")"
plaintext_column_count="$(docker exec "${postgres}" psql -U switching_migration -d switching_migration -Atc \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='webhook_registrations' AND column_name='secret_plain'")"

if [[ "${failed_count}" != "0" || "${success_count}" -lt "1" ]]; then
  echo "Migration verification failed: success=${success_count}, failed=${failed_count}" >&2
  exit 1
fi
if [[ "${current_version}" != "44" ]]; then
  echo "Migration verification failed: expected current version 44, got ${current_version}" >&2
  exit 1
fi
if [[ "${plaintext_column_count}" != "0" ]]; then
  echo "Migration verification failed: secret_plain column still exists" >&2
  exit 1
fi

echo "[migration-test] PASS: version=${current_version}, success=${success_count}, failed=${failed_count}, plaintextColumns=0, process exit code=0 twice"
