# RB-20 — Participant Certification and Continuous Resilience

The certification runner executes a versioned JSON plan over TLS/mTLS, applies API-key authentication
and request HMAC exactly as production clients do, hashes each response, and emits a tamper-evident
result. Secrets and response bodies are not written to evidence.

An ADMIN records the evidence SHA-256, exact Git commit, immutable image digest, result, execution time,
and expiry. New participants default to `INACTIVE`; direct creation as `ACTIVE` and direct status changes
are rejected at the service layer. Participant activation through the four-eyes workflow is blocked
unless the latest certification is an unexpired `PASS`. A newer `FAIL` supersedes an older PASS. Metrics
detect active participants outside the controlled path and certifications expiring within 30 days.

The weekly UAT resilience workflow is intentionally bounded to one application-pod deletion and the
existing recovery/reconciliation checks. Broader DR scenarios remain manual because they affect Kafka,
object storage, network policy, or deployment state.
