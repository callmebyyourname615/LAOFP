# RTP Request Service Runbook

## Enablement

RTP is disabled by default. Enable only after V85 has migrated and schema
validation is green:

```bash
export PHASE_II_RTP_ENABLED=true
```

Production certification must also run:

```bash
python3 scripts/verify_phase_ii_01_04_static.py --strict-predecessors
```

The strict check intentionally blocks if V83 or V84 is absent.

## Operational checks

```sql
SELECT status, count(*) FROM rtp_request GROUP BY status ORDER BY status;
SELECT count(*) FROM rtp_request
WHERE status = 'PENDING_AUTH' AND expires_at <= now();
SELECT request_id, count(*) FROM rtp_state_transition
GROUP BY request_id HAVING count(*) = 0;
```

## Common failures

- `RTP-002`: correlation ID reused with a different canonical payload.
- `RTP-003`: caller attempted a forbidden state transition.
- `RTP-004`: participant is neither payer nor payee.
- `RTP-005`: expiry is in the past or exceeds the configured maximum.

## Rollback

Disable `PHASE_II_RTP_ENABLED`. Do not delete V85 or edit its checksum after it
has been applied. Schema rollback requires an approved forward migration.
