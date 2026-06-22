# Phase II-01 through II-04 Delivery Notes

## Scope

This delivery contains 55 changed/new files and implements the first four Phase II steps on the uploaded source
baseline:

1. Phase II architecture and migration contract.
2. V85 Request-to-Pay schema and domain foundation.
3. RTP lifecycle state machine and transition guard.
4. RTP create, query, cancel, participant access, and idempotency API.

It intentionally does **not** implement authorisation, partial payment,
installments execution, transfer settlement, expiry scheduler, webhook events,
or operational metrics. Those belong to Phase II-05 through II-07.

## Runtime behavior

All Phase II features are disabled by default. RTP is enabled with:

```bash
PHASE_II_RTP_ENABLED=true
```

New RTP creation uses PostgreSQL `INSERT ... ON CONFLICT DO NOTHING`, so
concurrent calls with the same correlation ID resolve to one durable request.
The canonical request fingerprint is SHA-256 stored as `VARCHAR(64)`.

## API

- `POST /v1/rtp/requests`
- `GET /v1/rtp/requests/{id}`
- `POST /v1/rtp/requests/{id}/cancel`

BANK callers are scoped to RTPs where their authenticated participant identity
is payer or payee. OPS and ADMIN may read any RTP; ADMIN may create on behalf of
a participant.

## Migration prerequisite

The uploaded repository contains V1–V82, while `PHASE_II_PLANNING.md` states V84
is the predecessor. V85 remains allocated to RTP according to that plan.
Development verification emits a warning. Production certification must run:

```bash
python3 scripts/verify_phase_ii_01_04_static.py --strict-predecessors
```

That command fails until V83 and V84 have been merged. Do not enable Flyway
out-of-order migration in Production and do not renumber V85 after it has been
applied anywhere.

## Apply

Overlay the changed files at the project root, then run:

```bash
chmod +x apply-phase-ii-01-04.sh
./apply-phase-ii-01-04.sh
```

Optional targeted Maven/Testcontainers execution:

```bash
PHASE_II_RUN_MAVEN=true ./apply-phase-ii-01-04.sh
```
