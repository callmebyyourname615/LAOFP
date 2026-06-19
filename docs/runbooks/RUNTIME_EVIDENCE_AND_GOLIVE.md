# Runtime Evidence Collection and Production Go-Live

## Purpose

This runbook turns production-readiness claims into immutable evidence tied to one Git commit, one container digest, one approved release reference, and one isolated non-production environment. It does not permit evidence collection against production.

## Safety model

- Allowed targets: `uat`, `performance`, and `dr` only.
- Load generation requires the exact acknowledgement `I_UNDERSTAND_THIS_GENERATES_LOAD`.
- Backup/restore, DR, Vault rotation, and alert delivery drills require the exact acknowledgement `I_UNDERSTAND_THIS_IS_DESTRUCTIVE`.
- No command uses `set -x`; secrets must never be copied into logs or evidence.
- The evidence builder defaults unexecuted controls to `NOT_RUN`.
- `goLiveReady` is true only when every `requiredForGoLive` control is `PASS`.
- The verifier recalculates readiness and verifies every artifact SHA-256 before sign-off.

## Release identity prerequisites

Prepare the immutable candidate first:

```bash
export RELEASE_REFERENCE=CHG-2026-000123
export RELEASE_GIT_COMMIT=<full-40-character-commit>
export RELEASE_IMAGE_DIGEST=sha256:<64-lowercase-hex>
export EVIDENCE_ENVIRONMENT=uat
```

Confirm the deployed image uses the same digest:

```bash
kubectl -n switching get deployment switching-api \
  -o jsonpath='{.spec.template.spec.containers[?(@.name=="switching-api")].image}'
```

Any mismatch invalidates the run. Do not relabel evidence for a different build.

## Evidence modes

### Preflight

Runs all static gates, the complete Maven verification, the isolated migration application test through Flyway V83, and sanctions fixture tests.

```bash
scripts/evidence/run_runtime_evidence.sh preflight
```

### Performance

Runs smoke, sustained 2k TPS, burst 10k TPS, VPA 500 concurrency, QR 200 concurrency, webhook 10k, settlement 500k, and a capacity snapshot.

```bash
export EVIDENCE_ENVIRONMENT=performance
export EVIDENCE_ALLOW_TRAFFIC=I_UNDERSTAND_THIS_GENERATES_LOAD
export BASE_URL=https://switching-perf.example.internal
export API_KEY=<runtime-secret>
export DB_URL='postgresql://...'
export DB_USERNAME=<runtime-secret>
export DB_PASSWORD=<runtime-secret>
scripts/evidence/run_runtime_evidence.sh performance
```

Acceptance criteria include scenario thresholds, zero balance mismatch, settlement cycle below 1,800 seconds, and resource/queue evidence sufficient to reproduce the decision.

### Soak

Run separately so an interrupted eight-hour test cannot erase shorter evidence:

```bash
export EVIDENCE_ALLOW_TRAFFIC=I_UNDERSTAND_THIS_GENERATES_LOAD
scripts/evidence/run_runtime_evidence.sh soak
```

Review memory growth, GC pause, Hikari saturation, Kafka lag, outbox backlog, error rate, and P95/P99 drift across the full interval.

### Resilience

Only in an isolated UAT/DR environment with an approved drill window:

```bash
export EVIDENCE_ENVIRONMENT=dr
export EVIDENCE_ALLOW_DISRUPTIVE=I_UNDERSTAND_THIS_IS_DESTRUCTIVE
export ALERT_DRILL_CONFIRMATION=I_UNDERSTAND_THIS_SENDS_TEST_ALERTS
export ALERTMANAGER_URL=https://alertmanager.dr.internal
export ALERT_EXPECTED_RECEIVER=switching-drill-sink
# Supply the approved backup, DR and Vault variables from the secret store.
scripts/evidence/run_runtime_evidence.sh resilience
```

This mode performs restore, DR, Vault rotation and alert-delivery drills. The Alertmanager route must send `drill=true` alerts to a non-paging drill receiver.

## Bundle structure

```text
build/runtime-evidence/<release>-<timestamp>/
├── logs/
├── artifacts/
├── step-results.jsonl
├── manifest.json
└── SUMMARY.md
```

`manifest.json` contains release identity, control status, artifact sizes and SHA-256 values. `SUMMARY.md` is human-readable but is not authoritative; the signed manifest is authoritative.

## Verification and sign-off

```bash
python3 scripts/evidence/verify_runtime_evidence.py \
  build/runtime-evidence/<release>/manifest.json \
  --expected-commit "$RELEASE_GIT_COMMIT" \
  --expected-digest "$RELEASE_IMAGE_DIGEST" \
  --expected-reference "$RELEASE_REFERENCE" \
  --require-go-live-ready
```

The command must exit zero. Attach the verified manifest, summary, release evidence, change-window gate evidence, migration log, vulnerability reports, and approvals to `docs/templates/PRODUCTION_GO_LIVE_SIGNOFF.md`.

## Failure handling

- `FAIL`: fix the cause and rerun that evidence group against the same unchanged candidate, or build a new candidate and invalidate all previous evidence.
- `NOT_RUN`: evidence is incomplete; production is NO-GO.
- Hash mismatch: quarantine the bundle and investigate tampering or accidental modification.
- Release identity mismatch: discard the bundle; never manually edit identity fields.
- Secret detected in evidence: revoke/rotate it, delete the bundle from artifact storage, and repeat the drill with redaction fixed.

## Retention

Retain final evidence and sign-off for at least the organization’s regulatory/audit retention period. CI defaults to 365 days; authoritative long-term storage must be immutable object storage with retention lock and access audit logs.
