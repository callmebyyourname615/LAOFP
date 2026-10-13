# Phase 43-52 Control Evidence Map

| Control | Evidence | Owner |
|---|---|---|
| Transaction limits | Approved policy file and validation output | Risk Ops |
| Manual adjustments | Pending adjustment report and balanced execution journal | Finance Ops |
| Settlement calendar | Calendar validation output and cutoff approval | Settlement Ops |
| Payment finality | Finality integrity report and duplicate fingerprint audit | Platform Ops |
| Crypto inventory | Asset inventory and rotation plan report | SecOps |
| Third-party SLA | Dependency probe result and circuit-state decision | SRE |
| Capacity autoscaling | Forecast output, HPA guardrail decision, rollback reference | SRE |
| Data lineage | Lineage validation and evidence verification output | Data Governance |
| Decision rules | Manifest validation and golden-case execution result | Risk / Compliance |
| Decommissioning | Readiness check, approvals, data-exit artifact | Operations |

## Evidence Rules

- Evidence must include command, environment, timestamp, and operator.
- Approval evidence must show requester and approver separation where required.
- Failed controls keep the production promotion gate closed.
- Waivers require Change Manager approval and the listed control owner.
