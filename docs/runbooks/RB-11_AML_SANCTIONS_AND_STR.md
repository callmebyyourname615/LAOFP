# RB-11 — AML Sanctions Data and STR Submission

## SwitchingSanctionsDataStaleOrEmpty

### Safety rule

Screening remains fail-closed. Do not disable sanctions checks or replace a failed provider snapshot with an empty dataset.

### Diagnose

Check per-provider active count, last successful import, source checksum, rejected records, HTTP/TLS errors and atomic-import audit. Confirm the last-known-good dataset is still active.

### Recover

Restore provider connectivity or credentials, download to staging, validate minimum records and checksum, then execute the atomic swap. A partial or malformed download must be rejected. Confirm freshness is below SLA and sample names match expected fixtures.

## SwitchingStrSubmissionBacklog

Check pending and failed STR rows, retry count, FIU endpoint/TLS/authentication, response reference and last error. STR payloads are restricted compliance data; keep them out of chat, tickets and general logs.

Restore FIU connectivity and allow the idempotent submission worker to retry. Manual submission must be recorded with dual approval and linked to the original report identifier. Never mark a report submitted without authoritative acknowledgement.

## Escalation and evidence

Page AML/FIU operations immediately for stale/empty data or a submission backlog. Preserve provider/import IDs, checksums, timestamps, report references, approvals and final acknowledgements. Obtain AML sign-off before closure.
