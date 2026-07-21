# API Endpoint Evidence Matrix

Generated: 2026-07-21T01:35:00Z
Last updated: 2026-07-21T02:53:00Z
Inventory source: `/Users/macbookpro/Desktop/Switching/API_ENDPOINTS.txt`
Total endpoints: 241

## Test Rules

- Every endpoint needs an authenticated expected-result record or an explicit N/A decision.
- Every mutating endpoint needs a validation/negative record and post-test cleanup where applicable.
- High-risk mutations also require authorization, audit, and rollback/cleanup evidence.
- Never store passwords, JWTs, refresh tokens, private keys, or break-glass tokens in evidence.

## Summary

- Methods: `DELETE` 6, `GET` 115, `PATCH` 6, `POST` 110, `PUT` 4
- Risk: `high` 50, `read` 115, `standard` 76
- Domains: 33
- Verified on UAT: 209/241

## 🔴 CRITICAL — do not ship until fixed

- `POST /v1/settlement/rtgs-callback` — IP whitelist provides no real protection in the current
  deployment (trusts client-supplied `X-Forwarded-For` unconditionally, and even without spoofing
  every external request already appears as `127.0.0.1` through the nginx edge). Any participant
  holding a valid mTLS client cert can forge RTGS settlement confirmations for any
  `instructionRef`. See `18-api-endpoint-coverage/64-rtgs-callback-ip-whitelist-bypass/FINDING.md`.

## RTP module went live mid-session (2026-07-21, between 01:43Z and 03:01Z)

`PHASE_II_RTP_ENABLED` was deployed during this testing session — RTP endpoints that returned 404
route-not-found at 01:43Z (evidence 61) returned real business responses by 03:01Z. All 6 RTP
endpoints retested: create/get/cancel/decline confirmed full PASS; authorise/settlements reach
real business validation but are not fully happy-path-tested (see bug below and
`69-rtp-happy-path-20260721T030327Z`). Also found: `AuthoriseRtpRequest.inquiryRef` is optional in
the DTO (`@Size` only) but the service unconditionally requires it
(`RtpAuthorisationService.java:89`, generic "Required value is blank" error that doesn't name the
field) — see `69-rtp-happy-path-20260721T030327Z/BUG-inquiryRef-contract-mismatch.md`.

## Known deploy gap — 6 endpoints permanently 404 until fixed

`docker-compose.yml`'s `app` service still does not forward `PHASE_II_REPORT_DELIVERY_ENABLED` or
`PHASE_II_CROSS_BORDER_ENABLED` (+ per-rail secrets) into the container, so those
`@ConditionalOnProperty` controllers never register regardless of `.env` contents: all 3
`v1-operator` report-delivery-schedule endpoints, `GET /v1/reports/download/{id}`,
`POST /v1/crossborder/inbound/{rail}`, and `POST /v1/operator/crossborder/reconciliation/{rail}/{statementDate}`.
Note `POST /v1/crossborder/initiate` and `/v1/crossborder/quote` are NOT behind this gate
(`CrossBorderController` has no `@ConditionalOnProperty`, unlike `CrossBorderInboundController`)
and are already reachable — confirmed via `68-blocked-endpoints-recheck-20260721T030120Z`.

## Blocked by test-data availability this session (not a code defect)

- Settlement-cycle instruction mutations (`batch`, `close`, `settle`, `instructions/{ref}/approve`,
  `/reject`, `/send-rtgs`, `/record-rtgs-upload`) — today's 4-cycles/day quota was already
  exhausted by the scheduler and no cycle had any batched items. See
  `65-settlement-cycle-chain/NOTE.md`.
- `POST /api/admin/requests/{id}/approve` — only one distinct ADMIN account was available this
  session; the endpoint requires maker != checker, so only the negative self-approval case was
  exercised. See `67-maker-checker-approve/NOTE.md`.
- `POST /v1/rtp/requests/{id}/authorise` + `/settlements` — reach real business validation but need
  a real prior transfer-inquiry record (ISO20022 inquiry flow) to complete the happy path. See
  `69-rtp-happy-path-20260721T030327Z`.
- BANK-role endpoints (`/v1/settlement/liquidity/topup`, `/v1/qr/pay`, `/v1/qr/refund`,
  `/v1/webhooks*`) — no BANK-role SMOS account was available this session.
- Dead-letter mutations (`request-replay`, `approve-replay`, `execute-replay`, `discard`) —
  `GET /v1/operations/dead-letters` returned `[]`, no fixture data existed to act on.

## admin (13)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/admin/api-keys` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/01-api-key-lifecycle` |
| [x] | POST | `/api/admin/api-keys` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/01-api-key-lifecycle` |
| [x] | POST | `/api/admin/api-keys/{id}/disable` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/01-api-key-lifecycle` |
| [x] | POST | `/api/admin/api-keys/{id}/rotate` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/01-api-key-lifecycle` |
| [x] | GET | `/api/admin/requests` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/02-maker-checker` |
| [x] | POST | `/api/admin/requests` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/02-maker-checker` |
| [ ] | POST | `/api/admin/requests/{id}/approve` | standard | happy path + authorization + validation + cleanup | 18-api-endpoint-coverage/67-maker-checker-approve — negative (self-approval) only, needs 2nd admin account for happy path |
| [x] | POST | `/api/admin/requests/{id}/reject` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/02-maker-checker` |
| [x] | GET | `/api/admin/users` | read | authenticated read + unauthorized negative | `02-auth-security` |
| [x] | POST | `/api/admin/users` | standard | happy path + authorization + validation + cleanup | `17-security-operational` |
| [x] | GET | `/api/admin/users/{id}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/03-user-lifecycle` |
| [x] | PUT | `/api/admin/users/{id}/roles` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/03-user-lifecycle` |
| [x] | PUT | `/api/admin/users/{id}/status` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/03-user-lifecycle` |

## audit-logs (1)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/audit-logs` | read | authenticated read + unauthorized negative | `17-security-operational/audit-integrity` |

## auth (7)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/api/auth/login` | high | happy path + authorization + validation + audit + rollback/cleanup | `02-auth-security` |
| [x] | POST | `/api/auth/logout` | high | happy path + authorization + validation + audit + rollback/cleanup | `17-security-operational` |
| [x] | POST | `/api/auth/mfa` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/41-mfa` |
| [x] | POST | `/api/auth/refresh` | high | happy path + authorization + validation + audit + rollback/cleanup | `17-security-operational` |
| [x] | DELETE | `/api/auth/sessions` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/54-revoke-all-sessions` |
| [x] | GET | `/api/auth/sessions` | read | authenticated read + unauthorized negative | `17-security-operational` |
| [x] | DELETE | `/api/auth/sessions/{id}` | high | happy path + authorization + validation + audit + rollback/cleanup | `17-security-operational` |

## connector-configs (4)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/connector-configs` | read | authenticated read + unauthorized negative | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/api/connector-configs` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/05-connector-config-20260717T013817Z` |
| [x] | GET | `/api/connector-configs/{connectorName}` | read | authenticated read + unauthorized negative | `15-member-bank-connectivity-guardrails` |
| [x] | PATCH | `/api/connector-configs/{connectorName}` | standard | happy path + authorization + validation + cleanup | `15-member-bank-connectivity-guardrails` |

## dashboard (8)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/dashboard/cross-border` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/dr` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/infrastructure` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/overview` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/participants` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/risk` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/settlement` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |
| [x] | GET | `/api/dashboard/transactions/summary` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/09-dashboard-reads-20260717T015532Z` |

## inquiries (4)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/inquiries` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/10-inquiry-monitor-20260717T022358Z` |
| [x] | POST | `/api/inquiries` | standard | happy path + authorization + validation + cleanup | `03-payment-happy-path` |
| [x] | GET | `/api/inquiries/{inquiryRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/10-inquiry-monitor-20260717T022358Z` |
| [x] | GET | `/api/inquiries/{inquiryRef}/transfers` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/10-inquiry-monitor-20260717T022358Z` |

## iso-inquiries (1)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/iso-inquiries/{inquiryRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/15-iso-inquiries-20260717T025711Z` |

## iso-messages (6)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/iso-messages` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/16-iso-messages-20260717T030142Z` |
| [x] | POST | `/api/iso-messages/{id}/decrypt` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/operations-iso-messages` |
| [x] | POST | `/api/iso-messages/{id}/encrypt` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/operations-iso-messages` |
| [x] | GET | `/api/iso-messages/{messageKey}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/16-iso-messages-20260717T030142Z` |
| [x] | GET | `/api/iso-messages/{messageKey}/security-policy` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/16-iso-messages-20260717T030142Z` |
| [x] | POST | `/api/iso-messages/{messageKey}/validate` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/operations-iso-messages` |

## iso20022 (3)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/api/iso20022/acmt023` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/46-iso-acmt023` |
| [x] | POST | `/api/iso20022/application/*+xml` | standard | happy path + authorization + validation + cleanup | 18-api-endpoint-coverage/50-iso-pacs008-media-type (same route as pacs008, consumes variant) |
| [x] | POST | `/api/iso20022/pacs008` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/45-iso-pacs008` |

## operations (87)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/api/operations/aggregation/run` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/47-aggregation` |
| [x] | POST | `/api/operations/aggregation/run/{date}` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/47-aggregation` |
| [x] | GET | `/api/operations/audit-logs` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/21-operations-audit-logs-20260717T033222Z` |
| [x] | POST | `/api/operations/bank-onboarding` | standard | happy path + authorization + validation + cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/api/operations/bank-onboarding/generate-routes` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/49-generate-routes` |
| [x] | GET | `/api/operations/bank-status` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/17-bank-status-20260717T030530Z` |
| [x] | GET | `/api/operations/bau/status` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/18-bau-status-20260717T030801Z` |
| [x] | GET | `/api/operations/connectors/health` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/17-bank-status-20260717T030530Z` |
| [x] | POST | `/api/operations/connectors/{connectorName}/test` | standard | happy path + authorization + validation + cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | GET | `/api/operations/continuous-assurance/hypercare` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/19-hypercare-20260717T032317Z` |
| [x] | POST | `/api/operations/continuous-assurance/hypercare/complete` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/56-hypercare-flow` |
| [x] | POST | `/api/operations/continuous-assurance/hypercare/events` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/56-hypercare-flow` |
| [x] | POST | `/api/operations/continuous-assurance/hypercare/start` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/56-hypercare-flow` |
| [x] | POST | `/api/operations/continuous-assurance/scorecard` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/55-continuous-assurance-scorecard` |
| [x] | GET | `/api/operations/dashboard-summary` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/20-operations-dashboard-summary-20260717T033103Z` |
| [x] | GET | `/api/operations/disputes` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | GET | `/api/operations/disputes/dashboard-summary` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/dispute-dashboard` |
| [x] | POST | `/api/operations/disputes/post-settlement` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z` |
| [x] | GET | `/api/operations/disputes/{disputeId}` | read | authenticated read + unauthorized negative | `05-refund-reversal` |
| [x] | GET | `/api/operations/disputes/{disputeId}/actions` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | POST | `/api/operations/disputes/{disputeId}/approve-resolution` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z` |
| [x] | GET | `/api/operations/disputes/{disputeId}/attachments` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | POST | `/api/operations/disputes/{disputeId}/attachments` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | GET | `/api/operations/disputes/{disputeId}/attachments/{attachmentId}/download` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | GET | `/api/operations/disputes/{disputeId}/evidence-report` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | GET | `/api/operations/disputes/{disputeId}/evidence-report.csv` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | POST | `/api/operations/disputes/{disputeId}/refund/retry` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z` |
| [x] | POST | `/api/operations/disputes/{disputeId}/reject-resolution` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | POST | `/api/operations/disputes/{disputeId}/submit-resolution` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | GET | `/api/operations/disputes/{disputeId}/timeline` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/30-dispute-operations-20260717T061831Z` |
| [x] | GET | `/api/operations/health` | read | authenticated read + unauthorized negative | `10-observability` |
| [x] | GET | `/api/operations/iso-inquiries` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/15-iso-inquiries-20260717T025711Z` |
| [x] | GET | `/api/operations/iso-inquiries/{inquiryRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/15-iso-inquiries-20260717T025711Z` |
| [x] | GET | `/api/operations/iso-messages` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/operations-iso-messages` |
| [x] | POST | `/api/operations/outbox-events/{id}/mark-reviewed` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/63-outbox-retry-mark-reviewed |
| [x] | GET | `/api/operations/outbox-failures` | read | authenticated read + unauthorized negative | `08-outbox-recovery` |
| [x] | POST | `/api/operations/outbox-failures/retry-all` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/63-outbox-retry-mark-reviewed |
| [x] | GET | `/api/operations/outbox-stuck` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/22-outbox-stuck-20260717T033305Z` |
| [x] | POST | `/api/operations/outbox-stuck/recover-all` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/57-outbox-stuck-recovery` |
| [x] | GET | `/api/operations/payment-flows/{transactionRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/14-operations-events-20260717T024646Z` |
| [x] | GET | `/api/operations/promotions/funder-ledger/reconciliation` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/38-promotion-funder-ledger` |
| [x] | GET | `/api/operations/readiness/approvals` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | POST | `/api/operations/readiness/approvals` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | GET | `/api/operations/readiness/controls` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | POST | `/api/operations/readiness/controls` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | POST | `/api/operations/readiness/decisions` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | GET | `/api/operations/readiness/evidence` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | POST | `/api/operations/readiness/evidence` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | GET | `/api/operations/readiness/evidence/integrity` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | GET | `/api/operations/readiness/incidents` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | POST | `/api/operations/readiness/incidents` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | GET | `/api/operations/readiness/risks` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | POST | `/api/operations/readiness/risks` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z/readiness` |
| [x] | GET | `/api/operations/reconciliation/files` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | POST | `/api/operations/reconciliation/files` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | GET | `/api/operations/reconciliation/files/{fileRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | GET | `/api/operations/reconciliation/files/{fileRef}/discrepancies` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | GET | `/api/operations/reconciliation/files/{fileRef}/items` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | POST | `/api/operations/reconciliation/files/{fileRef}/items` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | POST | `/api/operations/reconciliation/files/{fileRef}/rematch` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/39-reconciliation-files` |
| [x] | GET | `/api/operations/settlement/cycles` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [ ] | POST | `/api/operations/settlement/cycles` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/65-settlement-cycle-chain — validation only (quota), happy path blocked by daily-cycle cap + no batchable data |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/actions` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [ ] | POST | `/api/operations/settlement/cycles/{cycleRef}/batch` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [ ] | POST | `/api/operations/settlement/cycles/{cycleRef}/close` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/detail` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/instructions` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [ ] | POST | `/api/operations/settlement/cycles/{cycleRef}/instructions/generate` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/65-settlement-cycle-chain — 200 empty list only, no positions to net |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/ops-report` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/ops-report.csv` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations` |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/report` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations` |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/reports` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [ ] | POST | `/api/operations/settlement/cycles/{cycleRef}/settle` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [x] | GET | `/api/operations/settlement/cycles/{cycleRef}/timeline` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [x] | GET | `/api/operations/settlement/instructions/{instructionRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [ ] | POST | `/api/operations/settlement/instructions/{instructionRef}/approve` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [ ] | POST | `/api/operations/settlement/instructions/{instructionRef}/record-rtgs-upload` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [ ] | POST | `/api/operations/settlement/instructions/{instructionRef}/reject` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [x] | GET | `/api/operations/settlement/instructions/{instructionRef}/rtgs-file` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/29-settlement-operations-20260717T042342Z` |
| [ ] | POST | `/api/operations/settlement/instructions/{instructionRef}/send-rtgs` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [x] | GET | `/api/operations/transaction-events` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/14-operations-events-20260717T024646Z` |
| [x] | GET | `/api/operations/transaction-events/{transactionRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/14-operations-events-20260717T024646Z` |
| [x] | GET | `/api/operations/transactions` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/12-operations-transfer-query-20260717T023544Z` |
| [x] | GET | `/api/operations/transfers` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/12-operations-transfer-query-20260717T023544Z` |
| [x] | GET | `/api/operations/transfers/{transferRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/12-operations-transfer-query-20260717T023544Z` |
| [x] | GET | `/api/operations/transfers/{transferRef}/trace` | read | authenticated read + unauthorized negative | `04-payment-failure-retry-auto` |

## outbox-events (2)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/outbox-events` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/23-outbox-events-20260717T033431Z` |
| [x] | POST | `/api/outbox-events/{outboxEventId}/retry` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/63-outbox-retry-mark-reviewed |

## participants (4)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/participants` | read | authenticated read + unauthorized negative | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/api/participants` | standard | happy path + authorization + validation + cleanup | `17-security-operational/certificate-lifecycle` |
| [x] | GET | `/api/participants/{bankCode}` | read | authenticated read + unauthorized negative | `15-member-bank-connectivity-guardrails` |
| [x] | PATCH | `/api/participants/{bankCode}` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/06-participant-update-20260717T014144Z` |

## routing-rules (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/routing-rules` | read | authenticated read + unauthorized negative | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/api/routing-rules` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/04-routing-rules-20260717T013259Z` |
| [x] | POST | `/api/routing-rules/cache/clear` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/04-routing-rules-20260717T013259Z` |
| [x] | GET | `/api/routing-rules/resolve` | read | authenticated read + unauthorized negative | `15-member-bank-connectivity-guardrails` |
| [x] | PATCH | `/api/routing-rules/{routeCode}` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/04-routing-rules-20260717T013259Z` |

## transfers (4)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/api/transfers` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/11-transfer-monitor-20260717T022743Z` |
| [x] | POST | `/api/transfers` | high | happy path + authorization + validation + audit + rollback/cleanup | `04-payment-failure-retry-auto` |
| [x] | GET | `/api/transfers/{transferRef}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/11-transfer-monitor-20260717T022743Z` |
| [x] | GET | `/api/transfers/{transferRef}/trace` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/11-transfer-monitor-20260717T022743Z` |

## v1-billers (2)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/billers` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/32-billers-20260717T064404Z` |
| [x] | GET | `/v1/billers/{billerId}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/32-billers-20260717T064404Z` |

## v1-bills (2)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/bills/fetch` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/32-billers-20260717T064404Z` |
| [x] | POST | `/v1/bills/pay` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/32-billers-20260717T064404Z` |

## v1-compliance (3)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/compliance/sanctions/check` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/33-compliance-20260717T064657Z` |
| [x] | GET | `/v1/compliance/str/{strId}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/33-compliance-20260717T064657Z` |
| [x] | GET | `/v1/compliance/velocity/{pspId}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/33-compliance-20260717T064657Z` |

## v1-crossborder (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/crossborder/corridors` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/27-crossborder-reads-20260717T035247Z` |
| [x] | GET | `/v1/crossborder/fx-rates` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/27-crossborder-reads-20260717T035247Z` |
| [ ] | POST | `/v1/crossborder/inbound/{rail}` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | POST | `/v1/crossborder/initiate` | standard | happy path + authorization + validation + cleanup | - |
| [x] | POST | `/v1/crossborder/quote` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/42-crossborder-quote` |

## v1-disputes (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/disputes` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/public-disputes` |
| [x] | POST | `/v1/disputes/raise` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/public-disputes` |
| [x] | GET | `/v1/disputes/{disputeId}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/public-disputes` |
| [x] | POST | `/v1/disputes/{disputeId}/resolve` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/public-disputes` |
| [x] | PUT | `/v1/disputes/{disputeId}/respond` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/31-post-settlement-dispute-20260717T062635Z/public-disputes` |

## v1-fpre (1)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/fpre/health` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/13-fpre-observability-20260717T024042Z` |

## v1-lookup (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/v1/lookup/resolve` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/36-vpa-lookup` |
| [x] | POST | `/v1/lookup/vpa/register` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/36-vpa-lookup` |
| [x] | DELETE | `/v1/lookup/vpa/{vpaId}` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/36-vpa-lookup` |
| [x] | GET | `/v1/lookup/vpa/{vpaId}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/36-vpa-lookup` |
| [x] | PUT | `/v1/lookup/vpa/{vpaId}` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/36-vpa-lookup` |

## v1-oauth (2)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/v1/oauth/token` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/35-oauth` |
| [x] | POST | `/v1/oauth/token/revoke` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/35-oauth` |

## v1-operations (21)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/operations/break-glass` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/33-compliance-20260717T064657Z/participant-certifications/config-changes/break-glass` |
| [x] | POST | `/v1/operations/break-glass` | high | happy path + authorization + validation + audit + rollback/cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/v1/operations/break-glass/{id}/approve` | high | happy path + authorization + validation + audit + rollback/cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/v1/operations/break-glass/{id}/revoke` | high | happy path + authorization + validation + audit + rollback/cleanup | `17-security-operational` |
| [x] | GET | `/v1/operations/config-changes` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/33-compliance-20260717T064657Z/participant-certifications/config-changes` |
| [x] | POST | `/v1/operations/config-changes` | high | happy path + authorization + validation + audit + rollback/cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/v1/operations/config-changes/{id}/approve` | high | happy path + authorization + validation + audit + rollback/cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/v1/operations/config-changes/{id}/execute` | high | happy path + authorization + validation + audit + rollback/cleanup | `15-member-bank-connectivity-guardrails` |
| [x] | POST | `/v1/operations/config-changes/{id}/reject` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/53-config-change-reject` |
| [x] | GET | `/v1/operations/dead-letters` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/24-dead-letters-20260717T033528Z` |
| [ ] | POST | `/v1/operations/dead-letters/{id}/approve-replay` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | POST | `/v1/operations/dead-letters/{id}/discard` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | POST | `/v1/operations/dead-letters/{id}/execute-replay` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | POST | `/v1/operations/dead-letters/{id}/request-replay` | standard | happy path + authorization + validation + cleanup | - |
| [x] | GET | `/v1/operations/legal-holds` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/25-legal-holds-20260717T033808Z` |
| [x] | POST | `/v1/operations/legal-holds` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/58-legal-hold-guardrail` |
| [x] | POST | `/v1/operations/legal-holds/{id}/approve` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/58-legal-hold-guardrail` |
| [x] | POST | `/v1/operations/legal-holds/{id}/approve-release` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/58-legal-hold-guardrail` |
| [x] | POST | `/v1/operations/legal-holds/{id}/request-release` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/58-legal-hold-guardrail` |
| [x] | GET | `/v1/operations/participant-certifications` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/33-compliance-20260717T064657Z/participant-certifications` |
| [x] | POST | `/v1/operations/participant-certifications` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/51-participant-certification` |

## v1-operator (6)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [ ] | POST | `/v1/operator/crossborder/reconciliation/{rail}/{statementDate}` | standard | happy path + authorization + validation + cleanup | - |
| [x] | POST | `/v1/operator/push-payment-policies` | standard | happy path + authorization + validation + cleanup | `12-admin-portal-integration` |
| [x] | POST | `/v1/operator/push-payment-policies/{id}/activate` | standard | happy path + authorization + validation + cleanup | `12-admin-portal-integration` |
| [ ] | GET | `/v1/operator/report-delivery-schedules` | read | authenticated read + unauthorized negative | 18-api-endpoint-coverage/60-report-delivery-schedules-20260721T014323Z — BLOCKED, 404 route not found |
| [ ] | POST | `/v1/operator/report-delivery-schedules` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | PATCH | `/v1/operator/report-delivery-schedules/{id}/suspend` | standard | happy path + authorization + validation + cleanup | - |

## v1-participants (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/v1/participants/{pspId}/certificates/register` | high | happy path + authorization + validation + audit + rollback/cleanup | `17-security-operational/certificate-lifecycle` |
| [x] | POST | `/v1/participants/{pspId}/certificates/issue` | high | happy path + authorization + validation + audit + rollback/cleanup | `18-api-endpoint-coverage/07-certificate-issue-20260717T014548Z` |
| [x] | GET | `/v1/participants/certificates` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/07-certificate-issue-20260717T014548Z` |
| [x] | DELETE | `/v1/participants/{pspId}/certificates/{certId}` | high | happy path + authorization + validation + audit + rollback/cleanup | `17-security-operational/certificate-lifecycle` |
| [x] | POST | `/v1/participants/{pspId}/credentials/rotate` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/66-credentials-rotate |

## v1-promotions (7)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/v1/promotions` | standard | happy path + authorization + validation + cleanup | `promotion-scenarios` |
| [x] | GET | `/v1/promotions` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/08-promotion-lifecycle-20260717T014945Z` |
| [x] | POST | `/v1/promotions/{id}/activate` | standard | happy path + authorization + validation + cleanup | `promotion-scenarios` |
| [x] | PATCH | `/v1/promotions/{id}/extend` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/08-promotion-lifecycle-20260717T014945Z` |
| [x] | GET | `/v1/promotions/{id}/report` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/08-promotion-lifecycle-20260717T014945Z` |
| [x] | PATCH | `/v1/promotions/{id}/suspend` | standard | happy path + authorization + validation + cleanup | `promotion-scenarios` |
| [x] | DELETE | `/v1/promotions/{id}` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/08-promotion-lifecycle-20260717T014945Z` |

## v1-qr (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/v1/qr/decode` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/37-qr` |
| [x] | POST | `/v1/qr/generate/dynamic` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/37-qr` |
| [x] | POST | `/v1/qr/generate/static` | standard | happy path + authorization + validation + cleanup | `18-api-endpoint-coverage/37-qr` |
| [ ] | POST | `/v1/qr/pay` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | POST | `/v1/qr/refund` | standard | happy path + authorization + validation + cleanup | - |

## v1-reports (1)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [ ] | GET | `/v1/reports/download/{id}` | read | authenticated read + unauthorized negative | 18-api-endpoint-coverage/62-reports-download — BLOCKED, same PHASE_II_REPORT_DELIVERY_ENABLED deploy gap as RTP |

## v1-risk (1)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/risk/scores/{txnId}` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/44-risk-score` |

## v1-rtp (6)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | POST | `/v1/rtp/requests` | standard | happy path + authorization + validation + cleanup | 18-api-endpoint-coverage/69-rtp-happy-path-20260721T030327Z |
| [x] | GET | `/v1/rtp/requests/{id}` | read | authenticated read + unauthorized negative | 18-api-endpoint-coverage/69-rtp-happy-path-20260721T030327Z |
| [ ] | POST | `/v1/rtp/requests/{id}/authorise` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/69-rtp-happy-path-20260721T030327Z -- validation confirmed live + found DTO/service inquiryRef contract bug; full happy path needs a real prior inquiry record (out of scope this pass) |
| [x] | POST | `/v1/rtp/requests/{id}/cancel` | standard | happy path + authorization + validation + cleanup | 18-api-endpoint-coverage/69-rtp-happy-path-20260721T030327Z |
| [x] | POST | `/v1/rtp/requests/{id}/decline` | standard | happy path + authorization + validation + cleanup | 18-api-endpoint-coverage/69-rtp-happy-path-20260721T030327Z |
| [ ] | POST | `/v1/rtp/requests/{id}/settlements` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/69-rtp-happy-path-20260721T030327Z -- guard validated (rejects over-authorised amount); blocked on authorise happy path above |

## v1-settlement (5)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/settlement/balance` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/28-settlement-reads-20260717T035448Z` |
| [ ] | POST | `/v1/settlement/liquidity/topup` | high | happy path + authorization + validation + audit + rollback/cleanup | - |
| [x] | GET | `/v1/settlement/pool-history` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/28-settlement-reads-20260717T035448Z` |
| [x] | GET | `/v1/settlement/positions` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/28-settlement-reads-20260717T035448Z` |
| [ ] | POST | `/v1/settlement/rtgs-callback` | high | happy path + authorization + validation + audit + rollback/cleanup | 18-api-endpoint-coverage/64-rtgs-callback-ip-whitelist-bypass — CRITICAL FINDING, IP whitelist bypassed, see FINDING.md |

## v1-transfers (4)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/transfers/failed` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/13-fpre-observability-20260717T024042Z` |
| [x] | GET | `/v1/transfers/pending` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/13-fpre-observability-20260717T024042Z` |
| [x] | GET | `/v1/transfers/{txnId}/retry-history` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/13-fpre-observability-20260717T024042Z` |
| [x] | GET | `/v1/transfers/{txnId}/retry-status` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/13-fpre-observability-20260717T024042Z` |

## v1-webhooks (6)

| Done | Method | Path | Risk | Required Evidence | Evidence |
| --- | --- | --- | --- | --- | --- |
| [x] | GET | `/v1/webhooks` | read | authenticated read + unauthorized negative | `18-api-endpoint-coverage/34-webhooks-20260717T065152Z` |
| [ ] | POST | `/v1/webhooks` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | DELETE | `/v1/webhooks/{webhookId}` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | GET | `/v1/webhooks/{webhookId}` | read | authenticated read + unauthorized negative | - |
| [ ] | POST | `/v1/webhooks/{webhookId}/secret/rotate` | standard | happy path + authorization + validation + cleanup | - |
| [ ] | POST | `/v1/webhooks/{webhookId}/test` | standard | happy path + authorization + validation + cleanup | - |
