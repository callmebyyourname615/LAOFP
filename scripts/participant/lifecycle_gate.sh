#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}"
: "${PARTICIPANT_CODE:?PARTICIPANT_CODE required}"
psql "$DB_URL" -v ON_ERROR_STOP=1 -Atc "select count(*) from participant_lifecycle_case where participant_code='${PARTICIPANT_CODE}' and status='APPROVED' and effective_at <= now()" | grep -qx '1'
