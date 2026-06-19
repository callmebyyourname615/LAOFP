# Phase 11 — Disaster-recovery drill automation

These scripts are intentionally destructive and refuse to run unless:

```text
DR_ENVIRONMENT=uat|dr
DR_CONFIRMATION=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
EVIDENCE_DIR=<new isolated directory>
```

Never run them against production. `DR_NAMESPACE` defaults to `switching`.

The suite captures baseline counts, runs only allowlisted scenario scripts, records an UTC timeline, verifies health and non-decreasing critical table counts, hashes every evidence file, and leaves final approval to humans. Any scenario that changes a replica count, ConfigMap, or NetworkPolicy captures the original state and installs an exit/signal trap to restore it.

Supported scenarios:

- `kill-application-pod`
- `kafka-broker-failure`
- `object-storage-failure`
- `network-partition`
- `deployment-rollback`
- `external-timeout`

Database-primary promotion remains delegated to the Phase 8 failover runbook because topology differs by environment. Perform it in the same drill window and copy its evidence into the final bundle.
