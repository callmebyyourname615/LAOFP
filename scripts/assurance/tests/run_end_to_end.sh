#!/usr/bin/env bash
set -Eeuo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
export RELEASE_REFERENCE=phase58-synthetic RELEASE_GIT_COMMIT="$(printf 'a%.0s' {1..40})" RELEASE_IMAGE_DIGEST="sha256:$(printf 'b%.0s' {1..64})" ASSURANCE_ROOT="$TMP/evidence" ASSURANCE_NOW=2026-06-22T02:00:00Z
python3 scripts/assurance/tests/generate_synthetic_inputs.py --output "$TMP/inputs" --release-reference "$RELEASE_REFERENCE" --git-commit "$RELEASE_GIT_COMMIT" --image-digest "$RELEASE_IMAGE_DIGEST"
mkdir -p "$TMP/bin"; cat > "$TMP/bin/cosign" <<'COSIGN'
#!/usr/bin/env bash
set -Eeuo pipefail
case "$1" in
 sign-blob) while [[ $# -gt 0 ]]; do [[ "$1" == --output-signature ]] && { out="$2"; shift 2; continue; }; shift; done; printf synthetic-signature > "$out";;
 verify-blob) echo '{"verified":true}';;
 *) exit 2;; esac
COSIGN
chmod +x "$TMP/bin/cosign"; export PATH="$TMP/bin:$PATH" ASSURANCE_COSIGN_KEY_REF=fake-key ASSURANCE_COSIGN_PUBLIC_KEY_REF=fake-pub
run(){ local phase="$1" domain="$2" var="$3" file="$4"; echo "[$(date -u +%H:%M:%S)] RUN $phase"; export ASSURANCE_ENVIRONMENT="$domain" "$var=$file"; scripts/assurance/run_phase58_assurance.sh "$phase"; echo "[$(date -u +%H:%M:%S)] PASS $phase"; }
run 58A regulatory REGULATORY_SUBMISSION_SNAPSHOT "$TMP/inputs/58a.json"
run 58B operations PARTICIPANT_GOVERNANCE_SNAPSHOT "$TMP/inputs/58b.json"
run 58C security CRYPTO_AGILITY_SNAPSHOT "$TMP/inputs/58c.json"
run 58D compliance PRIVACY_ASSURANCE_SNAPSHOT "$TMP/inputs/58d.json"
run 58E security DECISION_GOVERNANCE_SNAPSHOT "$TMP/inputs/58e.json"
run 58F operations ISO20022_LIFECYCLE_SNAPSHOT "$TMP/inputs/58f.json"
run 58G financial-control SETTLEMENT_RISK_SNAPSHOT "$TMP/inputs/58g.json"
run 58H simulation DIGITAL_TWIN_SNAPSHOT "$TMP/inputs/58h.json"
run 58I compliance THIRD_PARTY_RISK_SNAPSHOT "$TMP/inputs/58i.json"
run 58J regulatory SUPERVISORY_READINESS_INPUT "$TMP/inputs/58j.json"
scripts/assurance/run_phase58_assurance.sh status | grep -q '58J: PASS'
echo 'Phase 58 synthetic end-to-end PASS'
