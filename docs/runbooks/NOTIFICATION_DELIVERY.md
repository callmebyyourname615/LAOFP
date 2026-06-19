# Notification Delivery Runbook

## Purpose
Approved template versions, recipient pseudonymization, idempotent queueing, bounded retries, and dead-letter visibility.

## Detection
Use the Phase 39 database evidence tables, Prometheus alerts, and control CronJob logs. Every operator action must include a ticket or incident reference.

## Response
1. Stop unsafe automated processing without deleting evidence.
2. Capture UTC timestamps, affected participants, business dates, and evidence hashes.
3. Use four-eyes approval for monetary, certificate, release, or regulatory state changes.
4. Re-run the relevant control and attach the output to the operational record.

## Recovery Gate
Recovery is complete only when the database invariant passes, the control job exits zero, related alerts clear, and evidence is reviewed by a role independent from the executor.
