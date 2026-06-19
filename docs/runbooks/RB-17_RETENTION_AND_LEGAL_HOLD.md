# RB-17 — Retention and Legal Hold

Legal holds are explicit database records with scope, case reference, effective date range, requester,
approver, and immutable audit events. A requester cannot approve the hold. Release also requires a
second actor and, after Phase 20, a valid break-glass session.

Before dropping any archived partition, `PartitionMaintenanceService` checks active and
release-requested table holds for the partition date. A release request remains binding until a different
actor approves it. A blocked drop is logged as `DROP_BLOCKED_LEGAL_HOLD` and fails closed. Do not bypass
this service with manual `DROP TABLE`; emergency database actions require legal/compliance approval and
must be added to the evidence bundle.
