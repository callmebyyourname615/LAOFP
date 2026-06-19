# V83 Payload SHA-256 Schema Alignment Runbook

## Purpose

This runbook governs the rollout of Flyway migration
`V83__align_payload_sha256_to_varchar.sql`. V83 aligns two PostgreSQL columns
with their existing JPA mappings:

| Table | Column | Before | After |
|---|---|---|---|
| `configuration_change_requests` | `payload_sha256` | `CHAR(64)` | `VARCHAR(64) NOT NULL` |
| `outbox_dead_letters` | `payload_sha256` | `CHAR(64)` | `VARCHAR(64) NOT NULL` |

V47 and V51 must never be edited after deployment. V83 is the forward-only
correction that preserves Flyway checksums and supports both clean installs and
upgrades from an existing V82 database.

## Safety properties implemented by V83

- fails before conversion if either table or column is missing;
- acquires both table locks in deterministic order;
- uses a 15-second lock timeout instead of waiting indefinitely;
- uses a 5-minute statement timeout;
- validates every existing value without printing digest contents;
- removes only trailing `CHAR` padding with `rtrim`;
- preserves the existing hexadecimal digest text and letter case;
- adds and validates hexadecimal SHA-256 check constraints;
- verifies PostgreSQL metadata after conversion;
- runs transactionally under Flyway, so an error rolls the migration back.

## Deployment prerequisites

1. Phase 53A repository cleanup is applied.
2. The exact release artifact is built and signed.
3. A recent recoverable database backup exists and its restore evidence is valid.
4. The migration image contains V1 through V83.
5. Application deployment is configured to wait for the migration Job.
6. No manual DDL is running on either affected table.
7. Operations has an approved change window and rollback owner.
8. UAT has passed the V82 to V83 Testcontainers test and migration-image test.

## Pre-deployment checks

Run with a read-only account where possible. These queries report counts and
metadata only; they do not reveal digest values.

### Confirm current Flyway state

```sql
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank DESC
LIMIT 5;
```

Expected before first V83 rollout: latest successful version is `82`.

### Confirm existing column types

```sql
SELECT table_name, column_name, data_type, udt_name,
       character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('configuration_change_requests', 'outbox_dead_letters')
  AND column_name = 'payload_sha256'
ORDER BY table_name;
```

Expected before V83: two non-null `CHAR(64)`/`bpchar` columns.

### Confirm all stored values are convertible

```sql
SELECT 'configuration_change_requests' AS table_name, COUNT(*) AS invalid_rows
FROM configuration_change_requests
WHERE payload_sha256 IS NULL
   OR rtrim(payload_sha256) !~ '^[0-9A-Fa-f]{64}$'
UNION ALL
SELECT 'outbox_dead_letters', COUNT(*)
FROM outbox_dead_letters
WHERE payload_sha256 IS NULL
   OR rtrim(payload_sha256) !~ '^[0-9A-Fa-f]{64}$';
```

Expected: `0` invalid rows for both tables. Do not bypass this condition. Open an
incident and repair/quarantine bad rows through an approved data-correction
procedure before retrying.

### Check blocking sessions

```sql
SELECT activity.pid, activity.usename, activity.application_name,
       activity.state, activity.xact_start, activity.query_start,
       wait_event_type, wait_event
FROM pg_stat_activity activity
WHERE activity.datname = current_database()
  AND activity.xact_start IS NOT NULL
ORDER BY activity.xact_start;
```

Do not terminate sessions without incident/change approval. Reschedule the
migration if long-running transactions make the 15-second lock SLA unrealistic.

## Rollout sequence

1. Freeze schema-changing deployments.
2. Record the pre-deployment queries in the evidence template.
3. Scale or pause write traffic only if required by the approved change plan.
4. Run the dedicated migration image/Job with `SPRING_PROFILES_ACTIVE=migration`.
5. Require migration Job exit code `0` before starting application pods.
6. Start the application with the normal environment profile. The base config now
   uses `spring.jpa.hibernate.ddl-auto=validate`; schema drift therefore stops
   startup instead of being ignored.
7. Run the post-deployment verification below.
8. Observe errors, DB locks, application readiness and latency through the agreed
   stabilization window.

## Post-deployment verification

### Flyway version and checksum

```sql
SELECT version, description, checksum, success, installed_on, execution_time
FROM flyway_schema_history
WHERE version = '83';
```

Expected: one successful V83 row.

### Column metadata

```sql
SELECT table_name, data_type, character_maximum_length, is_nullable
FROM information_schema.columns
WHERE table_schema = current_schema()
  AND table_name IN ('configuration_change_requests', 'outbox_dead_letters')
  AND column_name = 'payload_sha256'
ORDER BY table_name;
```

Expected: two rows, both `character varying`, length `64`, nullable `NO`.

### Constraint validation

```sql
SELECT table_metadata.relname AS table_name,
       constraint_metadata.conname,
       constraint_metadata.convalidated,
       pg_get_constraintdef(constraint_metadata.oid) AS definition
FROM pg_constraint constraint_metadata
JOIN pg_class table_metadata
  ON table_metadata.oid = constraint_metadata.conrelid
JOIN pg_namespace schema_metadata
  ON schema_metadata.oid = table_metadata.relnamespace
WHERE schema_metadata.nspname = current_schema()
  AND constraint_metadata.conname IN (
      'ck_config_change_payload_sha256',
      'ck_outbox_dlq_payload_sha256'
  )
ORDER BY table_name;
```

Expected: both constraints exist and `convalidated = true`.

### Data count and digest-length checks

Capture row counts before and after and compare:

```sql
SELECT 'configuration_change_requests' AS table_name,
       COUNT(*) AS rows,
       COUNT(*) FILTER (WHERE length(payload_sha256) = 64) AS valid_length_rows
FROM configuration_change_requests
UNION ALL
SELECT 'outbox_dead_letters', COUNT(*),
       COUNT(*) FILTER (WHERE length(payload_sha256) = 64)
FROM outbox_dead_letters;
```

Expected: no row-count loss and `rows = valid_length_rows` for both tables.

### Application checks

- application readiness becomes healthy;
- no Hibernate schema-validation exception appears;
- configuration-change create/read paths remain healthy;
- dead-letter quarantine/list/replay integrity checks remain healthy;
- no increase in SQL errors, lock waits, or transaction rollback rate.

## Automated verification commands

```bash
# Dependency-free static contract gate
./scripts/run_phase53b_verification.sh static

# Static gate + focused JPA/Testcontainers tests
./scripts/run_phase53b_verification.sh targeted

# Focused tests followed by the complete Maven suite
./scripts/run_phase53b_verification.sh full

# Verify a built migration image against a clean PostgreSQL database twice
./scripts/verify_migration_image.sh <migration-image@sha256:digest> 83
```

## Failure and rollback behavior

### V83 fails before commit

Flyway/PostgreSQL rolls back the migration transaction. Keep application rollout
blocked, collect the redacted error and lock evidence, correct the condition, and
rerun the same immutable image. Never mark the migration successful manually.

### V83 succeeds but the new application fails

Rollback the application image only. The previous application already maps these
fields as length-64 strings and remains compatible with `VARCHAR(64)`. A database
downgrade is not required.

### A database type reversal is exceptionally required

Do not edit/delete V83 and do not change `flyway_schema_history`. Create a reviewed
forward migration, for example V84, that validates every value and explicitly
converts back to `CHAR(64)`. This should be treated as a new production change,
not an emergency shell command.

## Evidence to retain

- release image digest and source commit;
- V83 SQL checksum;
- preflight invalid-row counts;
- pre/post table row counts;
- pre/post column metadata;
- Flyway V83 history row and execution time;
- constraint validation output;
- migration Job logs with secrets redacted;
- targeted and full Maven reports;
- application readiness and stabilization-window metrics;
- change owner, reviewer and approval references.

Use `docs/templates/V83_SCHEMA_ALIGNMENT_EVIDENCE.md` as the sign-off record.
