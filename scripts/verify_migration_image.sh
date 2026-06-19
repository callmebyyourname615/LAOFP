#!/usr/bin/env bash
set -Eeuo pipefail

IMAGE_REF="${1:-}"
EXPECTED_VERSION="${2:-83}"

if [[ -z "${IMAGE_REF}" ]]; then
  echo "Usage: $0 <container-image-ref> [expected-migration-version]" >&2
  exit 64
fi
if [[ ! "${EXPECTED_VERSION}" =~ ^[1-9][0-9]*$ ]]; then
  echo "Expected migration version must be a positive integer, got: ${EXPECTED_VERSION}" >&2
  exit 64
fi
for command_name in docker; do
  command -v "${command_name}" >/dev/null 2>&1 || {
    echo "Required command not found: ${command_name}" >&2
    exit 69
  }
done

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
  postgres:16.9-alpine >/dev/null

ready=false
for _ in $(seq 1 60); do
  if docker exec "${postgres}" pg_isready \
      -U switching_migration -d switching_migration >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [[ "${ready}" != "true" ]]; then
  echo "PostgreSQL did not become ready within 60 seconds" >&2
  exit 1
fi

run_migration() {
  docker run --rm --network "${network}" --read-only \
    --tmpfs /tmp:rw,noexec,nosuid,size=64m \
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

query() {
  docker exec "${postgres}" psql \
    -v ON_ERROR_STOP=1 \
    -U switching_migration \
    -d switching_migration \
    -Atc "$1"
}

echo "[migration-test] Running against an empty database"
run_migration

echo "[migration-test] Running again against an existing database"
run_migration

failed_count="$(query \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE")"
success_count="$(query \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL")"
current_version="$(query \
  "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1")"
pending_count="$(query \
  "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE AND version::INTEGER > ${EXPECTED_VERSION}")"
plaintext_column_count="$(query \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='webhook_registrations' AND column_name='secret_plain'")"
aligned_sha_column_count="$(query \
  "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('configuration_change_requests','outbox_dead_letters') AND column_name='payload_sha256' AND data_type='character varying' AND character_maximum_length=64 AND is_nullable='NO'")"
validated_sha_constraint_count="$(query \
  "SELECT COUNT(*) FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='public' AND c.conname IN ('ck_config_change_payload_sha256','ck_outbox_dlq_payload_sha256') AND c.contype='c' AND c.convalidated")"

if [[ "${failed_count}" != "0" ]]; then
  echo "Migration verification failed: failed migrations=${failed_count}" >&2
  exit 1
fi
if [[ "${success_count}" != "${EXPECTED_VERSION}" ]]; then
  echo "Migration verification failed: expected ${EXPECTED_VERSION} successful versioned migrations, got ${success_count}" >&2
  exit 1
fi
if [[ "${current_version}" != "${EXPECTED_VERSION}" ]]; then
  echo "Migration verification failed: expected current version ${EXPECTED_VERSION}, got ${current_version}" >&2
  exit 1
fi
if [[ "${pending_count}" != "0" ]]; then
  echo "Migration verification failed: found versions newer than expected=${EXPECTED_VERSION}" >&2
  exit 1
fi
if [[ "${plaintext_column_count}" != "0" ]]; then
  echo "Migration verification failed: webhook_registrations.secret_plain still exists" >&2
  exit 1
fi
if [[ "${aligned_sha_column_count}" != "2" ]]; then
  echo "Migration verification failed: expected two non-null VARCHAR(64) payload_sha256 columns, got ${aligned_sha_column_count}" >&2
  exit 1
fi
if [[ "${validated_sha_constraint_count}" != "2" ]]; then
  echo "Migration verification failed: expected two validated SHA-256 check constraints, got ${validated_sha_constraint_count}" >&2
  exit 1
fi

echo "[migration-test] PASS: version=${current_version}, success=${success_count}, failed=${failed_count}, plaintextColumns=0, alignedShaColumns=2, validatedShaConstraints=2, processExitCode=0Twice"
