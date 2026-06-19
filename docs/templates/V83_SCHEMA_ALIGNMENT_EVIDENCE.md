# V83 Schema Alignment Evidence

> Do not paste passwords, connection strings, digest values, tokens, customer data,
> or unredacted application/database logs into this document.

## Change identification

| Field | Value |
|---|---|
| Environment | |
| Change ticket | |
| Release/source commit | |
| Migration image digest | |
| Operator | |
| Independent reviewer | |
| Start time (Asia/Vientiane) | |
| End time (Asia/Vientiane) | |

## Artifact integrity

| Artifact | SHA-256 |
|---|---|
| `V83__align_payload_sha256_to_varchar.sql` | |
| Migration image | |
| Release manifest | |

## Preflight results

| Check | Expected | Observed | Pass/Fail |
|---|---|---|---|
| Latest successful Flyway version | 82 | | |
| Invalid configuration-change hashes | 0 | | |
| Invalid dead-letter hashes | 0 | | |
| Blocking transaction review completed | Yes | | |
| Backup/restore evidence current | Yes | | |
| Approved change window active | Yes | | |

## Before/after schema metadata

| Table | Before type | After type | Length | Nullable | Pass/Fail |
|---|---|---|---:|---|---|
| `configuration_change_requests` | `CHAR(64)` | `VARCHAR(64)` | 64 | NO | |
| `outbox_dead_letters` | `CHAR(64)` | `VARCHAR(64)` | 64 | NO | |

## Data preservation

| Table | Rows before | Rows after | Length-valid rows after | Pass/Fail |
|---|---:|---:|---:|---|
| `configuration_change_requests` | | | | |
| `outbox_dead_letters` | | | | |

## Constraint evidence

| Constraint | Exists | Validated | Pass/Fail |
|---|---|---|---|
| `ck_config_change_payload_sha256` | | | |
| `ck_outbox_dlq_payload_sha256` | | | |

## Automated tests

| Command/check | Result | Evidence location |
|---|---|---|
| Phase 53B static verifier | | |
| Entity mapping contract test | | |
| V82→V83 PostgreSQL upgrade test | | |
| Full Maven test suite | | |
| Migration image clean DB run | | |
| Migration image second/idempotent run | | |

## Runtime stabilization

| Signal | Baseline | Observed | Pass/Fail |
|---|---|---|---|
| Application readiness | Healthy | | |
| Hibernate schema validation errors | 0 | | |
| Database lock timeout/errors | 0 | | |
| SQL constraint violations | Baseline | | |
| Configuration-change flow | Healthy | | |
| Dead-letter flow | Healthy | | |

## Exceptions and incidents

Document redacted incident/change references only:

- None / reference:

## Sign-off

| Role | Name | Decision | Timestamp |
|---|---|---|---|
| Database owner | | GO / NO-GO | |
| Application owner | | GO / NO-GO | |
| Operations | | GO / NO-GO | |
| Independent reviewer | | GO / NO-GO | |
