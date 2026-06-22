# Continuous Re-Certification

1. Produce a repository-relative changed-path manifest.
2. Hash the source artifacts and policy set.
3. Map changes using `certification/enterprise/impact-rules.yaml`.
4. Treat every unmapped path as requiring full re-certification.
5. Collect signed certification results for every impacted control.
6. Verify the prior certificate signature and expiry.
7. Run Phase 57A. A `BLOCK` result prohibits production promotion.
8. Preserve the previous and new certification reports in immutable evidence storage.

Emergency changes have a maximum 24-hour grace period and require post-change re-certification.
