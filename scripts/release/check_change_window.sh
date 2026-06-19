#!/usr/bin/env bash
set -euo pipefail
: "${DB_URL:?DB_URL required}" "${RELEASE_REFERENCE:?RELEASE_REFERENCE required}" "${ENVIRONMENT:?ENVIRONMENT required}" "${CHANGE_TYPE:?CHANGE_TYPE required}"
allowed=$(psql "$DB_URL" -v ON_ERROR_STOP=1 -v ref="$RELEASE_REFERENCE" -v env="$ENVIRONMENT" -v typ="$CHANGE_TYPE" -Atc "SELECT EXISTS(SELECT 1 FROM release_gate_decision WHERE release_reference=:'ref' AND environment=:'env' AND change_type=:'typ' AND decision='ALLOW' AND evaluated_at>now()-interval '15 minutes')")
[[ "$allowed" == "t" ]] || { echo "release gate has no recent ALLOW decision" >&2; exit 1; }
