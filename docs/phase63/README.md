# Phase 63A–63J — UAT Runtime Evidence and Entry Certification

Phase 63 executes the production-readiness tooling that Phase 61 prepared. It is deliberately additive: it does not change application source, Flyway migrations, Maven dependencies, or existing Phase 61 scripts.

## Modes

```bash
scripts/phase63/run_phase63.sh --preflight
scripts/phase63/run_phase63.sh --repo
scripts/phase63/run_phase63.sh --full
```

- `--preflight` validates file contracts only. No Maven, network calls, load, backup, secret rotation or fault injection.
- `--repo` additionally executes the Phase 61 repository gate (`61A`, `61B`, `61D`, `61E`). Runtime phases remain `PREPARED`.
- `--full` runs against UAT and requires `TARGET_ENVIRONMENT=uat`, `PHASE63_EXECUTE_RUNTIME=true`, `CONFIRM_UAT_DRILLS=yes`, signed attestations and explicit topology-specific command hooks.

A populated `config/phase63/execution.env` must never be committed. Start from `config/phase63/execution.env.example` and inject passwords/tokens through the runner or Vault.

## Phase ownership

| Phase | Evidence produced | PASS condition |
|---|---|---|
| 63A | UAT dependency probes and platform inventory | Live contract and infrastructure gap report pass |
| 63B | Phase 61 preflight/repository results | 61A/61B/61D/61E PASS |
| 63C | Rotation/supply-chain verification | Signed Phase 61F attestation and strict repository hygiene pass |
| 63D | k6/capacity evidence | Phase 61G full UAT certification passes |
| 63E | Backup/PITR logs and signed report | RPO < 5 minutes, RTO < 30 minutes, zero loss |
| 63F | DR/failover/failback evidence | All controlled scenarios PASS with zero loss |
| 63G | Rule inventory, routing and delivery evidence | All repository alerts valid; all PrometheusRule alerts delivered and resolved |
| 63H | SMOS endpoint/RBAC matrix | Public allowlist and protected operator endpoints pass audit |
| 63I | 500K settlement and sanctions evidence | Phase 61H plus signed sanctions evidence pass |
| 63J | SHA-256 manifest and approvals | 63A–63I all PASS and signed UAT entry attestation valid |

## Safety rules

1. Never run `--full` against production.
2. Never place secrets in evidence logs or JSON attestations.
3. Command hooks are mandatory for topology-specific failover/backup operations; the repository does not guess cloud/Patroni/Vault/MinIO commands.
4. `PREPARED` is not runtime certification and cannot pass the 63J full gate.
5. Keep the complete run directory under `evidence/phase63/<run-id>/` immutable after approval.
