# 18.17 Bank And Connector Status

Status: PASS

Evidence:
- Bank-status endpoint returned participant, connector, route, transfer, and outbox health context for all banks.
- Connector-health endpoint differentiated healthy, degraded, and down connectors with operational reasons.
- Down UAT entries were expected inactive participants or intentionally disabled test connectors.
- Degraded active connectors identified the two known historical failed outbox events.

Conclusion:
Operations has an actionable bank and connector health view with sufficient context to triage expected versus operational issues.
