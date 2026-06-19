#!/usr/bin/env bash
set -Eeuo pipefail

: "${DB_URL:?DB_URL required}"
: "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
: "${ENVIRONMENT:?ENVIRONMENT required}"
: "${CHANGE_TYPE:?CHANGE_TYPE required}"

EVIDENCE_FILE="${RELEASE_GATE_EVIDENCE_FILE:-build/release-gate-evidence.json}"
MAX_DECISION_AGE_MINUTES="${RELEASE_GATE_MAX_DECISION_AGE_MINUTES:-15}"

command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 69; }
command -v python3 >/dev/null 2>&1 || { echo "python3 is required" >&2; exit 69; }

[[ "${RELEASE_REFERENCE}" =~ ^[A-Za-z0-9._:/-]{3,160}$ ]] \
  || { echo "RELEASE_REFERENCE contains unsupported characters" >&2; exit 64; }
[[ "${ENVIRONMENT}" =~ ^[a-z0-9_-]{2,32}$ ]] \
  || { echo "ENVIRONMENT contains unsupported characters" >&2; exit 64; }
[[ "${CHANGE_TYPE}" =~ ^[A-Z0-9_-]{2,40}$ ]] \
  || { echo "CHANGE_TYPE contains unsupported characters" >&2; exit 64; }
[[ "${MAX_DECISION_AGE_MINUTES}" =~ ^[1-9][0-9]?$ ]] \
  || { echo "RELEASE_GATE_MAX_DECISION_AGE_MINUTES must be 1-99" >&2; exit 64; }

mkdir -p "$(dirname "${EVIDENCE_FILE}")"
umask 077

result="$({
  psql "${DB_URL}" -X --no-psqlrc -v ON_ERROR_STOP=1 -At \
    -v ref="${RELEASE_REFERENCE}" \
    -v env="${ENVIRONMENT}" \
    -v typ="${CHANGE_TYPE}" \
    -v max_age="${MAX_DECISION_AGE_MINUTES}" <<'SQL'
WITH active_window AS (
    SELECT id, window_name, starts_at, ends_at
    FROM release_change_window
    WHERE environment = :'env'
      AND change_type IN (:'typ', 'ALL')
      AND starts_at <= now()
      AND ends_at > now()
    ORDER BY starts_at DESC
    LIMIT 1
), active_hard_freeze AS (
    SELECT id, reason, starts_at, ends_at
    FROM release_freeze_period
    WHERE environment = :'env'
      AND severity = 'HARD'
      AND starts_at <= now()
      AND ends_at > now()
    ORDER BY starts_at DESC
    LIMIT 1
), approved_exception AS (
    SELECT exception_row.id, exception_row.freeze_period_id,
           exception_row.approved_by, exception_row.expires_at
    FROM release_freeze_exception exception_row
    JOIN active_hard_freeze freeze_row
      ON freeze_row.id = exception_row.freeze_period_id
    WHERE exception_row.release_reference = :'ref'
      AND exception_row.status = 'APPROVED'
      AND exception_row.expires_at > now()
    ORDER BY exception_row.created_at DESC
    LIMIT 1
), recent_decision AS (
    SELECT decision, reason, evaluated_by, evidence_hash, evaluated_at
    FROM release_gate_decision
    WHERE release_reference = :'ref'
      AND environment = :'env'
      AND change_type = :'typ'
      AND evaluated_at > now() - make_interval(mins => :'max_age'::int)
    ORDER BY evaluated_at DESC
    LIMIT 1
)
SELECT json_build_object(
    'schemaVersion', 1,
    'releaseReference', :'ref',
    'environment', :'env',
    'changeType', :'typ',
    'evaluatedAt', to_char(now() AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"'),
    'windowOpen', EXISTS (SELECT 1 FROM active_window),
    'windowName', (SELECT window_name FROM active_window),
    'hardFreezeActive', EXISTS (SELECT 1 FROM active_hard_freeze),
    'freezeReason', (SELECT reason FROM active_hard_freeze),
    'approvedException', EXISTS (SELECT 1 FROM approved_exception),
    'exceptionId', (SELECT id::text FROM approved_exception),
    'decision', (SELECT decision FROM recent_decision),
    'decisionReason', (SELECT reason FROM recent_decision),
    'evaluatedBy', (SELECT evaluated_by FROM recent_decision),
    'evidenceHash', (SELECT evidence_hash FROM recent_decision),
    'decisionAt', (SELECT to_char(evaluated_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.MS"Z"') FROM recent_decision)
)::text;
SQL
} 2>&1)" || {
  echo "Release governance query failed" >&2
  # psql output can contain infrastructure details; retain it only in the protected job log.
  printf '%s\n' "${result}" >&2
  exit 1
}

python3 - "${result}" "${EVIDENCE_FILE}" <<'PY'
import json
import re
import sys
from pathlib import Path

raw, output = sys.argv[1], Path(sys.argv[2])
try:
    evidence = json.loads(raw)
except json.JSONDecodeError as exc:
    raise SystemExit(f"release gate returned invalid JSON: {exc}")

problems = []
change_type = evidence.get("changeType")
window_open = evidence.get("windowOpen") is True
freeze_active = evidence.get("hardFreezeActive") is True
exception_approved = evidence.get("approvedException") is True

if freeze_active and not exception_approved:
    problems.append("a HARD release freeze is active and no approved unexpired exception exists")
if not window_open:
    if change_type != "EMERGENCY":
        problems.append("no approved release window is currently open")
    elif not exception_approved:
        problems.append("EMERGENCY change outside a window requires an approved freeze exception")
if evidence.get("decision") != "ALLOW":
    problems.append("no recent ALLOW decision is recorded")
if not re.fullmatch(r"[0-9a-f]{64}", evidence.get("evidenceHash") or ""):
    problems.append("ALLOW decision is not bound to a valid SHA-256 evidence hash")
if not evidence.get("evaluatedBy"):
    problems.append("ALLOW decision has no evaluator identity")

output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(evidence, indent=2, sort_keys=True) + "\n", encoding="utf-8")

if problems:
    for problem in problems:
        print(f"DENY: {problem}", file=sys.stderr)
    raise SystemExit(1)

print(
    "ALLOW: release governance gate passed "
    f"for {evidence['releaseReference']} ({evidence['environment']}/{change_type})"
)
PY
