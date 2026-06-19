# Phase 08 — Backup, PITR, Restore and Failover Evidence

## Implemented controls

### Phase 8A — Automated backup and WAL archive

- immutable backup image based on PostgreSQL 16 client tools;
- daily `pg_basebackup` in plain physical format;
- `pg_verifybackup` before publication;
- SHA-256 checksum over the encrypted archive;
- client-side age encryption;
- continuous `pg_receivewal` using a named physical replication slot;
- durable WAL spool with delete-after-both-uploads semantics;
- primary and optional mandatory secondary S3-compatible storage;
- object lifecycle policy aligned to a 35-day recovery window;
- Pushgateway metrics and critical alerts.

### Phase 8B — PITR restore tooling

- latest or selected backup resolution;
- checksum validation and age decryption;
- `pg_verifybackup` after extraction;
- generated `restore_command` with primary-to-secondary fallback;
- exact UTC `recovery_target_time` validation;
- recovery on the latest timeline and automatic promotion;
- external tablespaces rejected until explicit mappings are designed.

### Phase 8C — drills and evidence

- isolated Kubernetes restore drill on an ephemeral PVC;
- PostgreSQL start/readiness and schema verification;
- transaction count and newest transaction timestamp evidence;
- measured RTO and target result metric;
- evidence JSON uploaded to both required storage targets;
- guarded UAT primary-stop/replica-promote script.

## Important operational decisions

- Backup and restore credentials are separate from application/Flyway credentials.
- Backup workers possess only the public encryption recipient.
- Restore identity is available only to restore jobs.
- The latest pointer is written last, after archive/checksum/metadata succeed.
- Secondary copy failure fails the backup when `SECONDARY_S3_ENABLED=true`.
- Restore CronJob is suspended by default until a manual drill passes.
- The replication slot is bounded in UAT with `max_slot_wal_keep_size`; production needs an approved value and disk alert.

## Remaining environment validation

- build and scan the backup image;
- create least-privilege replication role and TLS rule;
- apply lifecycle policy to both repositories;
- prove WAL continuity across a timeline switch;
- run empty/new and existing database restore drills;
- measure actual RPO/RTO with production-sized data;
- verify S3 object lock/versioning requirements with the infrastructure team;
- run a failover drill using the real database routing/failover technology.
