# Phase 69 Operator Runbook

## 1. Merge order

1. Merge Phase 68A–68J.
2. Apply the Phase 69 changed-files package.
3. Resolve only genuine source conflicts; never overwrite `scripts/phase68/**`, `docs/phase68/**`, `config/phase68/**`, `schemas/phase68/**` or `evidence/phase68/**`.
4. Run the static contract.

```bash
python3 scripts/verify_phase69_static.py
scripts/phase69/run_phase69.sh --preflight
```

## 2. Full verification

Use a machine with Java 21, Docker/Testcontainers access, Maven dependency access and the required repository tooling.

```bash
export PHASE69_CONFIRM_FULL=YES
export PHASE69_RUN_ID="phase69-$(date -u +%Y%m%dT%H%M%SZ)"
export PHASE69_ATTESTATION=/secure/attestations/phase69-release.json
scripts/phase69/run_phase69.sh --full
```

## 3. Evidence review

Review:

- `results/69A.json` through `results/69J.json`
- targeted and full JUnit summaries
- Maven clean-verify log
- repository-gate log
- `phase69-verification-manifest.json`
- `SHA256SUMS`

Only `decision=VERIFIED` closes the P0 verification blocker. `PREPARED` means scripts are ready but runtime tests have not run. `BLOCKED` requires remediation and a completely new run ID.

## 4. Attestation rules

The attestation must be stored outside Git while pending. It must contain `approved=true`, a non-empty approver, role, approval timestamp and the exact verified Git commit. Never place credentials or tokens in the attestation or evidence directory.
