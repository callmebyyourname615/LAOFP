# RB-14 — SLO and Error-Budget Policy

The production availability objective is 99.95% over a rolling 30-day window. Server-side HTTP 5xx
responses are counted as bad events; all HTTP requests are total events. Missing Prometheus data is a
gate failure, not a pass.

## Fast burn

Page the on-call engineer, stop progressive rollout, compare stable/canary, and prioritize restoration.
A 14.4x burn consumes roughly 2% of a 30-day budget in one hour.

## Slow burn

Open an incident and investigate degradation before it becomes customer-visible at scale.

## Error-budget policy

When less than 20% remains, freeze standard releases. Reliability, security, and emergency changes
require an explicit approved exception. When exhausted, keep the freeze until the rolling budget
recovers and the incident review identifies corrective actions.
