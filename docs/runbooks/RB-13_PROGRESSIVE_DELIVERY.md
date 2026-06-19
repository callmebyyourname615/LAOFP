# RB-13 — Progressive Delivery

## Preconditions

- The exact image digest passed CI, security, migration-image tests, and release-evidence verification.
- Prometheus is reachable from the deployment runner and has stable/canary HTTP metrics.
- The canary ingress controller supports weighted canary routing.

## Stages

The default sequence routes 5%, 25%, then 50% of traffic to two canary pods. Each stage waits five
minutes and checks 5xx ratio and p95 latency. A failed query, missing metric, unhealthy rollout, or
threshold breach is fail-closed: canary weight is reset to zero and canary replicas are scaled to zero.

## Rollback

If stable promotion fails, the script restores the prior stable image and waits for rollout. Never
manually promote the canary deployment as the permanent stable deployment; stable identity and HPA
must remain attached to `deployment/switching-api`.
