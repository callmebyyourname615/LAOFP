#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A 54B 54C 54D 54E 54F 54G 54H 54I
[[ "$CERTIFICATION_ENVIRONMENT" == uat ]] || cert_die "54J Go-Live rehearsal must run in UAT"
[[ "${GOLIVE_REHEARSAL_CONFIRMATION:-}" == I_UNDERSTAND_THIS_RUNS_CANARY_PROMOTION_AND_ROLLBACK ]] || cert_die "invalid GOLIVE_REHEARSAL_CONFIRMATION"
: "${RELEASE_IMAGE_REPOSITORY:?RELEASE_IMAGE_REPOSITORY is required}"
: "${PROMETHEUS_URL:?PROMETHEUS_URL is required}"
cert_require_command kubectl

phase_begin 54J "Go-Live Rehearsal & Release Candidate"
failed=0
NAMESPACE="${UAT_NAMESPACE:-switching-uat}"
DEPLOYMENT="${UAT_DEPLOYMENT:-switching-api}"
CONTAINER="${UAT_CONTAINER:-switching-api}"
previous="$(kubectl -n "$NAMESPACE" get deployment "$DEPLOYMENT" -o "jsonpath={.spec.template.spec.containers[?(@.name=='$CONTAINER')].image}")"
[[ -n "$previous" ]] || cert_die "cannot determine previous UAT image"

run_check prerequisite-phases python3 scripts/certification/create_release_candidate.py \
  --root "$CERTIFICATION_ROOT" \
  --reference "$RELEASE_REFERENCE" \
  --git-commit "$RELEASE_GIT_COMMIT" \
  --image-digest "$RELEASE_IMAGE_DIGEST" \
  --through 54I \
  --output "$PHASE_DIR/prerequisite-candidate.json" || failed=1

export IMAGE_REPOSITORY="$RELEASE_IMAGE_REPOSITORY"
export IMAGE_DIGEST="$RELEASE_IMAGE_DIGEST"
export NAMESPACE
export CANARY_WEIGHTS="5 25 50"
export CANARY_STAGE_SECONDS="${CANARY_STAGE_SECONDS:-300}"
run_check canary-5-25-50-100 scripts/release/progressive-rollout.sh || failed=1
candidate="${RELEASE_IMAGE_REPOSITORY}@${RELEASE_IMAGE_DIGEST}"
python3 - "$PHASE_DIR/canary-summary.json" "$candidate" "$previous" "$failed" <<'PY'
import json, pathlib, sys
out, candidate, previous, failed = sys.argv[1:]
doc = {
    "schemaVersion": 1,
    "weights": [5, 25, 50, 100],
    "candidateImage": candidate,
    "previousImage": previous,
    "metricGates": "PASS" if failed == "0" else "FAIL",
    "passed": failed == "0",
}
pathlib.Path(out).write_text(json.dumps(doc, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY

run_check rollback-rehearsal bash -lc '
  ns="$1"; dep="$2"; container="$3"; previous="$4";
  kubectl -n "$ns" set image "deployment/$dep" "$container=$previous";
  kubectl -n "$ns" rollout status "deployment/$dep" --timeout=600s;
  actual=$(kubectl -n "$ns" get deployment "$dep" -o "jsonpath={.spec.template.spec.containers[?(@.name==\"$container\")].image}");
  test "$actual" = "$previous"' _ "$NAMESPACE" "$DEPLOYMENT" "$CONTAINER" "$previous" || failed=1
python3 - "$PHASE_DIR/rollback-summary.json" "$previous" "$failed" <<'PY'
import json, pathlib, sys
pathlib.Path(sys.argv[1]).write_text(
    json.dumps(
        {
            "schemaVersion": 1,
            "restoredImage": sys.argv[2],
            "databaseRollbackPerformed": False,
            "passed": sys.argv[3] == "0",
        },
        indent=2,
        sort_keys=True,
    ) + "\n",
    encoding="utf-8",
)
PY

run_check evidence-secret-scan python3 scripts/certification/scan_evidence.py \
  --root "$CERTIFICATION_ROOT" \
  --output "$PHASE_DIR/evidence-secret-scan.json" || failed=1

if (( failed )); then
  write_phase_result FAIL
  exit 1
fi

# Write the final 54J result before assembling the candidate so the candidate
# hashes the complete A-J decision set. Missing candidate files are also checked
# by the top-level certification manifest.
write_phase_result PASS
mkdir -p "$CERTIFICATION_ROOT/release-candidate"
python3 scripts/certification/create_release_candidate.py \
  --root "$CERTIFICATION_ROOT" \
  --reference "$RELEASE_REFERENCE" \
  --git-commit "$RELEASE_GIT_COMMIT" \
  --image-digest "$RELEASE_IMAGE_DIGEST" \
  --through 54J \
  --output "$CERTIFICATION_ROOT/release-candidate/manifest.json"
(
  cd "$CERTIFICATION_ROOT/release-candidate"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum manifest.json > checksums.sha256
  else
    shasum -a 256 manifest.json > checksums.sha256
  fi
)
