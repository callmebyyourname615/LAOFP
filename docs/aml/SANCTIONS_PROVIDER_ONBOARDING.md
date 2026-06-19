# Sanctions Provider Onboarding

This checklist applies to BoL/FIU, OFAC, UN and future approved providers.

## Contract

- HTTPS with hostname verification and controlled trust roots.
- Documented ownership, update frequency, maximum age and minimum record count.
- Bounded response size, connection/read timeout and retry policy.
- Stable provider UID, source timestamp and content checksum.
- Parser rejects DTD, external entities, malformed encoding and unsupported schema.
- Secrets supplied through Vault/External Secrets; none in repository or ConfigMap.

## Acceptance sequence

1. Import signed or checksummed fixtures into an isolated database.
2. Validate record count, aliases, names, identifiers and duplicate handling.
3. Prove failed/partial imports leave the last-known-good live dataset unchanged.
4. Prove successful import uses staging and atomic activation.
5. Run screening golden cases and false-positive review.
6. Verify freshness metrics, health indicator and alert routing.
7. Record provider owner, credential rotation, incident contact and rollback procedure.

Production enablement requires AML, security and operations approval plus evidence from the import and alert tests.
