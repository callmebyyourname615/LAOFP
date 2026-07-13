# Switching UAT Production Readiness Evidence

Bundle: prod-readiness-20260713Tuat01
Target: https://175.11.0.200
Status: READY_FOR_UAT_PORTAL_INTEGRATION
Score: 90/100

## Passed

- Auth/security: mTLS, JWT, invalid token rejection, wrong password rejection, admin authorization.
- Payment happy path: inquiry, transfer, connector confirmation, outbox success, settlement-ready state.
- Payment retry: transient dispatch failure retried and recovered successfully.
- Refund/reversal: post-settlement dispute, maker submission, checker approval, completed refund.
- Settlement: closed DNS cycle processed through RTGS upload, callback confirmation, SETTLED cycle, settled positions, CAMT054 reports.
- Outbox recovery: failed terminal outbox rows reviewed; operations health returned HEALTHY.
- Observability: health, dashboard, outbox failure, dead-letter, Docker state, and application logs were collected.
- Config hardcode check: production/staging profiles externalize critical runtime settings.

## Fix Applied

- DRS SLA scheduler no longer attempts auto-refund for overdue TECHNICAL_ERROR disputes when the related transfer is not settled.
- Old overdue DRS_REQUIRED disputes were escalated for manual review.
- Scheduler verification showed bad SQL count = 0, SLA auto-resolution failed count = 0, and SLA escalated count = 7.

## Remaining Notes

- Local/dev defaults still exist in application.yml, but staging/prod profiles require externalized runtime configuration.
- Final production sign-off should include full regression, operational runbook review, and production secret/profile validation.

## Decision

The Switching API is ready for continued UAT and Admin/Bank/DRS portal integration.
