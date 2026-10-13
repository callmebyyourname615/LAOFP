# Phase 33-42 Control Evidence Map

| Control Area | Evidence Artifact | System Of Record | Owner |
|---|---|---|---|
| Double-entry ledger | Balanced journal report and script output | Release evidence ticket | Finance Ops |
| Intraday liquidity | Prefunding threshold report | Daily control room ticket | Treasury Ops |
| Tariff governance | Tariff bundle validation result and approval record | Change ticket | Product Ops |
| FX governance | FX source freshness and approval result | Treasury evidence folder | Treasury Ops |
| Certificate lifecycle | Certificate expiry report | Security evidence folder | SecOps |
| Regulatory reporting | Manifest JSON and artifact hashes | Regulatory submission ticket | Compliance |
| Notification delivery | Template lint output | Release evidence ticket | Platform Ops |
| Change freeze | Release gate decision and evidence hash | Change ticket | Change Manager |
| Synthetic monitoring | Probe result and alert status | Monitoring evidence folder | SRE |
| Incident CAPA | Overdue action report | Incident management system | Incident Manager |

## Evidence Rules

- Evidence must include command, environment, timestamp, and operator identity.
- Evidence files must be immutable after approval or include a hash in the change ticket.
- Failed controls require a corrective action before production promotion.
- Waivers require Change Manager and control owner approval.

## Static Verification

The static verifier confirms the required migrations, scripts, services, and release workflow hooks exist. This control map is the operator-facing index that links those controls to production evidence.
