# Phases 09–12 delivery notes

This change set contains ten reviewable implementation sub-phases built on the Phase 1–8 baseline.

## 09A — Performance harness foundation

Pinned k6 container execution, reusable request/payload libraries, threshold gates, machine-readable summaries, manual GitHub Actions workflow, and immutable release-digest input validation.

## 09B — Target load scenarios

Sustained 2,000 TPS, burst 10,000 TPS, VPA 500 concurrent, QR 200 concurrent, and 10,000 webhook-test scenarios. QR traffic requires a pre-seeded active QR identifier and never invents a production-like QR payload.

## 09C — Settlement, soak, capacity, and reconciliation evidence

500,000-transfer T+1 settlement seeding, 8–24-hour soak test, Kubernetes/Prometheus evidence capture, database reconciliation, and capacity sign-off template.

## 10A — Webhook SSRF destination policy

Registration-time and send-time checks; HTTPS/port/hostname controls; all-address DNS validation; loopback/private/link-local/metadata/reserved blocking; user-info and fragment rejection.

## 10B — Redirect, DNS-rebinding, and egress controls

Redirects are disabled. Production requires an organization-managed forward proxy in addition to the application allowlist to close the DNS-validation/connection TOCTOU gap. Startup rejects absent or placeholder proxy settings.

## 10C — mTLS trusted-ingress boundary

Ingress-nginx client-certificate verification, trusted `ssl-client-cert` header, spoofed-header removal before authentication filters, ingress-only application-port NetworkPolicy, non-root container, read-only root filesystem, dropped Linux capabilities, and RuntimeDefault seccomp.

## 10D — PII, audit, DAST, and key-rotation readiness

Recursive secret masking, malformed-text masking, repository logging scan, ZAP baseline workflow, Vault Transit rotation drill, chained SHA-256 audit entries, database-role hardening, and chain verification.

## 11A — DR orchestration

Guarded, allowlisted UAT/DR scenarios for pod, broker, object storage, network, deployment rollback, and external timeout failures. Scripts preserve and restore original state on normal exit, error, or signal.

## 11B — Recovery and evidence

UTC timeline, baseline/post-recovery counts, application health, reconciliation JSON, evidence hashes, and a human-sign-off boundary. Phase 8 owns database primary failover/PITR evidence.

## 12A — Go-live evidence gate

Portable content-addressed evidence manifest, path traversal protection, size/hash validation, required evidence categories, role-separated approvals, and an explicit APPROVED status gate.

## External gates still required

Automated tooling does not replace a real isolated load test, independent penetration test, proxy/firewall implementation, DR exercise, PostgreSQL failover/PITR drill, BoL/AML approval, operations readiness review, or executive go-live approval.
