# 18.07 Certificate Issue Endpoint

Status: PASS

Endpoints verified:

- `POST /v1/participants/{pspId}/certificates/issue` signed a fresh CSR for an inactive UAT participant.
- The response returned valid issuer, subject, serial, file name, and expiry metadata.
- `GET /v1/participants/certificates` returned the certificate inventory with active and revoked lifecycle states.
- The certificate PEM was redacted from evidence; temporary local private key and CSR were deleted after the test.

Conclusion: Portal/API certificate issuance passed on UAT without retaining sensitive private material.
