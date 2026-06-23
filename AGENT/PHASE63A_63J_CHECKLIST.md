# Phase 63A–63J Implementation Checklist

## Repository implementation

- [x] 63A UAT environment inventory and gap-report automation
- [x] 63B Phase 61 preflight/repository execution and result verifier
- [x] 63C secret-rotation/supply-chain ceremony verifier
- [x] 63D Phase 61G performance/capacity runtime wrapper
- [x] 63E backup/PITR runtime hooks and signed verifier
- [x] 63F DR/failover/failback runtime hooks and signed verifier
- [x] 63G alert inventory, runbook, delivery and signed verification
- [x] 63H SMOS endpoint/RBAC audit and permission matrix
- [x] 63I Phase 61H settlement plus sanctions evidence verifier
- [x] 63J immutable manifest and UAT entry gate
- [x] Static contract verifier and safe CI/preflight entry point
- [x] Bash/Python/JSON/YAML validation
- [x] Preflight execution produces only PREPARED, never false runtime PASS
- [x] Changed-files-only delivery package

## Runtime/operator execution — not complete in repository build environment

- [ ] Run `scripts/phase63/run_phase63.sh --repo` with Maven/Testcontainers access
- [ ] Rotate six credentials and complete signed supply-chain attestation
- [ ] Run full k6/capacity scenarios on UAT
- [ ] Execute backup and PITR restore drill
- [ ] Execute DR/failover/failback scenarios
- [ ] Validate alert delivery and Pending/Firing/Resolved behavior
- [ ] Run SMOS UAT security suite and provision approved initial admins
- [ ] Execute 500K settlement and sanctions-provider synchronization
- [ ] Sign UAT entry attestation
- [ ] Run `scripts/phase63/run_phase63.sh --full` and obtain 63J PASS
