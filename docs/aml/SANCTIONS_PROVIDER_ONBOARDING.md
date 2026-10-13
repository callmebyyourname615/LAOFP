# Sanctions Provider Onboarding Runbook

## Scope

This runbook closes the production onboarding contract for external sanctions data providers. It covers OFAC XML, UN XML, and Bank of Laos FIU JSON feeds imported through the sanctions provider pipeline.

## Production Gate

Complete a successful provider import before production. The import must load fresh provider data into staging, promote the snapshot without truncating the live sanctions table, and leave the previous last-known-good data available when a provider feed fails.

The provider endpoint must use HTTPS in production. For private provider links, use mTLS or an allow-listed private network path in addition to HTTPS.

## Required Provider Contract

- Provider identity is stable and mapped to `provider_code`.
- Every record has a stable provider UID mapped to `provider_uid`.
- Source payload contains a source version, publication timestamp, or equivalent feed date.
- Provider supports replay of the latest production snapshot for recovery.
- Provider supplies a documented emergency contact and feed incident SLA.
- Provider sends schema change notice before breaking XML or JSON shape.

## Onboarding Checklist

- [ ] Confirm legal approval to consume the provider feed.
- [ ] Confirm provider transport is HTTPS and certificate validation is enabled.
- [ ] Store provider credentials in Vault through External Secrets only.
- [ ] Add or confirm parser coverage for the provider fixture.
- [ ] Run a dry import against a non-production database.
- [ ] Confirm `sanctions_import_runs` records the run status and counts.
- [ ] Confirm duplicate provider UID records are rejected or merged according to provider policy.
- [ ] Confirm live sanctions data is not truncated during import.
- [ ] Confirm failed import retains last-known-good data.
- [ ] Attach evidence to the release ticket before production enablement.

## Import Verification

Use the application import job or the operational endpoint configured for the environment. After the import, verify the database state with read-only queries:

```sql
select provider_code, status, imported_count, rejected_count, started_at, completed_at
from sanctions_import_runs
order by started_at desc
limit 10;

select provider_code, count(*) as active_records
from sanctions_lists
where deleted_at is null
group by provider_code
order by provider_code;
```

Expected result:

- Latest run for every production provider is `COMPLETED`.
- `imported_count` is greater than zero for each required provider.
- `rejected_count` is reviewed and accepted by AML operations.
- Active record count does not drop unexpectedly compared with the previous production snapshot.

## Rollback

If a provider import fails or looks suspicious:

- Disable the provider sync schedule for that provider.
- Keep the current live `sanctions_lists` rows in service.
- Preserve the failed staging rows and import run evidence.
- Escalate to AML operations and the provider contact.
- Re-run import only after parser, mapping, or provider data issue is understood.

## Evidence

Attach these artifacts to the production release ticket:

- Provider feed URL or source reference with secrets redacted.
- Import run ID from `sanctions_import_runs`.
- Active sanctions record counts by provider before and after import.
- Parser test result for the provider fixture.
- AML sign-off and SecOps transport review.
