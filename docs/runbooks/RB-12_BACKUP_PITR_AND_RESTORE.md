# RB-12 — PostgreSQL Backup, WAL Archive, PITR and Restore Drill

## Purpose

This runbook protects the active `switching_db` PostgreSQL database with:

- one encrypted physical base backup every day;
- continuous WAL streaming through a dedicated physical replication slot;
- a primary and mandatory off-site/secondary S3-compatible copy;
- checksum and `pg_verifybackup` validation before a backup is published as latest;
- an isolated monthly restore drill that starts PostgreSQL and runs verification SQL;
- evidence JSON and Prometheus metrics for go/no-go review.

A successful upload alone is not proof of recoverability. Production readiness requires a recent restore drill.

## Recovery objectives

Initial engineering targets:

- base backup interval: 24 hours;
- WAL archive freshness: less than 15 minutes;
- restore drill frequency: at least monthly;
- PITR RPO target: 5 minutes or lower while WAL receiver is healthy;
- restore RTO target: 60 minutes for the isolated drill.

Business and operations owners must approve the final values.

## Security model

The backup runtime receives only the public `age` recipient. It cannot decrypt backups. The private age identity is stored at `switching/prod/restore` in Vault and mounted only into restore jobs. Objects are encrypted before upload and may additionally use S3 server-side encryption.

Never store the age private identity, database password, or S3 secret in Git, CI artifacts, logs, or ConfigMaps.

## Bootstrap

1. Generate the encryption identity offline:

```bash
age-keygen -o age-identity.txt
age-keygen -y age-identity.txt
```

2. Store the private file content in Vault property `BACKUP_AGE_IDENTITY` under `switching/prod/restore`.
3. Store the printed public recipient and backup credentials under `switching/prod/backup`.
4. Create a dedicated PostgreSQL role with `LOGIN REPLICATION`; do not reuse the application or Flyway user.
5. Ensure `pg_hba.conf` accepts replication connections only from backup worker CIDRs and requires TLS/SCRAM.
6. Apply `k8s/external-secrets/backup-secrets.yaml`, then wait for both generated Secrets.
7. Build and deploy the digest-pinned backup image using the Phase 8 workflows.
8. Replace storage class and endpoint placeholders in `k8s/backup/`.

## First validation

```bash
kubectl -n switching create job --from=cronjob/switching-full-backup switching-full-backup-manual
kubectl -n switching logs -f job/switching-full-backup-manual
kubectl -n switching exec deploy/switching-wal-archiver -- \
  /opt/switching-backup/bin/verify-backup.sh
```

Confirm:

- `latest.json` exists on primary and secondary storage;
- the archive, checksum, and metadata keys exist on both targets;
- WAL objects continue to advance;
- the physical replication slot does not retain excessive WAL;
- Pushgateway contains fresh backup and WAL timestamps.

## Manual restore drill

The scheduled restore drill is intentionally created with `spec.suspend: true`. Enable it only after storage sizing and secrets are validated.

```bash
kubectl -n switching create job --from=cronjob/switching-restore-drill switching-restore-drill-manual
kubectl -n switching logs -f job/switching-restore-drill-manual
```

The job downloads the latest base backup, verifies its checksum, decrypts it, runs `pg_verifybackup`, replays WAL, promotes the isolated server, runs `restore-verification.sql`, and uploads evidence JSON.

Do not use a production PVC as the restore target. The drill manifest uses a generic ephemeral PVC.

## Point-in-time recovery

Set an exact UTC target before creating a one-off restore Job:

```yaml
- name: RECOVERY_TARGET_TIME
  value: "2026-06-18T03:00:00Z"
```

Recovery fails closed when a required WAL segment is absent or its encrypted checksum does not match. Investigate the WAL chain; never bypass verification.

## SwitchingBaseBackupStale

1. Inspect CronJob and latest Job events.
2. Check replication authentication, TLS CA, disk space, `pg_basebackup`, age encryption, and both S3 endpoints.
3. Confirm no older backup process holds the file lock.
4. Run one manual backup after remediation.

## SwitchingBaseBackupFailed

Read the failed Job log without printing environment variables. The script preserves no successful latest pointer until archive, checksum, and metadata uploads pass.

## SwitchingWalArchiveStale

1. Check `switching-wal-archiver` liveness and spool PVC usage.
2. Inspect `pg_replication_slots` on the primary.
3. Check object storage and secondary storage reachability.
4. Do not drop the replication slot while unarchived WAL remains in the spool.
5. If retained WAL threatens the primary disk, escalate to the DBA and preserve the spool before slot recreation.

## SwitchingWalArchiveFailed

The uploader removes a segment only after encryption, checksum, primary upload, and required secondary upload succeed. Repair credentials/network/storage, then allow the uploader loop to retry the retained file.

## SwitchingBackupCrossRegionCopyFailed

Verify both latest metadata and archive objects on secondary storage. Production must not silently downgrade to one copy. Repair the secondary target and rerun `verify-replication.sh`.

## SwitchingRestoreDrillOverdue

Run a manual isolated drill, review evidence, and obtain operations sign-off. A stale drill closes the production readiness gate.

## SwitchingRestoreDrillFailedOrMissedRto

Preserve Job logs, PostgreSQL logs, evidence, image digest, backup ID, recovery target, start/end timestamps, and resource usage. Fix the failure and repeat the complete drill; do not edit evidence to mark it passed.

## UAT failover drill

```bash
DRILL_ENVIRONMENT=uat ./scripts/uat_failover_drill.sh
```

The script stops application writes, inserts a marker, waits for replay, stops the primary, promotes the replica, verifies the marker, and writes evidence. It deliberately does not restart the former primary because doing so can create split brain. Re-seed it as a standby before resuming the original topology.

## Human sign-off

Complete `docs/runbooks/templates/BACKUP_RESTORE_DRILL_SIGNOFF.md` after every formal restore or failover drill. Attach immutable evidence references and keep the go-live gate closed when any required approver marks the drill failed.
