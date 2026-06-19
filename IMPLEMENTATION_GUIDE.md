# Switching API — Implementation Guide & Time Estimates

> สร้างเมื่อ: 2026-06-17
> Baseline: roadmap 2026-06-12, branch `main` @ 185d5af
> ประมาณการเป็น **engineer-hours** (1 senior backend dev, focused work)

---

## สรุปภาพรวมเวลา

| Phase | Items | Estimate | Calendar |
|---|---|---:|---|
| **Sprint 1 — P0 Blockers** | 5 งาน | **76–112 hrs** | 2–3 สัปดาห์ |
| **Sprint 2 — Secrets/Observability** | 3 งาน | **80–120 hrs** | 2–3 สัปดาห์ |
| **Sprint 3 — Performance/Backup** | 2 งาน | **60–90 hrs** | 2 สัปดาห์ |
| **Sprint 4 — Certification** | 4 งาน | external + 40 hrs | 3–4 สัปดาห์ |
| **รวม จนถึง Go-Live** | | **~260–360 hrs** | **9–12 สัปดาห์** |

---

# 🔴 Sprint 1 — P0 Blockers

## 1. Kubernetes Flyway Migration Job
**⏱ Estimate: 12–16 hrs** | Priority: 🔴 CRITICAL | Blocks: ทุกอย่าง

### Breakdown
| Task | Time |
|---|---:|
| สร้าง `application-migration.yml` profile | 1h |
| เพิ่ม `@Profile("!migration")` บน scheduler/Kafka/outbox/archive worker | 2h |
| สร้าง `k8s/migration-job.yaml` | 2h |
| ลบ init-container Flyway ออกจาก `deployment.yaml` | 1h |
| Integration test: process exit code = 0 | 3h |
| ทดสอบ migration บน empty DB + existing DB | 2h |
| CI/CD: เรียก migration job ก่อน deploy | 2h |
| Doc + verification | 1h |

### Files
- `src/main/resources/application-migration.yml` (new)
- `k8s/migration-job.yaml` (new)
- `k8s/deployment.yaml` (modify)
- `src/main/java/.../config/*` (add `@Profile("!migration")`)
- `.github/workflows/deploy.yml`

### Verification
```bash
kubectl apply -f k8s/migration-job.yaml
kubectl wait --for=condition=complete job/switching-db-migration --timeout=300s
kubectl logs job/switching-db-migration | grep "Successfully applied"
```

---

## 2. Immutable Container Image
**⏱ Estimate: 4–6 hrs** | Priority: 🔴 CRITICAL

### Breakdown
| Task | Time |
|---|---:|
| แก้ CI tag image ด้วย `${GITHUB_SHA}` | 1h |
| Push by digest, capture digest output | 1h |
| `k8s/deployment.yaml` → ใช้ `@sha256:` digest | 1h |
| Build info ใน `pom.xml` + `/actuator/info` | 1h |
| Rollback drill on staging | 1h |

### Files
- `.github/workflows/build.yml`
- `k8s/deployment.yaml`
- `pom.xml` (build-info plugin)

---

## 3. CI Full Test Gate + Security Scans
**⏱ Estimate: 8–12 hrs** | Priority: 🔴 CRITICAL

### Breakdown
| Task | Time |
|---|---:|
| เปลี่ยน CI เป็น `./mvnw test` (เลิก test list) | 1h |
| Trivy/OWASP dependency scan | 2h |
| CodeQL SAST | 2h |
| Gitleaks secret scan | 1h |
| Container image scan | 2h |
| Branch protection rules | 1h |
| Test report artifacts | 1h |

### Files
- `.github/workflows/ci.yml`
- `.github/workflows/security.yml` (new)

---

## 4. Webhook Secret Encryption
**⏱ Estimate: 24–32 hrs** | Priority: 🔴 CRITICAL | งานใหญ่ที่สุดของ Sprint 1

### Breakdown
| Task | Time |
|---|---:|
| Design: KMS vs Vault decision | 2h |
| Schema migration `V43__webhook_secret_encryption.sql` | 2h |
| `SecretEncryptionService` interface | 1h |
| `KmsSecretEncryptionService` (envelope encryption) | 6h |
| `WebhookSecretRotationService` (grace period) | 4h |
| แก้ `WebhookRegistrationEntity` + HMAC signer | 3h |
| Data migration utility (encrypt existing rows) | 3h |
| `ProductionStartupValidator` reject static keys | 1h |
| Unit + integration tests | 6h |
| Migration `V44__drop_secret_plain.sql` (หลัง backfill) | 1h |
| Doc + secret rotation runbook | 1h |

### Schema (V43)
```sql
ALTER TABLE webhook_registrations
    ADD COLUMN secret_ciphertext TEXT,
    ADD COLUMN secret_key_id VARCHAR(200),
    ADD COLUMN secret_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN previous_secret_ciphertext TEXT,
    ADD COLUMN previous_secret_expires_at TIMESTAMP;
```

### Acceptance
- [ ] ไม่มี plaintext ใน DB dump
- [ ] HMAC verify ผ่านในช่วง rotation grace
- [ ] KMS unavailable → fail closed
- [ ] Audit log ทุกครั้งที่ rotate

---

## 5. Real Sanctions Sync Providers
**⏱ Estimate: 28–40 hrs** | Priority: 🔴 CRITICAL | งานใหญ่ที่สุด overall

### Breakdown
| Task | Time |
|---|---:|
| Design: provider abstraction + staging table strategy | 3h |
| `SanctionsProvider` interface + base class | 2h |
| `BolFiuSanctionsProvider` (ติดต่อ BoL เพื่อ spec) | 8h |
| `OfacSanctionsProvider` (SDN XML parser, XXE-safe) | 5h |
| `UnSanctionsProvider` (Consolidated XML) | 5h |
| `SanctionsImportService` (staging + atomic swap) | 4h |
| `SanctionsFreshnessMonitor` + Micrometer gauges | 2h |
| Last-known-good fallback logic | 3h |
| `ProductionStartupValidator`: reject zero records | 1h |
| Tests: exact/alias/normalized match + edge cases | 6h |
| Doc + provider onboarding guide | 1h |

### Files
- `src/main/java/.../aml/sanctions/provider/SanctionsProvider.java`
- `src/main/java/.../aml/sanctions/provider/{BolFiu,Ofac,Un}SanctionsProvider.java`
- `src/main/java/.../aml/sanctions/SanctionsImportService.java`
- `src/main/java/.../aml/sanctions/SanctionsFreshnessMonitor.java`
- แก้ `SanctionsListSyncService.java:68,80,91` (ลบ TODO)

### Must-Have
- HTTPS cert validation, connect/read timeout, exponential backoff retry
- XML parser: `XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES=false`
- Staging table → atomic swap (ห้าม truncate ก่อน parse ผ่าน)
- Idempotent upsert + soft-delete

---

# 🟡 Sprint 2 — Secrets Manager & Observability

## 6. Production Secrets Manager
**⏱ Estimate: 20–28 hrs**

| Task | Time |
|---|---:|
| เลือก provider (Vault / AWS SM / GCP SM) | 2h |
| External Secrets Operator setup | 4h |
| Migrate ทุก secret ออกจาก `k8s/secret.yaml` | 4h |
| แยก app DB user vs Flyway DB user | 3h |
| Postgres TLS `verify-full` | 3h |
| Kafka SASL_SSL + hostname check | 3h |
| Rotation policy + runbook | 2h |
| `scripts/check_prod_config.sh` enhancements | 2h |

## 7. Monitoring + Alerting
**⏱ Estimate: 32–48 hrs**

| Task | Time |
|---|---:|
| Micrometer metrics (12 categories ใน roadmap §9) | 12h |
| Prometheus scrape config + ServiceMonitor | 3h |
| Grafana dashboards (API, transaction, settlement, AML) | 12h |
| AlertManager rules (11 critical alerts) | 6h |
| Runbook ต่อ alert | 8h |
| Alert testing (simulated failure) | 4h |

## 8. Backup, PITR & Restore Drill
**⏱ Estimate: 28–44 hrs**

| Task | Time |
|---|---:|
| Automated full backup (pg_basebackup or cloud-native) | 4h |
| WAL archiving config | 3h |
| Backup encryption | 2h |
| Cross-region replication | 4h |
| Restore runbook + scripts | 6h |
| **Restore drill บน isolated env** | 8h |
| Failover drill | 6h |
| RPO/RTO measurement + sign-off doc | 3h |

---

# 🟢 Sprint 3 — Performance & Security

## 9. Performance Testing
**⏱ Estimate: 40–60 hrs**

| Scenario | Time |
|---|---:|
| K6/Gatling script authoring | 12h |
| Sustained 2k TPS / 300s | 4h |
| Burst 10k TPS / 60s | 4h |
| VPA lookup 500 concurrent | 3h |
| Settlement 500k tx/cycle | 6h |
| Soak test 8-24h | 12h |
| Analysis + tuning (Hikari, JVM, HPA) | 12h |
| Report + capacity sizing | 4h |

## 10. Security Hardening + Pen Test
**⏱ Estimate: 20–30 hrs internal** + external pen-test (1–2 weeks vendor)

| Task | Time |
|---|---:|
| Webhook SSRF protection (localhost/private/metadata block) | 6h |
| Outbound URL allowlist or egress proxy | 4h |
| mTLS trust boundary review | 3h |
| Ingress client-cert header sanitization | 2h |
| PII masking audit | 3h |
| Key rotation drill | 2h |
| Pen-test remediation buffer | 8h |

---

# 🟢 Sprint 4 — DR & Compliance Sign-Off

## 11. Disaster Recovery Drill — 16–24 hrs
- Kill pod / broker / DB primary
- AZ failure simulation
- Object storage unavailable
- External RTGS/FIU timeout
- Network partition
- Deployment rollback drill

## 12. Compliance & Business Sign-Off — external (BoL, AML team, exec)
- BoL technical architecture approval
- AML/CFT workflow sign-off
- Production license/certification
- Operations readiness review

---

# 📊 Quick Reference: ลำดับเริ่มทำ

```
Week 1   ┃ #1 Migration Job (12-16h) → ปลดล็อก deploy
Week 1-2 ┃ #2 Image digest (4-6h) + #3 CI gate (8-12h)  [คู่ขนาน]
Week 2-3 ┃ #4 Webhook encryption (24-32h)  [คู่ขนาน]
Week 2-4 ┃ #5 Sanctions providers (28-40h)  [คู่ขนาน — รอ BoL spec]
─────────── End of Sprint 1 ───────────
Week 4-5 ┃ #6 Secrets Manager
Week 4-6 ┃ #7 Monitoring + #8 Backup  [คู่ขนาน]
─────────── End of Sprint 2 ───────────
Week 7-8 ┃ #9 Performance + #10 Security  [คู่ขนาน]
─────────── End of Sprint 3 ───────────
Week 9+  ┃ #11 DR drill + #12 Sign-off
```

---

# ✅ Go/No-Go Gate ก่อนเริ่มแต่ละสัปดาห์

**Before Sprint 1 starts:**
- [ ] เลือก KMS provider (AWS KMS / Vault Transit / GCP KMS)
- [ ] ติดต่อ BoL เพื่อขอ spec sanctions endpoint
- [ ] Confirm container registry (GHCR / ECR / GCR)

**Before Sprint 2 starts:**
- [ ] P0 checklist ทุกข้อ ✅
- [ ] Staging deploy ทำซ้ำได้ + rollback ได้

**Before Sprint 3 starts:**
- [ ] Monitoring dashboard ใช้งานได้จริง
- [ ] Restore drill มีหลักฐาน

**Before Production Go-Live:**
- ดู `PRODUCTION_GO_LIVE_IMPLEMENTATION_ROADMAP.md` §16

---

# 🎯 คำแนะนำ

1. **เริ่ม #1 ก่อนเสมอ** — Migration Job blocks ทุก staging deploy
2. **#4 + #5 คือ critical path** — งานใหญ่สุด ต้องมอบหมายให้ dev คนเก่งสุด
3. **#5 ต้องเริ่ม contact BoL ตั้งแต่วันแรก** — external dependency มี lead time
4. **อย่าเริ่ม Sprint 2 ก่อน P0 ครบ** — observability บนระบบที่ deploy ไม่ stable เสียเวลาเปล่า
5. **Pen test (#10) จองล่วงหน้า ≥ 3 สัปดาห์** — vendor มี waitlist

---

> Total: **~260–360 engineer-hours** ≈ **9–12 calendar weeks** (1 senior dev focused)
> ลดลงเหลือ ~6 สัปดาห์ ถ้ามี 2-3 devs ทำคู่ขนาน
