# Phase 16 — Event Schema Governance

Kafka dispatch messages now carry an explicit schema name, integer version, event UUID, aggregate
identifier, and occurrence time. Consumers reject unknown names, versions, invalid UUIDs, and missing
required fields. Legacy messages are accepted only when `switching.outbox.schema.allow-legacy-messages`
is explicitly enabled for a controlled migration window.

The compatibility workflow compares every registered candidate schema with its committed baseline.
Removing fields, changing types or constants, or narrowing constraints fails CI. Breaking changes must
use a new schema version and a parallel consumer migration rather than overwriting v1.
