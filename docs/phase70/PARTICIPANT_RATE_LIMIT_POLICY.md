# Participant Rate-Limit Policy

The policy file is `config/phase70-participant-traffic-policy.yaml`. It intentionally uses JSON syntax, which is valid YAML and can be parsed by the existing Jackson dependency without adding another parser.

Each quota defines:

- `capacity`: maximum tokens held by the participant bucket.
- `refillTokens`: tokens restored each period.
- `refillPeriodSeconds`: refill period.

Authenticated API-key calls use the `bankCode` authentication detail. OAuth PSP calls use the PSP principal. Legacy API keys are represented only by a SHA-256 prefix; plaintext keys are never retained. Unauthenticated calls fall back to the direct remote address and do not trust `X-Forwarded-For`.

Reload behavior is fail-safe: a valid changed file atomically replaces the active policy; an invalid or missing file leaves the last good policy active. A changed policy revision rebuilds affected buckets. `max-identities` bounds in-memory state, with excess untrusted identities sharing an overflow bucket.

The same reviewed default is packaged at `src/main/resources/phase70/participant-traffic-policy.json`, so an immutable JAR has a safe baseline when no external file is mounted. An external file takes precedence and remains runtime-reloadable.
