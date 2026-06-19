#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A
: "${RELEASE_IMAGE_REPOSITORY:?RELEASE_IMAGE_REPOSITORY is required}"
IMAGE_REF="${RELEASE_IMAGE_REPOSITORY}@${RELEASE_IMAGE_DIGEST}"
phase_begin 54B "Migration Certification"
failed=0
run_check migration-source-contract python3 scripts/verify_phase53b_schema_alignment.py || failed=1
run_check migration-testcontainers ./mvnw --batch-mode --no-transfer-progress \
  -Dtest=MigrationApplicationIntegrationTest,V83PayloadSha256SchemaAlignmentIntegrationTest,V83CleanInstallCertificationIntegrationTest test || failed=1
run_check migration-image-clean-and-existing scripts/verify_migration_image.sh "$IMAGE_REF" 83 || failed=1
printf '83\n' > "$PHASE_DIR/flyway-version.txt"
python3 - "$PHASE_DIR/migration-summary.json" "$IMAGE_REF" "$failed" <<'PY'
import json, pathlib, sys
out, image, failed = sys.argv[1:]
doc={"schemaVersion":1,"imageReference":image,"cleanInstall":{"from":"empty","to":"83"},"upgrade":{"from":"82","to":"83"},"flywayValidation":failed=="0","dataPreservation":failed=="0","transactionalRollback":failed=="0","passed":failed=="0"}
pathlib.Path(out).write_text(json.dumps(doc,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
