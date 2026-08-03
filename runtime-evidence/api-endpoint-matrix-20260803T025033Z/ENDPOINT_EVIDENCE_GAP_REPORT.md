# Endpoint Evidence Gap Report

Total endpoints: 250
PASS evidence registered: 200
Missing / not tested: 50
Coverage: 80.0%

## Missing By Domain

- `operations`: 11
- `v1-rtp`: 6
- `v1-operator`: 5
- `v1-webhooks`: 5
- `v1-operations`: 4
- `risk`: 4
- `admin`: 2
- `v1-crossborder`: 2
- `v1-qr`: 2
- `v1-settlement`: 2
- `fees`: 2
- `iso20022`: 1
- `outbox-events`: 1
- `v1-participants`: 1
- `v1-reports`: 1
- `v1-aml`: 1

## Missing By Risk

- `standard`: 22
- `high`: 17
- `read`: 11

## Missing Endpoints

- `POST /api/admin/requests/{id}/approve` (admin, standard): happy path + authorization + validation + cleanup
- `POST /api/iso20022/application/*+xml` (iso20022, standard): happy path + authorization + validation + cleanup
- `POST /api/operations/outbox-events/{id}/mark-reviewed` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/outbox-failures/retry-all` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/cycles` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/cycles/{cycleRef}/batch` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/cycles/{cycleRef}/close` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/cycles/{cycleRef}/instructions/generate` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/cycles/{cycleRef}/settle` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/instructions/{instructionRef}/approve` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/instructions/{instructionRef}/record-rtgs-upload` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/instructions/{instructionRef}/reject` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/operations/settlement/instructions/{instructionRef}/send-rtgs` (operations, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /api/outbox-events/{outboxEventId}/retry` (outbox-events, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /v1/crossborder/inbound/{rail}` (v1-crossborder, standard): happy path + authorization + validation + cleanup
- `POST /v1/crossborder/initiate` (v1-crossborder, standard): happy path + authorization + validation + cleanup
- `POST /v1/operations/dead-letters/{id}/approve-replay` (v1-operations, standard): happy path + authorization + validation + cleanup
- `POST /v1/operations/dead-letters/{id}/discard` (v1-operations, standard): happy path + authorization + validation + cleanup
- `POST /v1/operations/dead-letters/{id}/execute-replay` (v1-operations, standard): happy path + authorization + validation + cleanup
- `POST /v1/operations/dead-letters/{id}/request-replay` (v1-operations, standard): happy path + authorization + validation + cleanup
- `POST /v1/operator/crossborder/reconciliation/{rail}/{statementDate}` (v1-operator, standard): happy path + authorization + validation + cleanup
- `GET /v1/operator/report-delivery-schedules` (v1-operator, read): authenticated read + unauthorized negative
- `GET /v1/operator/report-delivery-history` (v1-operator, read): authenticated read + unauthorized negative
- `POST /v1/operator/report-delivery-schedules` (v1-operator, standard): happy path + authorization + validation + cleanup
- `PATCH /v1/operator/report-delivery-schedules/{id}/suspend` (v1-operator, standard): happy path + authorization + validation + cleanup
- `POST /v1/participants/{pspId}/credentials/rotate` (v1-participants, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /v1/qr/pay` (v1-qr, standard): happy path + authorization + validation + cleanup
- `POST /v1/qr/refund` (v1-qr, standard): happy path + authorization + validation + cleanup
- `GET /v1/reports/download/{id}` (v1-reports, read): authenticated read + unauthorized negative
- `GET /v1/aml/reports` (v1-aml, read): authenticated read + unauthorized negative
- `POST /v1/rtp/requests` (v1-rtp, standard): happy path + authorization + validation + cleanup
- `GET /v1/rtp/requests/{id}` (v1-rtp, read): authenticated read + unauthorized negative
- `POST /v1/rtp/requests/{id}/authorise` (v1-rtp, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /v1/rtp/requests/{id}/cancel` (v1-rtp, standard): happy path + authorization + validation + cleanup
- `POST /v1/rtp/requests/{id}/decline` (v1-rtp, standard): happy path + authorization + validation + cleanup
- `POST /v1/rtp/requests/{id}/settlements` (v1-rtp, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /v1/settlement/liquidity/topup` (v1-settlement, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /v1/settlement/rtgs-callback` (v1-settlement, high): happy path + authorization + validation + audit + rollback/cleanup
- `POST /v1/webhooks` (v1-webhooks, standard): happy path + authorization + validation + cleanup
- `DELETE /v1/webhooks/{webhookId}` (v1-webhooks, standard): happy path + authorization + validation + cleanup
- `GET /v1/webhooks/{webhookId}` (v1-webhooks, read): authenticated read + unauthorized negative
- `POST /v1/webhooks/{webhookId}/secret/rotate` (v1-webhooks, standard): happy path + authorization + validation + cleanup
- `POST /v1/webhooks/{webhookId}/test` (v1-webhooks, standard): happy path + authorization + validation + cleanup
- `GET /api/risk/rules` (risk, read): authenticated read + unauthorized negative
- `POST /api/risk/rules/{id}/enable` (risk, standard): happy path + authorization + validation + cleanup
- `POST /api/risk/rules/{id}/disable` (risk, standard): happy path + authorization + validation + cleanup
- `GET /api/risk/scores/distribution` (risk, read): authenticated read + unauthorized negative
- `GET /api/admin/security-events` (admin, read): authenticated read + unauthorized negative
- `GET /api/fees/decisions` (fees, read): authenticated read + unauthorized negative
- `GET /api/fees/exceptions` (fees, read): authenticated read + unauthorized negative

## Registry Evidence Paths Not Found On Disk

- `GET /api/admin/api-keys` -> `18-api-endpoint-coverage/01-api-key-lifecycle`
- `POST /api/admin/api-keys` -> `18-api-endpoint-coverage/01-api-key-lifecycle`
- `POST /api/admin/api-keys/{id}/disable` -> `18-api-endpoint-coverage/01-api-key-lifecycle`
- `POST /api/admin/api-keys/{id}/rotate` -> `18-api-endpoint-coverage/01-api-key-lifecycle`
- `GET /api/admin/requests` -> `18-api-endpoint-coverage/02-maker-checker`
- `POST /api/admin/requests` -> `18-api-endpoint-coverage/02-maker-checker`
- `POST /api/admin/requests/{id}/reject` -> `18-api-endpoint-coverage/02-maker-checker`
- `GET /api/admin/users/{id}` -> `18-api-endpoint-coverage/03-user-lifecycle`
- `PUT /api/admin/users/{id}/roles` -> `18-api-endpoint-coverage/03-user-lifecycle`
- `PUT /api/admin/users/{id}/status` -> `18-api-endpoint-coverage/03-user-lifecycle`
- `GET /api/audit-logs` -> `17-security-operational/audit-integrity`
- `POST /api/auth/mfa` -> `18-api-endpoint-coverage/41-mfa`
- `DELETE /api/auth/sessions` -> `18-api-endpoint-coverage/54-revoke-all-sessions`
- `POST /api/iso20022/acmt023` -> `18-api-endpoint-coverage/46-iso-acmt023`
- `POST /api/iso20022/pacs008` -> `18-api-endpoint-coverage/45-iso-pacs008`
- `POST /api/operations/aggregation/run` -> `18-api-endpoint-coverage/47-aggregation`
- `POST /api/operations/aggregation/run/{date}` -> `18-api-endpoint-coverage/47-aggregation`
- `POST /api/operations/bank-onboarding/generate-routes` -> `18-api-endpoint-coverage/49-generate-routes`
- `POST /api/operations/continuous-assurance/hypercare/complete` -> `18-api-endpoint-coverage/56-hypercare-flow`
- `POST /api/operations/continuous-assurance/hypercare/events` -> `18-api-endpoint-coverage/56-hypercare-flow`
- `POST /api/operations/continuous-assurance/hypercare/start` -> `18-api-endpoint-coverage/56-hypercare-flow`
- `POST /api/operations/continuous-assurance/scorecard` -> `18-api-endpoint-coverage/55-continuous-assurance-scorecard`
- `POST /api/operations/outbox-stuck/recover-all` -> `18-api-endpoint-coverage/57-outbox-stuck-recovery`
- `GET /api/operations/promotions/funder-ledger/reconciliation` -> `18-api-endpoint-coverage/38-promotion-funder-ledger`
- `GET /api/operations/reconciliation/files` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `POST /api/operations/reconciliation/files` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `GET /api/operations/reconciliation/files/{fileRef}` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `GET /api/operations/reconciliation/files/{fileRef}/discrepancies` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `GET /api/operations/reconciliation/files/{fileRef}/items` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `POST /api/operations/reconciliation/files/{fileRef}/items` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `POST /api/operations/reconciliation/files/{fileRef}/rematch` -> `18-api-endpoint-coverage/39-reconciliation-files`
- `GET /api/operations/settlement/cycles/{cycleRef}/ops-report.csv` -> `18-api-endpoint-coverage/29-settlement-operations`
- `GET /api/operations/settlement/cycles/{cycleRef}/report` -> `18-api-endpoint-coverage/29-settlement-operations`
- `GET /api/operations/transfers/{transferRef}/trace` -> `04-payment-failure-retry-auto`
- `POST /api/participants` -> `17-security-operational/certificate-lifecycle`
- `POST /api/transfers` -> `04-payment-failure-retry-auto`
- `POST /v1/crossborder/quote` -> `18-api-endpoint-coverage/42-crossborder-quote`
- `POST /v1/lookup/resolve` -> `18-api-endpoint-coverage/36-vpa-lookup`
- `POST /v1/lookup/vpa/register` -> `18-api-endpoint-coverage/36-vpa-lookup`
- `DELETE /v1/lookup/vpa/{vpaId}` -> `18-api-endpoint-coverage/36-vpa-lookup`
- `GET /v1/lookup/vpa/{vpaId}` -> `18-api-endpoint-coverage/36-vpa-lookup`
- `PUT /v1/lookup/vpa/{vpaId}` -> `18-api-endpoint-coverage/36-vpa-lookup`
- `POST /v1/oauth/token` -> `18-api-endpoint-coverage/35-oauth`
- `POST /v1/oauth/token/revoke` -> `18-api-endpoint-coverage/35-oauth`
- `POST /v1/operations/config-changes/{id}/reject` -> `18-api-endpoint-coverage/53-config-change-reject`
- `POST /v1/operations/legal-holds` -> `18-api-endpoint-coverage/58-legal-hold-guardrail`
- `POST /v1/operations/legal-holds/{id}/approve` -> `18-api-endpoint-coverage/58-legal-hold-guardrail`
- `POST /v1/operations/legal-holds/{id}/approve-release` -> `18-api-endpoint-coverage/58-legal-hold-guardrail`
- `POST /v1/operations/legal-holds/{id}/request-release` -> `18-api-endpoint-coverage/58-legal-hold-guardrail`
- `POST /v1/operations/participant-certifications` -> `18-api-endpoint-coverage/51-participant-certification`
- `POST /v1/participants/{pspId}/certificates/register` -> `17-security-operational/certificate-lifecycle`
- `DELETE /v1/participants/{pspId}/certificates/{certId}` -> `17-security-operational/certificate-lifecycle`
- `POST /v1/qr/decode` -> `18-api-endpoint-coverage/37-qr`
- `POST /v1/qr/generate/dynamic` -> `18-api-endpoint-coverage/37-qr`
- `POST /v1/qr/generate/static` -> `18-api-endpoint-coverage/37-qr`
- `GET /v1/risk/scores/{txnId}` -> `18-api-endpoint-coverage/44-risk-score`
