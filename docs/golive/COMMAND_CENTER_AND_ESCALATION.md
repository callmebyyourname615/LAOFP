# Go-Live Command Center and Escalation

Required roles:

- Release commander
- Application owner
- Database owner
- Kafka owner
- Security owner
- Operations owner
- Business validation owner
- Rollback decision authority

Each role must have a primary contact, escalation contact, authenticated communication channel, and availability window. Contact data is supplied at execution time and must not be committed to the repository.

Decision authority:

- The release commander coordinates but cannot independently approve promotion.
- Every promotion requires at least two distinct approvers.
- Rollback authority may stop the release unilaterally when a stop condition is met.
- Security may stop the release for identity, signature, secret, or access-control failures.
- Business validation may stop the release for reconciliation or transaction-integrity failure.
