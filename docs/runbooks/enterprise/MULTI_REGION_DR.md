# Multi-Region DR Certification

Phase 57B verifies, but does not initiate, a cross-region failover. The external drill must provide topology, failover and failback reports.

Mandatory controls:

- exactly one writable primary region;
- region fencing and single-writer enforcement;
- PostgreSQL, Kafka, Vault and object-storage replication;
- DR warm capacity of at least 25%;
- DNS failover inside the configured RTO;
- zero lost and zero duplicate transactions;
- post-failover and post-failback reconciliation PASS;
- two distinct failback approvers.

Abort immediately if split-brain is suspected. Fence the old primary before allowing writes in DR.
