# POST /v1/participants/{pspId}/credentials/rotate — PASS

Tried against two disposable UAT-onboarded test participants first (EVD260716T01591,
UAT260716T02220) — both correctly rejected with 400 REQ-001 "No OAuth client registered for PSP"
since those were only ever onboarded for cert-lifecycle/connectivity tests, never given an OAuth
client. Good validation evidence.

Happy path confirmed against BANK_A (the standard synthetic participant used throughout this
evidence suite, not a real bank) — 200 with a freshly rotated clientId/clientSecret/pspId.
Secret redacted here per evidence rules (never store credentials in evidence).

Note: `expiresAt` was null in the response — worth checking whether rotated OAuth secrets are
meant to have a TTL/expiry at all, or whether that's expected to stay null until an expiry
policy is implemented.
