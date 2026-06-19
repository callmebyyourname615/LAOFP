# RB-19 — Four-Eyes Configuration Changes

Direct PATCH endpoints for participant, routing-rule, and connector changes are denied when API-key
security is active. Supported operational toggles use a controlled request:

1. Request captures the current value, desired value, ticket, reason, expiry, and canonical SHA-256.
2. A different ADMIN approves.
3. Execution requires an active break-glass token belonging to the executing requester.
4. The service rereads current state. Drift marks the request `STALE` and blocks execution.
5. The canonical hash is checked before the repository update and every transition is audit-chained.

Supported targets are participant status, connector enabled, connector force-reject, and routing-rule
enabled. Structural creation or endpoint changes remain separate onboarding/change processes.
