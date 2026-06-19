# Runbook — Third-Party Dependency SLA and Circuit

Check DNS/certificate validity, recent health samples, latency, failure class, active SLA policy, and persisted circuit state. The probe blocks redirects and non-public addresses; do not weaken this for testing.

When a circuit opens, verify the product's fail-closed/fallback behavior and customer messaging. A forced state requires an expiring independently approved override. Remove the override after recovery and require the normal recovery-success threshold before closing.
