#!/usr/bin/env bash
set -Eeuo pipefail
: "${DB_URL:?DB_URL required}" "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}" "${ENVIRONMENT:?ENVIRONMENT required}"
command -v psql >/dev/null 2>&1 || { echo "psql is required" >&2; exit 69; }

updated="$(psql "${DB_URL}" -X --no-psqlrc -v ON_ERROR_STOP=1 -At \
  -v ref="${RELEASE_REFERENCE}" -v env="${ENVIRONMENT}" <<'SQL'
WITH active_freeze AS (
    SELECT id FROM release_freeze_period
    WHERE environment = :'env' AND severity = 'HARD'
      AND starts_at <= now() AND ends_at > now()
), consumed AS (
    UPDATE release_freeze_exception exception_row
       SET status = 'USED'
      FROM active_freeze freeze_row
     WHERE exception_row.freeze_period_id = freeze_row.id
       AND exception_row.release_reference = :'ref'
       AND exception_row.status = 'APPROVED'
       AND exception_row.expires_at > now()
    RETURNING exception_row.id
)
SELECT count(*) FROM consumed;
SQL
)"

echo "Consumed ${updated} approved freeze exception(s) for release ${RELEASE_REFERENCE}."
