# Phase 63G Alert Validation

The repository contains two relevant counts:

- the complete alert inventory, including plain Prometheus rule groups (62 in this baseline);
- the Kubernetes `PrometheusRule` inventory used by the synthetic Alertmanager delivery drill (58 in this baseline).

The signed attestation must match the generated counts. Direct Alertmanager injection proves routing, receiver selection and resolution delivery; the operator must additionally attest that representative Prometheus conditions were observed through Pending → Firing → Resolved.
