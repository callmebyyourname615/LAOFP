# Phase 66 Operator Guide

## Safe validation

```bash
scripts/phase66/run_phase66.sh --preflight
```

This produces `PREPARED` evidence only and performs no network calls, Maven build, load, backup, credential rotation or fault injection.

## Repository closure

```bash
scripts/phase66/run_phase66.sh --repo
```

This runs repository-safe checks. It does not execute UAT load, restore, DR or rotation ceremonies.

## Full UAT execution

Use an approved immutable release and an isolated UAT environment. Export the dependency, database, SMOS and approval variables described in the individual phase documents, then set:

```bash
export TARGET_ENVIRONMENT=uat
export PHASE66_EXECUTE_RUNTIME=true
export CONFIRM_UAT_RUNTIME=yes
export CONFIRM_UAT_LOAD=I_UNDERSTAND_THIS_GENERATES_LOAD
export CONFIRM_UAT_DESTRUCTIVE=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export CONFIRM_SECRET_ROTATION_CEREMONY=I_UNDERSTAND_THIS_ROTATES_CREDENTIALS
scripts/phase66/run_phase66.sh --full
```

Do not reuse production credentials in UAT evidence. A full run remains blocked until Phase 65 is detected and every required runtime control passes.
