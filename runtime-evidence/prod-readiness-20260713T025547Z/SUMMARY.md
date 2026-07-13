# Production Readiness Evidence

Status: NOT_READY_FOR_PRODUCTION

This bundle is the evidence workspace for the production readiness assessment. Each section must contain raw command output/API responses plus a short `RESULT.md` stating PASS/WARN/FAIL and the decision reason.

## Required Sections

| Section | Status | Required Evidence |
| --- | --- | --- |
| 01-deployment-state | NOT_RUN | deployed jar/image checksum, git commit, compose/container state, health/readiness |
| 02-auth-security | NOT_RUN | mTLS required, no-cert rejected, bad cert rejected, JWT required, RBAC denied, audit event |
| 03-payment-happy-path | NOT_RUN | inquiry, transfer, final status, trace, source view, audit, settlement impact |
| 04-payment-failure-retry | NOT_RUN | connector down, pending transfer, retry rounds, status enquiry, final recovery |
| 05-refund-reversal | NOT_RUN | original success, refund/reversal request, approval/reject path, ledger/settlement effect |
| 06-drs-matrix | NOT_RUN | pre/post-settlement dispute, no-action, refund-required, reject, maker/checker controls |
| 07-settlement | NOT_RUN | cycle creation, batch/instructions, approval, close/settle, reconciliation |
| 08-outbox-recovery | NOT_RUN | failed outbox inventory, retry/discard/review, operations health returns UP |
| 09-config-hardcode-check | NOT_RUN | env/config proof for secrets, DB, MinIO, fees, promotions, limits, retry policies |
| 10-observability | NOT_RUN | logs, requestId/traceId, audit logs, metrics/health, diagnostic commands |

## Current Priority

Start with `08-outbox-recovery` because production readiness is blocked while `/api/operations/health` is `DEGRADED` due failed outbox events.
