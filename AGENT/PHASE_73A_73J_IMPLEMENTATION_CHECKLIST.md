# Phase 73A–73J Implementation Checklist

## Isolation and safety

- [x] Use additive-only Phase 73 paths.
- [x] Do not modify application source, tests, Flyway migrations or Phase 71/72 paths.
- [x] Deny production execution in policy.
- [x] Require UAT target, explicit execute flag, approval file and matching token.
- [x] Reject expired, future-dated or scenario-incomplete approvals.
- [x] Limit execution to one experiment at a time.
- [x] Add cleanup trap to every fault injection execution.

## Phase implementation

- [x] 73A Chaos Mesh platform and UAT readiness gate.
- [x] 73B Application pod-kill experiment.
- [x] 73C Database network-loss experiment.
- [x] 73D Kafka network-delay experiment.
- [x] 73E Object-storage network-loss experiment.
- [x] 73F External API delay experiment.
- [x] 73G DNS error experiment.
- [x] 73H CPU and memory stress experiments.
- [x] 73I Financial integrity and recovery verifier.
- [x] 73J Signed resilience bundle and release gate.

## Evidence contracts

- [x] Result JSON schema.
- [x] Approval JSON schema.
- [x] Scenario attestation JSON schema.
- [x] Resilience bundle JSON schema.
- [x] SHA-256 artifact inventory.
- [x] OpenSSL signature generation and verification.
- [x] Operator runbook and exit criteria.

## Validation

- [x] Static verifier passes.
- [x] Shell syntax passes.
- [x] Python syntax passes.
- [x] JSON and YAML parse validation passes.
- [x] Unit tests pass.
- [x] Full Phase 73 preflight produces 73A–73J `PREPARED`.
- [x] Synthetic signed bundle verifies.
- [x] Changed-file boundary contains no Phase 71/72 or protected paths.

## Runtime execution — requires real UAT

- [ ] Chaos Mesh installed in UAT.
- [ ] Dependency CIDRs configured.
- [ ] Financial integrity adapter implemented with real queries.
- [ ] 73A–73J executed against UAT.
- [ ] Eight scenario attestations pass.
- [ ] Signed UAT resilience bundle approved by SRE, QA and Engineering.
