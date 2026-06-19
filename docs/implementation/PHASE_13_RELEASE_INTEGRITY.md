# Phase 13 — Release Integrity and Provenance

Every production deployment must be traceable to one full Git commit and one immutable registry digest.
The release evidence bundle hashes the application JAR, Flyway migrations, Kubernetes manifests,
event schemas, `pom.xml`, and `Dockerfile`.

## Gate

1. CI and security workflows pass for the exact commit.
2. Container registry returns `sha256:<64 hex>`.
3. `release-evidence.yml` checks out that exact commit and rebuilds the JAR.
4. `verify-release-evidence.py` validates path safety, size, and SHA-256 for every artifact.
5. Store evidence for at least the regulatory retention period.

A release evidence JSON is evidence, not a signing key. The production environment must additionally
apply its organization-approved artifact signing and key-custody process.
