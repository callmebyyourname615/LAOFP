#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE66_ROOT"
phase66_setup "66D" "Migration and data-integrity certification"
STATUS="FAIL"; MESSAGE="migration/data certification failed"
trap 'code=$?; phase66_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
extra=()
phase66_is_full && extra+=(--database-if-configured)
phase66_run "repository migration inventory" python3 scripts/phase66/migration_data_certification.py \
  --root . --output "$PHASE66_PHASE_DIR/migration-data.json" "${extra[@]}"
if phase66_is_full; then
  phase66_require_uat
  [[ -n "${DB_URL:-}" && -n "${DB_USERNAME:-}" && -n "${DB_PASSWORD:-}" ]] || { phase66_log "ERROR DB_URL/DB_USERNAME/DB_PASSWORD required"; exit 64; }
  phase66_run "execute integrity SQL" bash -c 'PGPASSWORD="$DB_PASSWORD" psql "$DB_URL" -U "$DB_USERNAME" -v ON_ERROR_STOP=1 -f sql/phase66/data-integrity-checks.sql --csv > "$1"' _ "$PHASE66_PHASE_DIR/data-integrity.csv"
  STATUS="PASS"; MESSAGE="migration inventory and live integrity queries completed"
else
  STATUS="PREPARED"; MESSAGE="repository migration inventory passed; live database checks require full mode"
fi
