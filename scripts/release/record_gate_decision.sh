#!/usr/bin/env bash
set -Eeuo pipefail

: "${DB_URL:?DB_URL required}"
: "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}"
: "${ENVIRONMENT:?ENVIRONMENT required}"
: "${CHANGE_TYPE:?CHANGE_TYPE required}"
: "${DECISION:?DECISION required (ALLOW or DENY)}"
: "${DECISION_REASON:?DECISION_REASON required}"
: "${EVALUATED_BY:?EVALUATED_BY required}"
: "${RELEASE_EVIDENCE_FILE:?RELEASE_EVIDENCE_FILE required}"

[[ "${DECISION}" == "ALLOW" || "${DECISION}" == "DENY" ]] \
  || { echo "DECISION must be ALLOW or DENY" >&2; exit 64; }
[[ -f "${RELEASE_EVIDENCE_FILE}" && ! -L "${RELEASE_EVIDENCE_FILE}" ]] \
  || { echo "RELEASE_EVIDENCE_FILE must be a regular non-symlink file" >&2; exit 66; }
[[ ${#DECISION_REASON} -ge 12 && ${#DECISION_REASON} -le 1000 ]] \
  || { echo "DECISION_REASON must be 12-1000 characters" >&2; exit 64; }

command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 69; }
command -v sha256sum >/dev/null 2>&1 || { echo "sha256sum is required" >&2; exit 69; }

id="$(python3 - <<'PY'
import uuid
print(uuid.uuid4())
PY
)"
evidence_hash="$(sha256sum "${RELEASE_EVIDENCE_FILE}" | awk '{print $1}')"

psql "${DB_URL}" -X --no-psqlrc -v ON_ERROR_STOP=1 \
  -v id="${id}" -v ref="${RELEASE_REFERENCE}" -v env="${ENVIRONMENT}" \
  -v typ="${CHANGE_TYPE}" -v decision="${DECISION}" \
  -v reason="${DECISION_REASON}" -v evaluator="${EVALUATED_BY}" \
  -v evidence_hash="${evidence_hash}" <<'SQL'
INSERT INTO release_gate_decision (
    id, release_reference, environment, change_type, decision,
    reason, evaluated_by, evidence_hash, evaluated_at
) VALUES (
    :'id'::uuid, :'ref', :'env', :'typ', :'decision',
    :'reason', :'evaluator', :'evidence_hash', now()
);
SQL

echo "Recorded ${DECISION} release-gate decision id=${id} evidence_sha256=${evidence_hash}"
