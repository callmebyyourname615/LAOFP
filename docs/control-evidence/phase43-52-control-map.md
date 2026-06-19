# Phase 43–52 Control Evidence Map

| Phase | Control | Primary evidence | Runtime owner |
|---|---|---|---|
| 43 | Entitlement and transaction limit enforcement | `transaction_limit_decision_audit` | Payments/Risk |
| 44 | Balanced, independently approved manual adjustments | adjustment + ledger journal + approval events | Finance Operations |
| 45 | Versioned settlement calendar and cutoff decisions | calendar bundle hash + `settlement_cutoff_decision` | Settlement Operations |
| 46 | Idempotency, duplicate protection, and finality reversal | idempotency/fingerprint/finality/reversal records | Payments Operations |
| 47 | Crypto inventory and rotation | asset inventory + rotation evidence | Security |
| 48 | Third-party SLA and circuit control | health samples + circuit state + override | Platform Operations |
| 49 | Capacity forecast and governed HPA | observations + forecast + approved change | SRE/Performance |
| 50 | Data lineage and sealed evidence | asset/edge catalog + verification record | Data Governance |
| 51 | Rule/model version approval and deployment | manifest/test/deployment hashes | Risk/Compliance |
| 52 | Controlled decommission and encrypted data exit | plan/tasks/approvals/artifacts/events | Operations/Business |

Evidence is not complete until the corresponding CI/UAT execution output is registered in `control_evidence_catalog` and independently verified.
