# Phase II-05 through II-24 Delivery Notes

> Scope: Request-to-Pay completion, Promotion Management, Push Payment Orchestrator, Cross-border Rail Framework, Scheduled Report Delivery, and Phase II hardening.
> Baseline: Uploaded Switching.zip plus Phase II-01 through II-04 overlay.
> Runtime flags: all new runtime paths remain disabled by default.

## Recommended 20-phase continuation

| Phase | Capability |
|---|---|
| II-05 | RTP full authorisation and transfer settlement gateway |
| II-06 | RTP partial payment and installment ledger |
| II-07 | RTP expiry, installment execution, events and metrics |
| II-08 | Promotion schema and domain foundation |
| II-09 | Promotion eligibility DSL and priority |
| II-10 | Promotion budget concurrency safety |
| II-11 | Fee pipeline promotion breakdown |
| II-12 | Promotion settlement, operator API and reports |
| II-13 | Payment lifecycle abstraction and parity harness |
| II-14 | Push Payment Orchestrator core and policy store |
| II-15 | Transfer/QR/bill feature-flagged migration to orchestrator |
| II-16 | RTP and cross-border orchestrator integration |
| II-17 | Durable cross-border rail journal and security framework |
| II-18 | PromptPay/NITMX and Bakong adapters |
| II-19 | NAPAS, UPI inward-only and rail reconciliation |
| II-20 | Report delivery foundation |
| II-21 | SFTP delivery with atomic upload |
| II-22 | S3 and email-link delivery |
| II-23 | Report operations and retry lifecycle |
| II-24 | Phase II hardening, static gate and certification glue |

## Delivered repository changes

- Added migrations V86, V87, V88, V89 and V90.
- Completed RTP authorisation, decline, settlement-confirmation, partial and installment flows.
- Added RTP expiry and installment schedulers with batch claiming and retry metadata.
- Added bounded Promotion Management domain with budget reservation, settlement and reports.
- Added fee-result promotion breakdown while preserving default no-promotion behavior.
- Added central Push Payment Orchestrator contracts, policy table, retry scheduler and operator policy endpoint.
- Added feature-flagged transfer, QR and bill delegation hooks.
- Added cross-border rail journal, mTLS/HMAC/OAuth2 guardrails, adapters for PromptPay, Bakong, NAPAS and UPI inward-only.
- Added repeatable-read rail reconciliation.
- Added scheduled report delivery with SFTP, S3/MinIO and signed email-link channels.
- Added Phase II API documentation, runbooks, curl smoke tests, static verifier and CI gate.

## Safety contract

- All new runtime modules are disabled by default.
- No destructive DDL is introduced.
- SHA-256 columns use `VARCHAR(64)`.
- Existing Transfer/QR/Bill behavior is preserved unless `PHASE_II_PUSH_ORCHESTRATOR_ENABLED=true`.
- Cross-border HTTP requires HTTPS except loopback tests.
- UPI outward flow is explicitly disabled pending accreditation.
- SFTP delivery uses strict host-key checking and atomic rename.
- Report artifacts are idempotent by report type, recipient and generation key.

## Production prerequisite

The uploaded baseline still has a migration gap: it contains V1 through V82 and Phase II adds V85 through V90. The Phase II plan says V84 is the predecessor. Development verifier warns; Production certification must merge V83 and V84 or formally reconcile the baseline before enabling Phase II migrations in production.
