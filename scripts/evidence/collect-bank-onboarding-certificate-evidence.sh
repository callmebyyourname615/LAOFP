#!/usr/bin/env bash
set -euo pipefail

TARGET="${TARGET:-https://175.11.0.200}"
CLIENT_CERT="${CLIENT_CERT:-$HOME/sundaybank-client.crt}"
CLIENT_KEY="${CLIENT_KEY:-$HOME/sundaybank-client.key}"
CA_CERT="${CA_CERT:-$HOME/uat-ca.crt}"
ISSUER_CA_CERT="${ISSUER_CA_CERT:-$HOME/issuer-ca.crt}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Set ADMIN_PASSWORD for the UAT admin account}"
STAMP="${STAMP:-$(date -u +%Y%m%dT%H%M%SZ)}"
EVDIR="${EVDIR:-runtime-evidence/prod-readiness-20260713Tuat01/14-bank-onboarding-certificates}"

for command in curl jq openssl shasum; do
  command -v "$command" >/dev/null 2>&1 || {
    echo "Missing required command: $command" >&2
    exit 1
  }
done

for file in "$CLIENT_CERT" "$CLIENT_KEY" "$CA_CERT" "$ISSUER_CA_CERT"; do
  [[ -f "$file" ]] || {
    echo "Missing mTLS file: $file" >&2
    exit 1
  }
done

mkdir -p "$EVDIR"
# A rerun must describe one complete scenario, not retain artifacts from a failed attempt.
rm -f "$EVDIR"/[0-9][0-9]-* "$EVDIR/RESULT.md" "$EVDIR/SHA256SUMS"
TMPDIR="$(mktemp -d)"
trap 'rm -rf "$TMPDIR"' EXIT

BANK_CODE="EVD${STAMP:2:12}"
CONNECTOR_NAME="MOCK_${BANK_CODE}_CONNECTOR"
ROUTE_CODE="ROUTE_SUNDAYBANK_TO_${BANK_CODE}_PACS_008_EVIDENCE"

api() {
  curl -k -sS "$@" \
    --cert "$CLIENT_CERT" \
    --key "$CLIENT_KEY" \
    --cacert "$CA_CERT"
}

LOGIN_RESPONSE=$(api -X POST "$TARGET/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}")
TOKEN=$(jq -r '.accessToken // empty' <<<"$LOGIN_RESPONSE")
[[ -n "$TOKEN" ]] || {
  echo 'Admin login failed; access token was not issued.' >&2
  jq . <<<"$LOGIN_RESPONSE" >&2 || true
  exit 1
}

printf '%s\n' '{"status":"LOGIN_SUCCESS","tokenStored":false}' \
  | jq . \
  | tee "$EVDIR/01-admin-login-redacted.json"

cat > "$TMPDIR/onboard.json" <<EOF
{
  "bankCode": "$BANK_CODE",
  "bankName": "UAT Evidence Bank $STAMP",
  "participantType": "DIRECT",
  "participantStatus": "INACTIVE",
  "country": "LA",
  "currency": "LAK",
  "connectorName": "$CONNECTOR_NAME",
  "connectorType": "MOCK",
  "timeoutMs": 5000,
  "connectorEnabled": false,
  "forceReject": false,
  "sourceBank": "SUNDAYBANK",
  "messageType": "PACS_008",
  "routeCode": "$ROUTE_CODE",
  "priority": 99,
  "routeEnabled": false
}
EOF

api -X POST "$TARGET/api/operations/bank-onboarding" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "@$TMPDIR/onboard.json" \
  | jq . \
  | tee "$EVDIR/02-bank-onboarding.json"

jq -e --arg bank "$BANK_CODE" \
  '.bankCode == $bank and .participantCreated == true and .connectorCreated == true and .routingRuleCreated == true' \
  "$EVDIR/02-bank-onboarding.json" >/dev/null

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TMPDIR/$BANK_CODE.key" >/dev/null 2>&1
openssl req -new \
  -key "$TMPDIR/$BANK_CODE.key" \
  -out "$TMPDIR/$BANK_CODE.csr" \
  -subj "/C=LA/O=LAOFP/OU=UAT Evidence/CN=${BANK_CODE}_MTLS"

jq -n --rawfile csr "$TMPDIR/$BANK_CODE.csr" '{csrPem: $csr}' > "$TMPDIR/issue.json"

api -X POST "$TARGET/v1/participants/$BANK_CODE/certificates/issue" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "@$TMPDIR/issue.json" \
  | jq . \
  | tee "$EVDIR/03-certificate-issued.json"

jq -er '.certPem' "$EVDIR/03-certificate-issued.json" > "$TMPDIR/$BANK_CODE.crt"
# The issuer sets notBefore at second precision; allow that second to elapse before verification.
sleep 3
openssl verify -CAfile "$ISSUER_CA_CERT" "$TMPDIR/$BANK_CODE.crt" \
  | tee "$EVDIR/04-issued-certificate-openssl-verify.txt"

jq -n --rawfile cert "$TMPDIR/$BANK_CODE.crt" '{certPem: $cert}' > "$TMPDIR/register.json"
api -X POST "$TARGET/v1/participants/$BANK_CODE/certificates/register" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "@$TMPDIR/register.json" \
  | jq . \
  | tee "$EVDIR/05-certificate-registered.json"

CERT_ID=$(jq -r '.certId' "$EVDIR/05-certificate-registered.json")
[[ -n "$CERT_ID" && "$CERT_ID" != null ]] || {
  echo 'Certificate registration did not return certId.' >&2
  exit 1
}

api "$TARGET/v1/participants/certificates" \
  -H "Authorization: Bearer $TOKEN" \
  | jq --arg bank "$BANK_CODE" 'map(select(.pspId == $bank))' \
  | tee "$EVDIR/06-certificate-inventory-active.json"

jq -e 'length == 1 and .[0].status == "ACTIVE"' "$EVDIR/06-certificate-inventory-active.json" >/dev/null

curl -k -sS -o "$EVDIR/07-mtls-registered-operations-health.json" \
  -w '%{http_code}\n' \
  "$TARGET/api/operations/health" \
  --cert "$TMPDIR/$BANK_CODE.crt" \
  --key "$TMPDIR/$BANK_CODE.key" \
  --cacert "$CA_CERT" \
  -H "Authorization: Bearer $TOKEN" \
  | tee "$EVDIR/07-mtls-registered-operations-health.status.txt"

[[ "$(cat "$EVDIR/07-mtls-registered-operations-health.status.txt")" == "200" ]] || {
  echo 'Newly registered mTLS certificate was not accepted.' >&2
  cat "$EVDIR/07-mtls-registered-operations-health.json" >&2
  exit 1
}

api -X DELETE "$TARGET/v1/participants/$BANK_CODE/certificates/$CERT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -o "$EVDIR/08-certificate-revoked.body.txt" \
  -w '%{http_code}\n' \
  | tee "$EVDIR/08-certificate-revoked.status.txt"

[[ "$(cat "$EVDIR/08-certificate-revoked.status.txt")" == "204" ]] || {
  echo 'Certificate revocation did not return HTTP 204.' >&2
  exit 1
}

api "$TARGET/v1/participants/certificates" \
  -H "Authorization: Bearer $TOKEN" \
  | jq --arg cert "$CERT_ID" 'map(select(.certId == $cert))' \
  | tee "$EVDIR/09-certificate-inventory-revoked.json"

jq -e 'length == 1 and .[0].status == "REVOKED"' "$EVDIR/09-certificate-inventory-revoked.json" >/dev/null

curl -k -sS -o "$EVDIR/10-mtls-revoked-operations-health.json" \
  -w '%{http_code}\n' \
  "$TARGET/api/operations/health" \
  --cert "$TMPDIR/$BANK_CODE.crt" \
  --key "$TMPDIR/$BANK_CODE.key" \
  --cacert "$CA_CERT" \
  -H "Authorization: Bearer $TOKEN" \
  | tee "$EVDIR/10-mtls-revoked-operations-health.status.txt"

[[ "$(cat "$EVDIR/10-mtls-revoked-operations-health.status.txt")" == "401" ]] || {
  echo 'Revoked mTLS certificate was not rejected with HTTP 401.' >&2
  cat "$EVDIR/10-mtls-revoked-operations-health.json" >&2
  exit 1
}

cat > "$EVDIR/RESULT.md" <<EOF
# 14 Bank Onboarding / Certificate Result

Status: PASS
Target: $TARGET
Test bank: $BANK_CODE (INACTIVE)

Evidence:
- A disabled test participant, connector, and route were created through bank onboarding.
- A bank-generated CSR was issued as a client-auth certificate by the UAT issuer CA.
- The certificate was verified, registered, and accepted by mTLS on a protected operations endpoint.
- The certificate was revoked and its subsequent protected mTLS request was rejected with HTTP 401.
EOF

find "$EVDIR" -type f ! -name SHA256SUMS -print0 \
  | sort -z \
  | xargs -0 shasum -a 256 \
  > "$EVDIR/SHA256SUMS"

echo "PASS: Bank onboarding and certificate evidence collected in $EVDIR"
