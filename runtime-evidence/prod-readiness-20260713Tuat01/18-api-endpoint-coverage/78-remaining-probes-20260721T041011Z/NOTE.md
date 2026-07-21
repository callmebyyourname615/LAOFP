# Route + auth + business-layer confirmation for the remaining probe-only endpoints

For endpoints whose full end-to-end happy path needs real fixture data this UAT
environment cannot produce today (real settlement instructions, real dead-letter queue
entries, real registered webhooks), we submitted well-formed requests with intentionally
nonexistent identifiers and captured the responses. In every case the endpoint routed
correctly, authenticated correctly, and reached its own business-layer id lookup or
service check.

## Results

- Settlement instructions (4 endpoints): 404 SET-003 from `SettlementInstructionService` — full
  route + auth + business layer verified.
- Dead letters (4 endpoints):
  - `request-replay` and `approve-replay` reached `OutboxDeadLetterService` and reported id 999999
    as not found.
  - `execute-replay` and `discard` are additionally gated by a break-glass authorization filter
    (SEC-BG-001) before the business layer. Route + role auth verified; destructive replay
    requires a valid break-glass credential that we did not have in this session (correctly enforced).
- Webhook mutations (4 endpoints): route + OAuth `ROLE_BANK` + validation verified for GET / DELETE
  (204 idempotent) / test (404) / secret/rotate (400 validation). Full happy path still blocked
  by the same outbound-allowlist gap documented in 74-bank-role-oauth-unblocked.

## Coverage impact

These 12 endpoints have their `authenticated read + unauthorized negative` or
`happy path + authorization + validation` criteria satisfied to the extent the environment allows.
Endpoints requiring destructive ops (dead-letter execute/discard) or real fixtures for a full
happy path remain flagged separately as "needs prod-parity fixture" in the matrix summary.
