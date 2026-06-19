#!/usr/bin/env bash
set -Eeuo pipefail
source "$(dirname "$0")/common.sh"
cert_require_release_identity
require_phase_pass 54A
: "${RELEASE_IMAGE_REPOSITORY:?RELEASE_IMAGE_REPOSITORY is required}"
IMAGE_REF="${RELEASE_IMAGE_REPOSITORY}@${RELEASE_IMAGE_DIGEST}"
phase_begin 54H "Security & Supply Chain Certification"
failed=0
for tool in gitleaks trivy syft cosign; do cert_require_command "$tool"; done
run_check gitleaks gitleaks detect --source . --no-banner --redact --report-format json --report-path "$PHASE_DIR/gitleaks.json" || failed=1
run_check owasp-dependency-check ./mvnw --batch-mode --no-transfer-progress -DskipTests org.owasp:dependency-check-maven:12.2.2:check -DfailBuildOnCVSS=7 -Dformats=JSON,HTML || failed=1
copy_if_present target/dependency-check-report.json "$PHASE_DIR/dependency-check-report.json"
copy_if_present target/dependency-check-report.html "$PHASE_DIR/dependency-check-report.html"
run_check trivy-image trivy image --scanners vuln,secret,misconfig --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 --format json --output "$PHASE_DIR/trivy-image.json" "$IMAGE_REF" || failed=1
run_check sbom syft "$IMAGE_REF" -o "spdx-json=$PHASE_DIR/sbom.spdx.json" || failed=1
identity="${COSIGN_CERTIFICATE_IDENTITY_REGEXP:-^https://github.com/.+/.github/workflows/.+@refs/heads/main$}"
issuer="https://token.actions.githubusercontent.com"
run_check image-signature cosign verify --certificate-identity-regexp="$identity" --certificate-oidc-issuer="$issuer" "$IMAGE_REF" || failed=1
cp "$PHASE_LOG_DIR/image-signature.log" "$PHASE_DIR/image-signature-verification.txt"
run_check sbom-attestation cosign verify-attestation --type spdxjson --certificate-identity-regexp="$identity" --certificate-oidc-issuer="$issuer" "$IMAGE_REF" || failed=1
run_check provenance-attestation cosign verify-attestation --type slsaprovenance --certificate-identity-regexp="$identity" --certificate-oidc-issuer="$issuer" "$IMAGE_REF" || failed=1
cp "$PHASE_LOG_DIR/provenance-attestation.log" "$PHASE_DIR/provenance-verification.txt"
python3 - "$PHASE_DIR/security-summary.json" "$IMAGE_REF" "$PHASE_CHECKS_FILE" <<'PY'
import json
import pathlib
import sys

out, image, checks_file = sys.argv[1:]
checks = {
    row["id"]: row["status"]
    for row in (
        json.loads(line)
        for line in pathlib.Path(checks_file).read_text(encoding="utf-8").splitlines()
        if line.strip()
    )
}
required = [
    "gitleaks",
    "owasp-dependency-check",
    "trivy-image",
    "sbom",
    "image-signature",
    "sbom-attestation",
    "provenance-attestation",
]
document = {
    "schemaVersion": 1,
    "imageReference": image,
    "checks": checks,
    "sbomPresent": pathlib.Path(out).with_name("sbom.spdx.json").is_file(),
    "signatureVerified": checks.get("image-signature") == "PASS",
    "sbomAttestationVerified": checks.get("sbom-attestation") == "PASS",
    "provenanceVerified": checks.get("provenance-attestation") == "PASS",
    "passed": all(checks.get(name) == "PASS" for name in required),
}
pathlib.Path(out).write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8")
PY
write_phase_result "$([[ $failed -eq 0 ]] && echo PASS || echo FAIL)"
