#!/usr/bin/env bash
set -Eeuo pipefail
: "${DB_URL:?DB_URL required}"; : "${DB_USERNAME:?DB_USERNAME required}"; : "${DB_PASSWORD:?DB_PASSWORD required}"
: "${FLYWAY_USERNAME:?FLYWAY_USERNAME required}"; : "${OUTPUT:?OUTPUT required}"
command -v psql >/dev/null 2>&1 || { echo 'psql is required' >&2; exit 127; }
uri="${DB_URL#jdbc:}"
export PGPASSWORD="$DB_PASSWORD" PGAPPNAME="switching-phase55-grant-audit"
mkdir -p "$(dirname "$OUTPUT")"
psql "$uri" -X -v ON_ERROR_STOP=1 -At \
  -v app_user="$DB_USERNAME" -v flyway_user="$FLYWAY_USERNAME" \
  -c "SELECT json_build_object(
    'schemaVersion',1,
    'applicationUser', :'app_user',
    'flywayUser', :'flyway_user',
    'applicationUserIsSuperuser', COALESCE((SELECT rolsuper FROM pg_roles WHERE rolname=:'app_user'), false),
    'flywayUserIsSuperuser', COALESCE((SELECT rolsuper FROM pg_roles WHERE rolname=:'flyway_user'), false),
    'applicationUserOwnsObjects', EXISTS(
       SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
       WHERE pg_get_userbyid(c.relowner)=:'app_user' AND n.nspname NOT IN ('pg_catalog','information_schema')
    ),
    'applicationUserCanCreateInPublic', has_schema_privilege(:'app_user','public','CREATE'),
    'flywayUserCanCreateInPublic', has_schema_privilege(:'flyway_user','public','CREATE'),
    'applicationUserHasDdl',
       COALESCE((SELECT rolcreatedb OR rolcreaterole OR rolsuper FROM pg_roles WHERE rolname=:'app_user'), false)
       OR has_schema_privilege(:'app_user','public','CREATE')
       OR EXISTS(
          SELECT 1 FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
          WHERE pg_get_userbyid(c.relowner)=:'app_user' AND n.nspname NOT IN ('pg_catalog','information_schema')
       )
  );" > "$OUTPUT"
python3 - "$OUTPUT" <<'PY'
import json, pathlib, sys
p=pathlib.Path(sys.argv[1]); data=json.loads(p.read_text())
errors=[]
if data.get('applicationUserHasDdl'): errors.append('application user retains DDL capability')
if data.get('applicationUserOwnsObjects'): errors.append('application user owns database objects')
if data.get('applicationUserCanCreateInPublic'): errors.append('application user can CREATE in public schema')
if not data.get('flywayUserCanCreateInPublic'): errors.append('Flyway user cannot CREATE in public schema')
if data.get('applicationUser')==data.get('flywayUser'): errors.append('application and Flyway users are not separated')
if errors: raise SystemExit('; '.join(errors))
PY
