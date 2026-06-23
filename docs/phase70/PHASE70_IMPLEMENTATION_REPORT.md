# Phase 70A–70J Implementation Report

## Delivery status

**Code status:** COMPLETE  
**Repository certification status:** BLOCKED at Maven bootstrap in this sandbox

Phase 70 was implemented against the supplied `Switching.zip`. The archive baseline is commit `f5a2453` (Phase 61) and does not contain Phase 68 or Phase 69 files. The delivery therefore uses isolated Phase 70 paths and limits edits to the explicitly named P0 blockers and integration points.

## Implemented scope

### 70A — Webhook ObjectMapper context closure
- Replaced hard ObjectMapper dependency in webhook crypto configuration with `ObjectProvider<ObjectMapper>`.
- Added an internal Java-time-aware fallback mapper for isolated contexts.
- Added local and Vault Transit context regression tests without a global ObjectMapper bean.

### 70B — FK-safe operations route generation
- Removed destructive participant cleanup from the integration test.
- Restricted route candidates to active participants with an enabled connector.
- Added a static regression guard against participant deletion.

### 70C — PostgreSQL timestamptz binding
- Added `PostgresTemporalBinder` using `Types.TIMESTAMP_WITH_TIMEZONE`.
- Integrated explicit binding into cross-border rail journal inserts.
- Added null and non-null JDBC binding tests.

### 70D–70F — Participant traffic governance
- Added independent Bucket4j token buckets per authenticated participant/client identity.
- Added SHA-256 legacy API-key identity representation; plaintext credentials are not retained.
- Added bounded identity cardinality and a shared overflow bucket.
- Added external versioned quota policy, scheduled reload, last-known-good retention, and immutable classpath fallback.
- Added `429`, `Retry-After`, limit/remaining/policy headers, structured error `REQ-004`, and rate-limited audit evidence.

### 70G–70H — Promotion financial safety
- Strengthened concurrent promotion budget reservation test with synchronized workers, bounded waits, and exact cap assertions.
- Added funder-ledger reconciliation DTO, service, operations endpoint, unit tests, and integration test.
- Reconciliation covers budget reservation, budget consumption, applications, settled/pending/failed/reversed settlement amounts.

### 70I — Read consistency controls
- Added explicit `STRICT_PRIMARY`, `READ_YOUR_WRITES`, and `EVENTUAL` semantics.
- Added PostgreSQL replica freshness/lag probe.
- Added primary failover for stale, unreachable, or incorrectly configured replicas.
- Added policy documentation and routing tests.

### 70J — Verification and evidence gate
- Added fail-closed Phase 70 orchestrator.
- Added static contract verifier, JSON result schema, and GitHub Actions workflow.
- The runner always emits a result manifest, including the failed step when verification cannot continue.

## Collision boundaries

The delivery contains no changes under:

- `scripts/phase65/**`
- `scripts/phase66/**`
- `scripts/phase67/**`
- `scripts/phase68/**`
- `scripts/phase69/**`
- `src/main/resources/db/migration/**`

`docs/GO_LIVE_CRITICAL_PATH.md`, existing evidence, `.git`, `target`, and Phase 61 evidence are excluded from the delivery.
