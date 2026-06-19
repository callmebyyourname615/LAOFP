# RB-15 — Dead-Letter Quarantine and Replay

A consumer failure is acknowledged only after the complete queue message and SHA-256 digest are saved
in `outbox_dead_letters`. If database quarantine fails, the Kafka listener throws and the original
record remains retryable.

Replay is a four-step state machine: `QUARANTINED → REPLAY_REQUESTED → APPROVED → REPLAYED`. The
requester cannot approve the same record. Execution rechecks payload hash, publishes synchronously,
and records chained audit events. Replay execution and discard both require a valid break-glass token in addition to normal role checks.
Use discard only after confirming the source event must never run. Never edit `payload_json` directly; the integrity check intentionally blocks modified records.
