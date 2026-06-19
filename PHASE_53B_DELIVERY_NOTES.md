# Phase 53B Delivery Notes — V83 Schema Alignment

## Objective

Close the production-blocking Hibernate/PostgreSQL type mismatch for
`payload_sha256` without rewriting historical Flyway migrations or weakening JPA
schema validation.

## Root cause

Flyway V47 and V51 created `payload_sha256` as PostgreSQL `CHAR(64)`, while both
JPA entities map the fields using `@Column(length = 64)`, which Hibernate expects
as `VARCHAR(64)`. The base application temporarily used `ddl-auto: none`, hiding
the mismatch instead of failing fast.

## Implementation

### 1. Forward-only Flyway V83

Added `V83__align_payload_sha256_to_varchar.sql`.

The migration:

- preserves V47/V51 checksums by leaving historical SQL unchanged;
- validates table and column existence;
- enforces bounded lock and statement timeouts;
- locks both tables in deterministic order;
- counts invalid records without printing digest values;
- refuses NULL/non-hex/non-64-character data;
- converts `CHAR(64)` to `VARCHAR(64)` using `rtrim` only;
- adds validated hexadecimal SHA-256 check constraints;
- adds column comments;
- verifies final type, length, nullability and constraint metadata.

### 2. JPA fail-fast restored

Changed base `application.yml` from:

```yaml
spring.jpa.hibernate.ddl-auto: none
```

to:

```yaml
spring.jpa.hibernate.ddl-auto: validate
```

The dedicated `migration` profile intentionally remains `ddl-auto: none` because
Hibernate auto-configuration is not part of the one-shot schema migration process.

### 3. Runtime upgrade coverage

Added `V83PayloadSha256SchemaAlignmentIntegrationTest` using PostgreSQL 16.9 and
Testcontainers. It:

1. migrates a clean database only through V82;
2. confirms the original `CHAR(64)` types;
3. inserts representative rows into both affected tables;
4. migrates through V83;
5. validates Flyway version 83;
6. verifies exact digest preservation and removal of padding;
7. verifies `VARCHAR(64) NOT NULL` metadata;
8. verifies both constraints are validated;
9. proves malformed values are rejected with SQLSTATE `23514`;
10. reruns Flyway and confirms no pending migration.

Added a separate reflection-based entity mapping contract test to prevent a future
`CHAR(64)` `columnDefinition` from being reintroduced.

### 4. Static and CI gates

Added:

- `scripts/verify_phase53b_schema_alignment.py`;
- `scripts/run_phase53b_verification.sh`;
- `.github/workflows/phase53b-schema-alignment.yml`;
- required branch-protection context `V83 Schema Alignment`.

The static verifier checks migration continuity V1–V83, historical migration
immutability, V83 safety clauses, profile settings, entity contracts, tests,
migration-image verification and CI wiring.

### 5. Migration image verification

Enhanced `scripts/verify_migration_image.sh` to:

- expect V83 by default;
- accept an explicit expected version;
- run the migration image twice;
- require exactly 83 successful versioned migrations;
- require no failed migrations;
- verify the plaintext webhook column remains absent;
- verify two aligned non-null `VARCHAR(64)` columns;
- verify both SHA-256 constraints are validated.

### 6. Operations material

Added:

- `docs/runbooks/V83_SCHEMA_ALIGNMENT.md`;
- `docs/templates/V83_SCHEMA_ALIGNMENT_EVIDENCE.md`.

These include preflight SQL, lock review, rollout ordering, post-deployment
verification, application rollback compatibility, forward-fix policy and sign-off
evidence requirements.

## Files changed or added

- `src/main/resources/application.yml`
- `src/main/resources/db/migration/V83__align_payload_sha256_to_varchar.sql`
- `src/test/java/com/example/switching/migration/V83PayloadSha256SchemaAlignmentIntegrationTest.java`
- `src/test/java/com/example/switching/migration/PayloadSha256EntityMappingContractTest.java`
- `scripts/verify_phase53b_schema_alignment.py`
- `scripts/run_phase53b_verification.sh`
- `scripts/verify_migration_image.sh`
- `scripts/configure_branch_protection.sh`
- `.github/workflows/phase53b-schema-alignment.yml`
- `docs/runbooks/V83_SCHEMA_ALIGNMENT.md`
- `docs/templates/V83_SCHEMA_ALIGNMENT_EVIDENCE.md`
- `PHASE_53B_DELIVERY_NOTES.md`
- `PHASE_53B_VALIDATION_REPORT.md`
- `IMPLEMENTATION_PROGRESS.md`
- `apply-phase53b.sh`
- `PHASE_53B_FILE_MANIFEST.txt`

## Compatibility

- Existing V82 databases: supported through forward migration V83.
- Clean databases: V1–V83 apply in order.
- Previous application image: compatible with the V83 `VARCHAR(64)` schema because
  its entities already use length-64 String mappings.
- Migration Job: continues using `ddl-auto: none` and Flyway lifecycle orchestration.

## Production status

Phase 53B code is complete when static, targeted and full-suite gates pass in CI/UAT.
It removes the A1/A2 schema-alignment blocker from the readiness plan. Production
remains NO-GO until Phase 53A operational closure and the remaining production
hardening/runtime-evidence phases are complete.
