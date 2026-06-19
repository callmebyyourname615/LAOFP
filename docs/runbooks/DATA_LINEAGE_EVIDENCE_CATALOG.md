# Runbook — Data Lineage and Evidence Catalog

Register every material table/topic/API/file/report with owner, classification, retention policy, and PII flag. Lineage changes require transformation version and approved field mapping hash. Cycles are rejected.

Evidence registration accepts only safe logical/object-store paths, exact byte size, and SHA-256. A verifier independently retrieves and hashes the artifact. Once sealed, artifact reference/hash/size are immutable; corrections create a superseding record.
