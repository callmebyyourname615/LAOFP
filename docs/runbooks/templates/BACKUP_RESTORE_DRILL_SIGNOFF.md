# Backup Restore Drill Sign-Off

## Drill Metadata

- Environment:
- Drill date:
- Drill owner:
- Change or evidence ticket:
- Backup object prefix:
- Restore target:
- Restore target time:

## Objectives

- Validate encrypted base backup restore.
- Validate WAL restore to the requested point in time.
- Validate `pg_verifybackup` passes before database startup.
- Validate the restored database supports application smoke checks.
- Confirm measured RPO and RTO meet the production objective.

## RPO And RTO

- Required RPO:
- Measured RPO:
- Required RTO:
- Measured RTO:
- Result: PASS / FAIL

## Evidence Checklist

- [ ] Base backup object downloaded from primary storage.
- [ ] Secondary storage fallback tested or explicitly waived by Change Manager.
- [ ] Backup checksum verified.
- [ ] Payload decrypted with approved age identity.
- [ ] `pg_verifybackup` completed successfully.
- [ ] WAL segments downloaded and checksum verified.
- [ ] PITR target reached.
- [ ] `restore-verification.sql` completed successfully.
- [ ] Application inquiry smoke check passed against the restored database.
- [ ] Restore drill output uploaded to the evidence bucket.

## Failure Rule

If checksum validation, decryption, `pg_verifybackup`, WAL replay, restore verification, RPO, or RTO fails, the production go-live gate remains closed until the issue has an approved corrective action and a successful repeat drill.

## Sign-Off

- Database owner:
- SRE owner:
- Security owner:
- Change Manager:
- Final decision: APPROVED / REJECTED
