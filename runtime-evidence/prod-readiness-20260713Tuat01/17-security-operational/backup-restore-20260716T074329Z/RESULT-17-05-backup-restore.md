# 17.05 Backup and Restore Drill

Status: PASS WITH NOTE

Evidence:
- An encrypted PostgreSQL physical base backup was created and verified.
- Backup ID `20260716074331-6f8c8423` was restored into an isolated PostgreSQL 16 instance.
- The restored instance completed recovery and was promoted before verification.
- Schema verification passed, including Flyway history, `transactions`, and `outbox_messages`.
- Restored transaction count was 64; latest restored transaction timestamp was `2026-07-16T06:12:16Z`.
- Measured RTO was 7 seconds, within the 3600-second target.
- The production UAT application remained available after the isolated drill.

Operational Note:
- The restore image reports a glibc collation-version mismatch (backup source 2.41, restore image 2.36). It did not affect this drill, but production recovery images must use a matching OS/glibc baseline or complete the documented collation refresh procedure before production sign-off.
- This drill validates base-backup recovery. Production PITR still requires the continuous `backup-wal` service to be enabled and its archived WAL retention to be tested separately.
