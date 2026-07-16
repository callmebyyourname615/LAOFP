# 17.03 Certificate Lifecycle

Status: PASS

Evidence:
- A fresh participant CSR was signed through the certificate-issuance API.
- The issued certificate was rejected before registration with LFP-2002.
- After registration, the certificate successfully accessed a protected mTLS API.
- After revocation, the same certificate was immediately rejected with HTTP 401 and LFP-2002.

Conclusion:
Certificate issuance, registration, active use, and immediate revocation controls passed on UAT.
