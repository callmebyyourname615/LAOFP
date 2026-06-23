# Read-Your-Writes and Replica Freshness Policy

Database reads must declare one of three semantics:

- `STRICT_PRIMARY`: always use the primary database.
- `READ_YOUR_WRITES`: always use the primary database. Use after any mutation when the caller must immediately observe its write.
- `EVENTUAL`: may use the configured reporting replica only when PostgreSQL confirms that it is in recovery and replay lag is no greater than `switching.read-replica.max-lag`.

Replica probe failure, a primary accidentally configured as the reporting datasource, negative/unknown lag, or excessive lag all fail closed to the primary. Financial reconciliation defaults to `STRICT_PRIMARY`; operators must explicitly request `EVENTUAL` for a lag-tolerant report.
