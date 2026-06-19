#!/usr/bin/env bash
set -Eeuo pipefail
: "${IMAGE_REFERENCE:?IMAGE_REFERENCE required}"
: "${DRY_RUN_APP_ENV_FILE:?DRY_RUN_APP_ENV_FILE required}"
: "${EXPECTED_DB_URL:?EXPECTED_DB_URL required}"
[[ -f "$DRY_RUN_APP_ENV_FILE" && ! -L "$DRY_RUN_APP_ENV_FILE" ]] || { echo 'dry-run env file must be a regular file' >&2; exit 64; }
mode=$(stat -c '%a' "$DRY_RUN_APP_ENV_FILE" 2>/dev/null || stat -f '%Lp' "$DRY_RUN_APP_ENV_FILE")
(( 8#$mode <= 8#600 )) || { echo 'dry-run env file permissions must be 0600 or stricter' >&2; exit 64; }
python3 - "$DRY_RUN_APP_ENV_FILE" "$EXPECTED_DB_URL" <<'PY'
import pathlib,sys
path,expected=sys.argv[1:]
values={}
for raw in pathlib.Path(path).read_text().splitlines():
    line=raw.strip()
    if not line or line.startswith('#'): continue
    if '=' not in line: raise SystemExit('invalid env file line')
    k,v=line.split('=',1); values[k.strip()]=v.strip().strip('"\'')
if values.get('DB_URL')!=expected: raise SystemExit('dry-run app DB_URL mismatch')
if values.get('SPRING_PROFILES_ACTIVE')!='prod': raise SystemExit('dry-run app must use prod profile')
PY
name="switching-schema-validation-${RANDOM}-$$"
cleanup(){ docker rm -f "$name" >/dev/null 2>&1 || true; }
trap cleanup EXIT
cid=$(docker run -d --name "$name" --read-only --tmpfs /tmp:rw,noexec,nosuid,size=128m --env-file "$DRY_RUN_APP_ENV_FILE" "$IMAGE_REFERENCE")
started=false
for _ in $(seq 1 "${DRY_RUN_APP_STARTUP_WAIT_SECONDS:-180}"); do
  if ! docker inspect "$cid" >/dev/null 2>&1; then break; fi
  state=$(docker inspect -f '{{.State.Status}}' "$cid")
  if [[ "$state" == exited || "$state" == dead ]]; then docker logs "$cid" >&2; exit 1; fi
  if docker logs "$cid" 2>&1 | grep -q 'Started SwitchingApplication'; then started=true; break; fi
  sleep 1
done
[[ "$started" == true ]] || { docker logs "$cid" >&2; echo 'application did not reach Started state' >&2; exit 1; }
docker stop --time 30 "$cid" >/dev/null
