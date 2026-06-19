# Production Cutover Checklist

## Before 55A
- [ ] Phase 54 manifest is `releaseCandidateReady=true`.
- [ ] Application and migration images are digest-pinned and signed.
- [ ] SBOM and SLSA provenance exist for both images.

## Before 55G
- [ ] 55A–55F are PASS with identical release identity.
- [ ] Production change window is open and no freeze blocks the release.
- [ ] V83 dry run passed against a production-like restore.
- [ ] Production baseline is captured and archived.
- [ ] Rollback image and schema compatibility are documented.
- [ ] War room, owners, escalation paths, and rollback authority are active.
- [ ] Five-percent promotion decision is signed and binds the latest evidence hash.

## Every traffic stage
- [ ] Signed two-person `PROMOTE` decision is fresh.
- [ ] Decision release identity matches the candidate.
- [ ] Decision evidence hash matches the previous stage.
- [ ] Prometheus gate passes.
- [ ] Reconciliation passes.
- [ ] No critical alerts are firing.
- [ ] Observation period completed.

## At 100%
- [ ] Stable Deployment uses the candidate digest.
- [ ] Canary ingress weight is zero and canary replicas are zero.
- [ ] Post-cutover reconciliation passes.
- [ ] Hypercare start time is recorded.
