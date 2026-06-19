# PostgreSQL Switching Database Concept

Last updated: 2026-05-21

This is the implemented database concept for the switching platform. The active
database engine is PostgreSQL, with Flyway migrations under
`src/main/resources/db/migration`.

## Runtime Topology

```text
CLIENTS / BANKS / MEMBERS
        |
        v
switching-api
  - receive inquiry
  - validate participant
  - resolve route
  - create transfer only after valid inquiry
  - create ISO metadata and payload
  - create outbox message
        |
        v
HOT DB: switching_primary / switching_db
  PostgreSQL primary write database
  Retention target: latest 90 days
  Large tables: daily partitions by business date
        |
        +--> HOT READ REPLICA: switching_primary_read_replica
        |    PostgreSQL read-only replica for operations portal,
        |    dashboards, and latest 90-day reports
        |
        +--> switching-archive-worker after 90 days
             - export metadata to WARM DB: switching_archive
             - export payloads/files to COLD STORAGE: MinIO bucket
             - verify row count and checksum
             - drop old partition after verification
```

## Hot Database

The hot database is PostgreSQL. In local Docker it is `switching_db`; in
production it should be exposed as the primary write endpoint, for example
`switching_primary`.

Local Docker endpoint:

```text
host=127.0.0.1
port=5433
database=switching_db
user=switching_app
```

The local read replica is exposed separately:

```text
host=127.0.0.1
port=5435
database=switching_db
user=switching_app
read_only=true
```

Implemented hot tables:

| Domain | Tables |
|---|---|
| Core flow | `payment_flows`, `inquiries`, `transactions`, `transaction_status_history`, `transaction_events`, `idempotency_records` |
| ISO 20022 | `iso_messages`, `iso_message_payloads`, `iso_validation_errors` |
| Reliability | `outbox_messages`, `outbox_attempts`, `dead_letter_messages` |
| Config | `participants`, `participant_limits`, `routing_rules`, `connector_configs`, `connector_credentials`, `connector_rate_limits` |
| Settlement / reconciliation | `settlement_cycles`, `settlement_positions`, `settlement_items`, `reconciliation_files`, `reconciliation_items` |
| Lookup / summary | `transaction_lookup`, `inquiry_lookup`, `hourly_transaction_summary`, `daily_transaction_summary`, `inquiry_daily_summary` |
| Maintenance | `archive_jobs`, `partition_maintenance_logs`, `scheduler_locks` |

Daily partitions are pre-created from 7 days in the past through 90 days ahead
for the large date-driven tables. `PartitionMaintenanceService` keeps the
forward window full every day using `switching.archive.partition-forward-days`
with a default of 90 days.

## Warm Archive

Warm archive metadata lives in a separate PostgreSQL database named
`switching_archive`. The primary also keeps a `switching_archive` schema so the
same Flyway archive metadata model is available before the archive worker is
fully split out.

Local archive endpoint:

```text
host=127.0.0.1
port=5434
database=switching_archive
user=switching_archive
```

Implemented warm archive tables:

| Table | Purpose |
|---|---|
| `switching_archive.payment_flows_archive` | Archived flow metadata |
| `switching_archive.inquiries_archive` | Archived inquiry metadata |
| `switching_archive.transactions_archive` | Archived transaction metadata |
| `switching_archive.transaction_status_history_archive` | Archived transaction status history |
| `switching_archive.transaction_events_archive` | Archived transaction events |
| `switching_archive.iso_messages_archive` | Archived ISO metadata with payload pointers |
| `switching_archive.settlement_items_archive` | Archived settlement items |
| `switching_archive.reconciliation_items_archive` | Archived reconciliation items |
| `switching_archive.connector_call_logs_archive` | Archived connector call metadata |

Archive metadata stores searchable fields only: transaction/inquiry/flow
references, participants, status, amount, business date, and `object_id`.
Payload location, checksum, size, encryption key id, manifest metadata, and
retention policy are owned by the `object_storage` schema.

## Object Storage Metadata Schema

Object storage metadata is separated from archive business metadata. Archive
tables keep only nullable `object_id` references to `object_storage.objects`.

Implemented object storage metadata tables:

| Table | Purpose |
|---|---|
| `object_storage.objects` | Canonical index of payload/file objects in MinIO or compatible storage |
| `object_storage.manifests` | Per-archive-run manifest with row count, checksum, and manifest object pointer |
| `object_storage.retention_policies` | Bucket/prefix retention policy, compression, encryption, and checksum requirements |

Archive tables that can point to cold payloads:

| Archive table | Object reference |
|---|---|
| `switching_archive.payment_flows_archive` | `object_id -> object_storage.objects.id` |
| `switching_archive.inquiries_archive` | `object_id -> object_storage.objects.id` |
| `switching_archive.transactions_archive` | `object_id -> object_storage.objects.id` |
| `switching_archive.iso_messages_archive` | `object_id -> object_storage.objects.id` |
| `switching_archive.reconciliation_items_archive` | `object_id -> object_storage.objects.id` |
| `switching_archive.connector_call_logs_archive` | `object_id -> object_storage.objects.id` |

## Archive Automation

`ArchiveWorkerService` runs daily by default and archives the business date that
has aged past `switching.archive.hot-retention-days` plus one day. The default
hot retention is 90 days.

Archive flow:

1. Acquire `scheduler_locks` entry `switching-archive-worker`.
2. Export ISO payload rows to MinIO as compressed objects.
3. Insert object metadata into `object_storage.objects`.
4. Copy searchable metadata rows to WARM archive tables.
5. Archive ISO validation errors to `iso_validation_errors_archive`.
6. Write a compressed manifest object and `object_storage.manifests` row.
7. Verify archived row counts.
8. Drop the old hot partitions only after verification succeeds.

Archive worker config:

```text
ARCHIVE_HOT_RETENTION_DAYS=90
ARCHIVE_WORKER_ENABLED=true
ARCHIVE_CRON=0 15 1 * * *
PARTITION_MAINTENANCE_ENABLED=true
ARCHIVE_PARTITION_FORWARD_DAYS=90
PARTITION_CRON=0 5 0 * * *
```

## Cold Storage

Large payloads and files are not kept in warm archive tables. They are stored in
on-prem object storage such as MinIO or Ceph.

Target bucket:

```text
switching-archive
```

Local MinIO endpoints:

```text
S3 API:  http://127.0.0.1:9000
Console: http://127.0.0.1:9001
Bucket:  switching-archive
```

Payload object requirements:

- compressed
- encrypted
- SHA-256 checksum recorded in `object_storage.objects`
- object key recorded in `object_storage.objects`
- archive business metadata references payloads by `object_id`

Example object key:

```text
iso/2026/05/17/FLOW001/transfer/TXN001-pacs008.xml.gz.enc
```

## Database Users

Local Docker creates these PostgreSQL roles:

| Role | Responsibility |
|---|---|
| `switching_app` | Runtime DML only: select, insert, update, delete |
| `switching_flyway` | Flyway migrations and DDL |
| `switching_replicator` | Streaming replication from primary to read replica |
| `switching_archive` | Archive database owner |

The app container uses `switching_app`. Flyway uses `switching_flyway`.
Kubernetes secrets should point to PostgreSQL URLs with `sslmode=require`.
