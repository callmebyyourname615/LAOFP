# Secret and Data Exposure Rotation Checklist

## Rule

A credential committed to Git is exposed even if it was never used in production
and even after the file is deleted. Rotation means issuing a new value, deploying
it through the approved secret manager, verifying service health, and revoking
the old value. Do not record secret values in this checklist.

## Required inventory

| Credential / data class | Required action | Verification evidence |
|---|---|---|
| PostgreSQL bootstrap/admin password | Rotate; restrict login/network scope | Secret version/ID, DB auth test, old credential rejected |
| Switching application DB password | Rotate and redeploy application | New pod connection succeeds; old login rejected |
| Flyway migration DB password | Rotate separately from app role | Migration job auth succeeds; old login rejected |
| Replication/backup DB password | Rotate if present in any related artifact | Replica/WAL/backup authentication succeeds |
| Message crypto master key | Create a new key version and follow approved data-key re-encryption/compatibility procedure | Key version ID, decrypt compatibility test, old key disabled after grace period |
| Webhook/local encryption key | Remove local key from non-development use; rotate Vault Transit key where exposure cannot be excluded | New signing/encryption key version, callback verification, old version verify-only/disabled |
| OAuth/JWT/signing secrets | Rotate if logs/dump/history could contain them | New token accepted; pre-rotation tokens rejected according to incident decision |
| Participant/API credentials | Revoke and reissue any credentials contained in database rows, logs, or fixtures | Participant confirmation and old-key rejection test |
| Object-storage credentials | Rotate if referenced by exposed env/log material | Read/write test using new principal; old principal disabled |
| Kafka credentials/keystore passwords | Rotate if present in any contaminated source or artifact | SASL/TLS connection succeeds; old credentials rejected |
| External BoL/FIU/RTGS credentials | Notify owner and rotate through the approved external process if exposure is possible | Provider ticket/reference and validation result |
| Database dump data | Classify affected data, access scope, retention, notification obligations, and evidence handling | Incident classification and approved disposition |
| Runtime logs | Review for tokens, headers, account identifiers, personal data, and stack traces | Redacted review record and deletion confirmation |

## Execution record

Create one row per rotated credential in the incident/change system. Record only:

- credential logical name and environment;
- owning team and approver;
- secret-manager path or credential ID, not its value;
- old version ID and new version ID;
- issue time, deployment time, and revocation time in UTC;
- dependent services restarted or reloaded;
- verification command/result with all sensitive output redacted;
- rollback decision and grace-period expiry;
- incident/change ticket reference.

## Rotation order

1. Freeze deployment and repository writes.
2. Rotate externally usable credentials first: API, database, object storage,
   Kafka, OAuth/signing, and provider credentials.
3. Deploy new secret references without committing values.
4. Verify application, migration, backup, replication, callbacks, and monitoring.
5. Revoke old credentials and confirm rejected authentication.
6. Rotate cryptographic keys according to data compatibility and grace-period
   requirements; never destroy a key still required to decrypt retained data.
7. Purge Git history and invalidate old clones/caches.
8. Run full history, repository, dependency, and container security gates.
9. Close the incident only after old credentials are unusable and evidence is approved.

## Sign-off

- [ ] Security owner approved scope and rotation order
- [ ] Database credentials rotated and old values rejected
- [ ] Application/crypto/signing credentials rotated as applicable
- [ ] External/provider credentials addressed
- [ ] Data/log exposure classified
- [ ] Git history purged and rescanned
- [ ] Existing clones, CI caches, source archives, and forks invalidated
- [ ] Branch protection and security gates enabled
- [ ] Redacted evidence package approved

## Go-Live P0 six-credential closure

| Logical credential | Secret-manager reference | New version ID | New credential verified | Old credential rejected | Approver |
|---|---|---|---|---|---|
| PostgreSQL admin/bootstrap | | | [ ] | [ ] | |
| PostgreSQL replication | | | [ ] | [ ] | |
| Switching application DB | | | [ ] | [ ] | |
| Flyway migration DB | | | [ ] | [ ] | |
| Archive PostgreSQL | | | [ ] | [ ] | |
| MinIO/object storage root | | | [ ] | [ ] | |

Do not place credential values in this document. Attach only redacted command output,
secret version identifiers, UTC timestamps, and the change/incident reference.
