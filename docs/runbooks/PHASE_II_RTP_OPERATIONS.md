# Phase II RTP Operations Runbook

## Checks

- Confirm `PHASE_II_RTP_ENABLED=true` only in approved environments.
- Monitor pending, authorised, settled, declined, and expired RTP counts.
- Review installment retries when `attempt_count` increases.

## Smoke

- Create RTP request.
- Authorise request.
- Settle request within authorised amount.
- Confirm `rtp.authorised` and `rtp.settled` events.

## Failure Handling

- Expired requests are handled by `RtpExpiryScheduler`.
- Installment retries are handled by `RtpInstallmentScheduler`.
- Settlement exceeding authorised amount must fail closed.
