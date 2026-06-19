# Phase 53B Validation Report — V83 Schema Alignment

**Date:** 2026-06-19  
**Baseline:** Switching repository with Phase 53A overlay applied  
**Scope:** Phase 53B changed files only  
**Implementation result:** PASS for static/source/configuration validation  
**Runtime result:** PENDING in Docker-enabled CI/UAT

## Validation summary

| Area | Result | Evidence |
|---|---|---|
| Flyway sequence | PASS | V1–V83 contiguous; no duplicate or missing version |
| Historical migration immutability | PASS | V47 and V51 still declare `payload_sha256 CHAR(64)` |
| V83 safety contract | PASS | preconditions, bounded locks, validation, preserving conversion, constraints, postconditions |
| JPA profile contract | PASS | base profile `ddl-auto=validate`; migration profile remains `none` |
| Entity mapping contract | PASS | both fields remain non-null length-64 mappings without `CHAR` definition |
| Integration-test source contract | PASS | V82 upgrade, invalid-data rollback, V83 success, metadata/data/constraint checks |
| Migration image gate | PASS | default expected version 83 and schema assertions present |
| CI and branch protection | PASS | `V83 Schema Alignment` workflow/job/context present |
| Operations material | PASS | rollout runbook and evidence/sign-off template present |
| Shell syntax | PASS | all Phase 53B shell scripts pass `bash -n` |
| Python syntax | PASS | verifier passes `py_compile` |
| YAML parsing | PASS | application, migration-profile and workflow YAML parse successfully |
| Branch-protection payload | PASS | JSON parses; 10 unique required contexts |
| SQL structural checks | PASS | dollar quotes balanced; two conversions and two validated constraints |
| Java structural checks | PASS | balanced delimiters and text blocks for both new tests |
| Repository hygiene | PASS | Phase 53A scanner passed across 1,226 repository files |
| Whitespace/mode checks | PASS | no tabs/trailing whitespace; executables retain executable mode |
| CLI fail-fast behavior | PASS | missing/invalid migration-image arguments return usage code 64 |
| Overlay verification | PASS | `apply-phase53b.sh` and static runner complete successfully |

## Static verifier result

Command:

```bash
python3 scripts/verify_phase53b_schema_alignment.py
```

Result: **9/9 checks PASS**

Checks performed:

1. contiguous migration sequence V1–V83;
2. V47/V51 immutability;
3. V83 safety and data-preservation contract;
4. runtime/migration JPA profile separation;
5. entity mappings;
6. integration/mapping test contracts;
7. migration image verification contract;
8. CI and branch protection wiring;
9. rollout/evidence documentation.

## Runtime tests delivered

### `V83PayloadSha256SchemaAlignmentIntegrationTest`

The test is designed to run against PostgreSQL 16.9 through Testcontainers and
covers:

- clean Flyway migration through V82;
- confirmation of both original `CHAR(64)` types;
- representative V82 data in both tables;
- malformed legacy digest insertion while the old schema still permits it;
- V83 fail-closed migration failure;
- confirmation that transactional rollback leaves the database at V82;
- correction of the malformed row;
- successful V83 migration;
- exact digest preservation and removal of `CHAR` padding;
- `VARCHAR(64) NOT NULL` metadata for both columns;
- both check constraints present and validated;
- SQLSTATE `23514` rejection of malformed post-V83 updates;
- second Flyway run as a no-op with no pending migration.

### `PayloadSha256EntityMappingContractTest`

The reflection test prevents future mapping drift by asserting for both entities:

- column name `payload_sha256`;
- `nullable=false`;
- `length=64`;
- no hard-coded `columnDefinition="CHAR(64)"`.

## Runtime execution limitation in this build environment

The focused Maven command was attempted:

```bash
./mvnw --batch-mode --no-transfer-progress \
  -Dtest=PayloadSha256EntityMappingContractTest,V83PayloadSha256SchemaAlignmentIntegrationTest \
  test
```

It could not start because the environment could not download Maven 3.9.12 from
Maven Central:

```text
wget: Failed to fetch ... apache-maven-3.9.12-bin.zip
```

Docker/PostgreSQL were also unavailable in the artifact environment. Therefore:

- the report does **not** claim that Maven compilation passed locally;
- the report does **not** claim that V83 executed against PostgreSQL locally;
- the dedicated GitHub Actions gate must pass before merge/promotion;
- the migration-image verification must pass in UAT before Production Go-Live.

## Required closure commands

Run in a Docker-enabled environment with Maven dependency access:

```bash
./scripts/run_phase53b_verification.sh targeted
./scripts/run_phase53b_verification.sh full
./scripts/verify_migration_image.sh <migration-image@sha256:digest> 83
```

Then complete:

```text
docs/templates/V83_SCHEMA_ALIGNMENT_EVIDENCE.md
```

## Go/No-Go impact

Phase 53B closes the repository implementation for the A1/A2 schema mismatch:

- JPA fail-fast validation is restored;
- the schema is aligned through immutable forward migration V83;
- automated gates and production rollout evidence are defined.

Production remains **NO-GO** until:

1. Phase 53A credential rotation and remote Git-history purge are closed;
2. the Phase 53B runtime CI/Testcontainers and migration-image gates pass;
3. the remaining production hardening and runtime-evidence phases are completed.
