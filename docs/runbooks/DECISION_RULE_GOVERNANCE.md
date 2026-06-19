# Runbook — Fraud/AML Decision Rule Governance

1. Package rules with schema version, artifact hash, manifest hash, reason, owner, and rollback version.
2. Run golden, regression, false-positive, false-negative, performance, and edge-case suites.
3. Risk and Compliance approve independently of requester and deployer.
4. Deploy canary first; monitor decision distribution, alerts, customer impact, and processing latency.
5. Roll back to the recorded previous version when thresholds fail.

Never hot-edit an active artifact or change a hash in place.
