#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STAMP="${STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
EVIDENCE_DIR="${EVIDENCE_DIR:-runtime-evidence/prod-readiness-${STAMP}}"
GIT_COMMIT="${GIT_COMMIT:-$(git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || echo unknown)}"
ENVIRONMENT="${ENVIRONMENT:-uat}"
BASE_URL="${BASE_URL:-https://175.11.0.200}"

cd "$ROOT_DIR"

mkdir -p "$EVIDENCE_DIR"/{01-deployment-state,02-auth-security,03-payment-happy-path,04-payment-failure-retry,05-refund-reversal,06-drs-matrix,07-settlement,08-outbox-recovery,09-config-hardcode-check,10-observability}

cat > "$EVIDENCE_DIR/00-manifest.json" <<JSON
{
  "schemaVersion": 1,
  "evidenceType": "production-readiness",
  "environment": "$ENVIRONMENT",
  "baseUrl": "$BASE_URL",
  "generatedAt": "$STAMP",
  "gitCommit": "$GIT_COMMIT",
  "productionReady": false,
  "verdict": "NOT_READY_FOR_PRODUCTION",
  "sections": [
    {"id": "01-deployment-state", "status": "NOT_RUN", "required": true},
    {"id": "02-auth-security", "status": "NOT_RUN", "required": true},
    {"id": "03-payment-happy-path", "status": "NOT_RUN", "required": true},
    {"id": "04-payment-failure-retry", "status": "NOT_RUN", "required": true},
    {"id": "05-refund-reversal", "status": "NOT_RUN", "required": true},
    {"id": "06-drs-matrix", "status": "NOT_RUN", "required": true},
    {"id": "07-settlement", "status": "NOT_RUN", "required": true},
    {"id": "08-outbox-recovery", "status": "NOT_RUN", "required": true},
    {"id": "09-config-hardcode-check", "status": "NOT_RUN", "required": true},
    {"id": "10-observability", "status": "NOT_RUN", "required": true}
  ],
  "passCriteria": {
    "productionReadyRequires": [
      "All required sections PASS",
      "Operations health UP",
      "No unresolved failed outbox events",
      "Security negative tests reject unauthenticated and unauthorized access",
      "Runtime config values are externalized from environment or config",
      "Audit, trace, requestId, and deployment identity are captured"
    ]
  }
}
JSON

cat > "$EVIDENCE_DIR/11-score.json" <<JSON
{
  "scope": "production readiness evidence bundle",
  "totalSections": 10,
  "passed": 0,
  "warnings": 0,
  "failed": 0,
  "blocked": 0,
  "notRun": 10,
  "scorePercent": 0,
  "productionReady": false,
  "verdict": "NOT_READY_FOR_PRODUCTION"
}
JSON

cat > "$EVIDENCE_DIR/SUMMARY.md" <<'MD'
# Production Readiness Evidence

Status: NOT_READY_FOR_PRODUCTION

This bundle is the evidence workspace for the production readiness assessment. Each section must contain raw command output/API responses plus a short `RESULT.md` stating PASS/WARN/FAIL and the decision reason.

## Required Sections

| Section | Status | Required Evidence |
| --- | --- | --- |
| 01-deployment-state | NOT_RUN | deployed jar/image checksum, git commit, compose/container state, health/readiness |
| 02-auth-security | NOT_RUN | mTLS required, no-cert rejected, bad cert rejected, JWT required, RBAC denied, audit event |
| 03-payment-happy-path | NOT_RUN | inquiry, transfer, final status, trace, source view, audit, settlement impact |
| 04-payment-failure-retry | NOT_RUN | connector down, pending transfer, retry rounds, status enquiry, final recovery |
| 05-refund-reversal | NOT_RUN | original success, refund/reversal request, approval/reject path, ledger/settlement effect |
| 06-drs-matrix | NOT_RUN | pre/post-settlement dispute, no-action, refund-required, reject, maker/checker controls |
| 07-settlement | NOT_RUN | cycle creation, batch/instructions, approval, close/settle, reconciliation |
| 08-outbox-recovery | NOT_RUN | failed outbox inventory, retry/discard/review, operations health returns UP |
| 09-config-hardcode-check | NOT_RUN | env/config proof for secrets, DB, MinIO, fees, promotions, limits, retry policies |
| 10-observability | NOT_RUN | logs, requestId/traceId, audit logs, metrics/health, diagnostic commands |

## Current Priority

Start with `08-outbox-recovery` because production readiness is blocked while `/api/operations/health` is `DEGRADED` due failed outbox events.
MD

for section in 01-deployment-state 02-auth-security 03-payment-happy-path 04-payment-failure-retry 05-refund-reversal 06-drs-matrix 07-settlement 08-outbox-recovery 09-config-hardcode-check 10-observability; do
  cat > "$EVIDENCE_DIR/$section/RESULT.md" <<MD
# $section

Status: NOT_RUN

## Evidence Files

- Add raw JSON/TXT command outputs here.

## Decision

Pending.
MD
done

find "$EVIDENCE_DIR" -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > "$EVIDENCE_DIR/SHA256SUMS"

echo "$EVIDENCE_DIR"
