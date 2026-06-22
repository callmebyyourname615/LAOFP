# Observability Anomaly Response

1. Confirm baseline season and freshness.
2. Check correlated metrics, logs and traces.
3. Acknowledge critical anomalies only after assigning an owner.
4. Suppression requires owner, reason and expiry; critical suppression requires two approvers.
5. Investigate database saturation, Kafka delivery degradation and Vault/authentication correlation first.
6. Re-run Phase 57F after mitigation. Unacknowledged critical anomalies block self-service production operations.
