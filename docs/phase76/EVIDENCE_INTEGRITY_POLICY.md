# Evidence Integrity Policy

Every artifact is SHA-256 hashed. Ledger entries chain to the previous record hash and are bound to the release Git commit. Synthetic evidence, stale evidence, commit mismatches, modified artifacts, expired approvals and missing signatures block release promotion. Secret values must never be present in the evidence bundle.
