# Phase 73 Operator Runbook

## Before execution

1. Confirm the cluster context points to UAT and its name does not contain `prod`.
2. Confirm Chaos Mesh and the PodChaos, NetworkChaos, DNSChaos and StressChaos CRDs are installed.
3. Populate database, Kafka, object storage and external API CIDRs in a secured UAT policy override. Empty target arrays fail closed.
4. Implement a read-only financial integrity adapter. It must produce:

```json
{"balanceMismatchCount": 0, "outboxBacklogGrowth": 0}
```

The values must come from UAT queries; assumed values are prohibited.
5. Create a time-bounded approval document from `docs/templates/PHASE73_CHAOS_APPROVAL.example.json`.
6. Confirm only one experiment will run at a time and that SRE, application, database and Kafka operators are present.
7. Confirm backups and rollback procedures are available.

## Preflight

```bash
python3 scripts/verify_phase73_static.py
scripts/phase73/run_phase73.sh --preflight
```

## Full UAT execution

```bash
export TARGET_ENVIRONMENT=uat
export PHASE73_EXECUTE_CHAOS=true
export CHAOS_APPROVAL_FILE=/secure/phase73-approval.json
export CHAOS_APPROVAL_TOKEN=CHAOS-UAT-CHANGE-12345678
export PHASE73_SIGNING_KEY=/secure/phase73-signing-key.pem
export PHASE73_FINANCIAL_INTEGRITY_COMMAND=/opt/switching/bin/phase73-financial-integrity
export DB_URL='jdbc-compatible-psql-url-or-host-options'
export DB_USERNAME='...'
export DB_PASSWORD='...'
export BASE_URL='https://uat.example/actuator/health'
scripts/phase73/run_phase73.sh --full
```

Use the actual `psql`-compatible `DB_URL` format expected by the existing DR scripts.

## Emergency stop

Delete Phase 73 experiments and verify application recovery:

```bash
kubectl -n switching delete podchaos,networkchaos,dnschaos,stresschaos \
  -l switching.run-id --ignore-not-found=true
kubectl -n switching rollout status deployment/switching-api --timeout=10m
```

Do not continue to another scenario until cleanup, application health and reconciliation are green.

## Evidence

Evidence is written beneath `build/phase73-evidence/<run-id>/`. Phase 73J creates a signed bundle and `SHA256SUMS`. A run is not certified merely because fault injection completed; 73I and 73J must both pass.
