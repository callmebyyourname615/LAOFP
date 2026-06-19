# RB-16 — Database Lifecycle

The weekly maintenance job uses a separate least-privileged database account. It fails closed when run
on a replica, when an invalid index exists, or when a transaction older than five minutes is active.
`VACUUM (ANALYZE, SKIP_LOCKED)` is limited to an explicit allowlist; partitioned high-volume tables are
analyzed without a cross-partition full vacuum.

## Maintenance job

The CronJob must be deployed with the immutable `switching-backup` image digest using
`database-maintenance-deploy.yml`. A failed or stale run is an incident: inspect the Job logs and
`database_maintenance_runs`, correct the blocking condition, then create a one-off Job from the
CronJob. Do not edit the CronJob to bypass preflight checks.

## Invalid index

Do not drop or rebuild automatically. Identify the failed concurrent-index operation, compare the index
DDL with migrations, rebuild during an approved window, and verify query plans before removing the old
index.

## Partition gap

Run the existing partition-maintenance service, verify all ten parent tables have today through +7 day
partitions, and investigate scheduler lock or DDL permission failures.

## Long transaction / dead tuples

Find the owning application and statement. Cancel only with incident authorization. Never terminate a
settlement or migration transaction solely to make the metric green.
