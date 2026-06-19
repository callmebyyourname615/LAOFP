# Runbook — Settlement Calendar and Cutoff

## Before activation
Validate the calendar YAML, check IANA time zone, official holidays, weekend definitions, early-close times, cycle cutoffs, and late actions. Compare the bundle hash presented for approval with the artifact promoted to production.

## Cutoff incident
Confirm application clock/NTP, active calendar version, participant submission timestamp, local time conversion, grace seconds, and holiday status. Do not bypass a cutoff by changing the server clock. Use `MANUAL_REVIEW` or a governed calendar change.

## Evidence
Calendar version/hash, approvers, cutoff rule, submitted timestamp, resolved business date, and `settlement_cutoff_decision` hash.
