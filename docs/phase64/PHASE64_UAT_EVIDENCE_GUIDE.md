# Phase 64A–64J — UAT Runtime Evidence and Phase 54 Entry Gate

Phase 64 is an additive orchestration and evidence layer. It does not modify application code, Flyway migrations, Phase 61 scripts, or Phase 54 certification logic.

## Objectives

- Acquire Phase 61 evidence by verified import or explicit UAT execution.
- Acquire the complete runtime evidence bundle by verified import or explicit execution.
- Certify build/test, performance, backup/PITR, DR and alert evidence against one immutable release identity.
- Block Phase 54 unless every machine gate is PASS.
- Produce a human-approved, SHA-256-covered and OpenSSL-signed handoff bundle.

## Safety model

Full execution is rejected unless all guards are present:

```bash
export TARGET_ENVIRONMENT=uat
export PHASE64_EXECUTE_RUNTIME=true
export CONFIRM_UAT_EVIDENCE=yes
```

Production is not an allowed Phase 64 target. Load tests and disruptive recovery drills must run only in approved UAT/performance/DR infrastructure.

## Preflight

```bash
scripts/phase64/run_phase64.sh --preflight
```

Preflight validates contracts, templates, current alert/runbook mappings, helper tests and signing-tool availability. It performs no remote calls, load generation, fault injection or signing.

## Full mode with imported evidence

```bash
export TARGET_ENVIRONMENT=uat
export PHASE64_EXECUTE_RUNTIME=true
export CONFIRM_UAT_EVIDENCE=yes

export RELEASE_REFERENCE=uat-rc-2026-001
export RELEASE_GIT_COMMIT=<40-character-commit>
export APPLICATION_IMAGE_DIGEST=sha256:<64-hex>
export MIGRATION_IMAGE_DIGEST=sha256:<64-hex>

export PHASE64_PHASE61_MODE=import
export PHASE61_MANIFEST=/evidence/phase61/<run-id>/manifest.json
export PHASE64_RUNTIME_MODE=import
export RUNTIME_EVIDENCE_MANIFEST=/evidence/runtime/<run-id>/manifest.json

export UAT_ENV_FILE=/secure/phase64-uat.env
export MANAGEMENT_BASE_URL=https://switching-uat.example.internal
export TLS_CA_FILE=/secure/uat-ca.pem

export PHASE64_SUPPLEMENTAL_PERFORMANCE_DIR=/evidence/k6-10k-20k
export BACKUP_PITR_ATTESTATION=/approvals/backup-pitr.json
export DR_ATTESTATION=/approvals/dr.json
export ALERT_ATTESTATION=/approvals/alerts.json
export PHASE64_HANDOFF_APPROVAL=/approvals/phase54-entry.json
export PHASE64_SIGNING_KEY=/secure/phase64-signing-private.pem
export PHASE64_SIGNING_PUBLIC_KEY=/secure/phase64-signing-public.pem

scripts/phase64/run_phase64.sh --full
```

The runtime evidence plan currently provides smoke, sustained 2K, capacity, settlement and soak evidence. Phase 64 additionally requires sustained 10K and burst 20K summaries. Supply those through `PHASE64_SUPPLEMENTAL_PERFORMANCE_DIR`, or set:

```bash
export PHASE64_RUN_SUPPLEMENTAL_PERFORMANCE=true
```

This executes the existing repository scenarios `sustained-10k-tps` and `burst-20k-tps`.

## Full mode with direct execution

```bash
export PHASE64_PHASE61_MODE=execute
export PHASE64_RUNTIME_MODE=execute
export PHASE64_RUN_SUPPLEMENTAL_PERFORMANCE=true
```

All environment variables required by Phase 61, runtime evidence, k6, backup, DR, Vault and Alertmanager must also be supplied. Direct runtime execution includes an eight-hour soak and disruptive UAT drills.

## Required attestations

Start from the repository templates:

- `docs/templates/PHASE64_BACKUP_PITR_ATTESTATION.example.json`
- `docs/templates/PHASE64_DR_ATTESTATION.example.json`
- `docs/templates/PHASE64_ALERT_ATTESTATION.example.json`
- `docs/templates/PHASE64_HANDOFF_APPROVAL.example.json`

Attestations must reference the same release reference, Git commit and application image digest used by Phase 61 and runtime evidence.

The alert attestation must contain one row for every current Prometheus alert. Phase 64 enumerates the repository dynamically; it does not accept a stale hard-coded alert count.

## Output

Default output:

```text
build/phase64-evidence/<run-id>/
```

Successful full mode produces:

```text
64I/phase54-entry-decision.json
64J/bundle-manifest.json
64J/bundle-manifest.sig
64J/signing-public-key.pem
64J/checksums.sha256
64J/bundle.sha256
64J/phase64-uat-evidence-bundle.zip
64J/PHASE54_HANDOFF.md
```

Phase 54 may start only when `64I/phase54-entry-decision.json` contains `APPROVE_PHASE54_ENTRY` and the 64J signature verifies successfully.

## Merge-conflict boundary

Phase 64 owns only:

- `scripts/phase64/**`
- `docs/phase64/**`
- `docs/templates/PHASE64_*`
- `config/phase64-entry-gate.yaml`
- `schemas/phase64-*`
- `.github/workflows/phase64-uat-evidence.yml`
- `scripts/verify_phase64_static.py`
- `scripts/execute-and-verify/08-phase64-preflight.sh`
- `AGENT/PHASE_64A_64J_IMPLEMENTATION_CHECKLIST.md`

Do not resolve Phase 62/63 conflicts by changing business services, test classes, migrations, Phase 61 scripts, or shared Phase 54 files in this phase.
