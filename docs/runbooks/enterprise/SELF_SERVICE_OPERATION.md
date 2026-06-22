# Self-Service Operation Safety

The request validator accepts only operations in `operations/operation-catalog.yaml`. It rejects shell metacharacters, non-allowlisted namespaces, excessive scale targets, oversized dead-letter retries and stale approvals.

The output plan always contains `executionAuthorized: false`. A separate protected executor must re-validate the signed request and perform the operation. Arbitrary commands are never accepted.
