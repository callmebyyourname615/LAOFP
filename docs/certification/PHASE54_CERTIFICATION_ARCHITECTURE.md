# Phase 54 Production Certification Architecture

## Purpose

Phase 54 converts repository readiness into runtime certification evidence bound to one immutable release. Every artifact is tied to:

- a full 40-character Git commit;
- an immutable `sha256:` image digest;
- a release/change reference;
- a non-production execution environment (`uat`, `performance`, or `dr`).

Production execution is intentionally prohibited. Production receives only a release candidate whose UAT/performance/DR evidence is complete and hash-verified.

## Certification model

The source of truth is `config/phase54-certification-plan.yaml`. Thresholds are centralized in `config/phase54-thresholds.yaml`. Each phase produces `build/phase54-certification/phases/<phase>/result.json`, logs, and machine-readable evidence. `build_certification_manifest.py` hashes all evidence and computes `releaseCandidateReady`.

| Phase | Runtime certification | Release blocker |
|---|---|---|
| 54A | clean build, all tests, static gates, JaCoCo, test evidence | Yes |
| 54B | clean V1→V83, upgrade V82→V83, image migration twice | Yes |
| 54C | digest-pinned UAT deploy, readiness, migration job, rollback | Yes |
| 54D | smoke, 2k sustained, 10k burst, 8h soak, capacity capture | Yes |
| 54E | 500k settlement cycle, duration and zero-sum validation | Yes |
| 54F | encrypted backup, object verification, isolated restore, RPO/RTO | Yes |
| 54G | six destructive failure scenarios and recovery validation | Yes |
| 54H | gitleaks, OWASP DC, Trivy, SPDX SBOM, signature, attestation | Yes |
| 54I | health, required metrics, dashboards, alerts and delivery route | Yes |
| 54J | 5→25→50→100 canary, rollback rehearsal, release candidate | Yes |

## Evidence immutability

A completed phase cannot be overwritten silently. Rerunning requires:

```bash
export CERTIFICATION_RERUN_CONFIRMATION=I_UNDERSTAND_THIS_ARCHIVES_THE_PREVIOUS_ATTEMPT
```

The previous phase directory is moved to `build/phase54-certification/attempts/` before the new attempt starts.

## Release identity contract

```bash
export CERTIFICATION_ENVIRONMENT=uat
export RELEASE_REFERENCE=CHG-2026-000123
export RELEASE_GIT_COMMIT=<40-lowercase-hex>
export RELEASE_IMAGE_REPOSITORY=ghcr.io/<owner>/<repo>/switching-api
export RELEASE_IMAGE_DIGEST=sha256:<64-lowercase-hex>
```

A phase result with a different commit, digest, or release reference cannot be merged into the final manifest.

## Final decision

`releaseCandidateReady=true` only when phases 54A through 54J are all `PASS`. `NOT_RUN` and `FAIL` are both blocking states. Hash changes, missing files, path traversal, duplicate phase IDs, and release identity mismatches invalidate the package.
