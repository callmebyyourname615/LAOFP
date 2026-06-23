#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cd "$PHASE64_ROOT"
phase64_setup "64C" "Runtime evidence acquisition and chain of custody"
STATUS=FAIL; MESSAGE="runtime evidence acquisition failed"
trap 'code=$?; phase64_finalize "$STATUS" "$code" "$MESSAGE"' EXIT
phase64_require_file scripts/evidence/run_runtime_evidence.sh
phase64_require_file scripts/evidence/verify_runtime_evidence.py
if phase64_is_preflight; then
  phase64_run "compile runtime evidence helpers" python3 -m py_compile \
    scripts/evidence/build_runtime_evidence.py scripts/evidence/verify_runtime_evidence.py
  phase64_run "validate runtime evidence runner syntax" bash -n scripts/evidence/run_runtime_evidence.sh
  STATUS=PREPARED; MESSAGE="runtime evidence import/execution and chain-of-custody tooling are ready"; exit 0
fi
phase64_require_release_identity
mode="${PHASE64_RUNTIME_MODE:-import}"
case "$mode" in
  import)
    : "${RUNTIME_EVIDENCE_MANIFEST:?RUNTIME_EVIDENCE_MANIFEST is required in import mode}"
    phase64_require_file "$RUNTIME_EVIDENCE_MANIFEST"
    source_dir="$(cd "$(dirname "$RUNTIME_EVIDENCE_MANIFEST")" && pwd)"
    ;;
  execute)
    export EVIDENCE_ENVIRONMENT=uat
    export RELEASE_IMAGE_DIGEST="$APPLICATION_IMAGE_DIGEST"
    export EVIDENCE_DIR="$PHASE64_PHASE_DIR/runtime-execution"
    phase64_run "execute complete runtime evidence plan" scripts/evidence/run_runtime_evidence.sh full
    source_dir="$EVIDENCE_DIR"
    ;;
  *) phase64_log "ERROR PHASE64_RUNTIME_MODE must be import or execute"; exit 64 ;;
esac
phase64_copy_tree "$source_dir" "$PHASE64_PHASE_DIR/runtime"
manifest="$PHASE64_PHASE_DIR/runtime/manifest.json"
phase64_require_file "$manifest"
phase64_run "verify copied runtime evidence hashes" python3 scripts/evidence/verify_runtime_evidence.py "$manifest" \
  --require-go-live-ready --expected-commit "$RELEASE_GIT_COMMIT" \
  --expected-digest "$APPLICATION_IMAGE_DIGEST" --expected-reference "$RELEASE_REFERENCE"
phase64_run "verify runtime release identity" python3 scripts/phase64/verify_release_identity.py \
  --kind runtime --manifest "$manifest" --reference "$RELEASE_REFERENCE" --commit "$RELEASE_GIT_COMMIT" \
  --application-digest "$APPLICATION_IMAGE_DIGEST"
python3 - "$PHASE64_RUN_DIR/64B/phase61/manifest.json" "$manifest" "$PHASE64_PHASE_DIR/chain-of-custody.json" <<'PY'
import hashlib, json, pathlib, sys
p61, runtime, output = map(pathlib.Path, sys.argv[1:])
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()
doc={"schemaVersion":1,"phase61Manifest":{"path":str(p61),"sha256":sha(p61)},"runtimeManifest":{"path":str(runtime),"sha256":sha(runtime)}}
output.write_text(json.dumps(doc,indent=2,sort_keys=True)+"\n",encoding="utf-8")
PY
printf '%s\n' "$manifest" > "$PHASE64_PHASE_DIR/runtime-manifest.path"
STATUS=PASS; MESSAGE="runtime evidence is hash-valid, Go-Live-ready and release-bound"
