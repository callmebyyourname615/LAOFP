# Phase II-05 through II-24 Implementation Plan

This plan expands the five Phase II BRD gaps into twenty independently reviewable
increments. All new runtime paths remain disabled by default until certification.

| Phase | Name | Primary deliverable |
|---|---|---|
| II-05 | RTP Full Authorisation | Full payer authorisation delegated to the transfer rail |
| II-06 | RTP Partial and Installments | Partial authorisation and durable installment ledger |
| II-07 | RTP Operations | Expiry, installment execution, events, metrics, API completion |
| II-08 | Promotion Foundation | V86 promotion, rule, application and settlement schema |
| II-09 | Promotion Eligibility | Bounded JSON DSL and deterministic priority |
| II-10 | Promotion Budget Safety | Atomic reservation, release, consumption and cleanup |
| II-11 | Fee Pipeline Integration | Gross/discount/net fee and promotion breakdown |
| II-12 | Promotion Operations | Four-eyes activation, settlement, reports and audit |
| II-13 | Payment Lifecycle Contract | Channel-neutral request/result/lifecycle abstractions |
| II-14 | Push Orchestrator Core | V87 policy store, lifecycle execution and transitions |
| II-15 | Existing Channel Migration | Transfer, QR and bill feature-flagged delegation |
| II-16 | New Channel Integration | RTP and cross-border lifecycle delegation |
| II-17 | Durable Rail Framework | V89 journal, replay protection, auth and mTLS contract |
| II-18 | PromptPay and Bakong | Concrete outbound/inbound adapters |
| II-19 | NAPAS and UPI | NAPAS adapter, UPI inward-only and rail reconciliation |
| II-20 | Report Delivery Foundation | V88 schedules, runs, artifacts and audit evidence |
| II-21 | SFTP Delivery | Key authentication, pinned host key and atomic rename |
| II-22 | S3 and Email Link | Object delivery and 24-hour signed links |
| II-23 | Report Operations | Operator schedules, retries, signed events and download |
| II-24 | Phase II Hardening | V90 extensions, static/CI gates, docs and certification |

## Migration contract

- V85 remains the RTP foundation delivered in Phase II-02.
- V86: Promotion management.
- V87: Push-payment orchestrator.
- V88: Scheduled report delivery.
- V89: Durable cross-border rail journal.
- V90: RTP/orchestrator hardening required by II-05 through II-24.
- Existing migrations are immutable. SHA-256 columns use `VARCHAR(64)`.

## Rollout contract

The following environment flags remain `false` by default:

- `PHASE_II_RTP_ENABLED`
- `PHASE_II_PROMOTION_ENABLED`
- `PHASE_II_PUSH_ORCHESTRATOR_ENABLED`
- `PHASE_II_CROSS_BORDER_ENABLED`
- `PHASE_II_REPORT_DELIVERY_ENABLED`

Enable one module at a time in test/UAT. The orchestrator must not be enabled for
a channel until parity evidence has been accepted.
