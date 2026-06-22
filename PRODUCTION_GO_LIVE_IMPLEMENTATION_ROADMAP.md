# Production Go-Live Implementation Roadmap

## 1. Executive Summary

สถานะปัจจุบันของ Switching API คือ **พร้อมสำหรับ Development/UAT และใกล้พร้อมสำหรับ Staging** แต่ยังไม่ควรเปิดใช้งานเป็น Full Production สำหรับระบบ Financial Switching

ผลตรวจล่าสุดเมื่อวันที่ 12 มิถุนายน 2026:

- Java 21 / Spring Boot 4.0.3
- Compile สำเร็จ
- Full automated test suite ผ่าน `396/396`
- Flyway migrations ผ่านทั้งหมด 42 migrations
- มี Docker image, Kubernetes manifests, HPA, PDB และ NetworkPolicy
- มี production profile และ `ProductionStartupValidator`
- Business modules หลักถูก implement แล้ว

อย่างไรก็ตาม ยังมี blocker ด้าน deployment, secret protection, sanctions data, CI/CD และ production certification ที่ต้องปิดก่อน Go-Live

### Current Readiness

| Environment | Readiness | Decision |
|---|---:|---|
| Development | 95% | GO |
| UAT | 90% | GO |
| Staging | 80% | CONDITIONAL GO |
| Limited Pilot | 70% | CONDITIONAL GO หลังปิด P0 |
| Full Production | 60-70% | NO-GO |

---

## 2. Implementation Priority

งานควรดำเนินตามลำดับต่อไปนี้:

| Priority | Workstream | Severity | Owner Type |
|---|---|---|---|
| P0 | แก้ Kubernetes Flyway migration lifecycle | Critical | Backend / DevOps |
| P0 | เข้ารหัส webhook signing secrets | Critical | Backend / Security |
| P0 | Implement sanctions synchronization จริง | Critical | Backend / Compliance |
| P0 | ทำ Full Test Suite เป็น CI Gate | Critical | Backend / DevOps |
| P0 | ใช้ immutable container image | Critical | DevOps |
| P1 | Production configuration และ Secrets Manager | High | DevOps / Security |
| P1 | Monitoring, alerting และ operational dashboards | High | SRE / DevOps |
| P1 | Backup, restore, PITR และ failover | High | DBA / SRE |
| P1 | Load, stress และ soak testing | High | Performance / QA |
| P1 | Security hardening และ penetration test | High | Security |
| P2 | DR drill และ multi-zone certification | High | SRE / Business |
| P2 | BoL/compliance sign-off | Mandatory | Compliance / Management |

---

# Phase P0: Code and Deployment Blockers

## 3. Fix Kubernetes Flyway Migration Lifecycle

### Problem

ไฟล์ `k8s/deployment.yaml` ใช้ application image เพื่อรัน Flyway ผ่าน Spring Boot `JarLauncher` ใน init container

แนวทางนี้อาจเปิด Spring application context, web server, scheduler, Kafka consumer หรือ background worker หลัง migration เสร็จ ทำให้ init container ไม่ exit และ application pod หลักไม่เริ่มทำงาน

### Required Implementation

เลือกใช้หนึ่งในสองแนวทาง:

#### Recommended: Separate Kubernetes Migration Job

- สร้าง `k8s/migration-job.yaml`
- ใช้ Flyway CLI image หรือ migration-specific application mode
- รัน migration ก่อน deployment
- Job ต้อง exit code `0` เมื่อสำเร็จ
- Deployment pipeline ต้องหยุดทันทีเมื่อ migration ล้มเหลว
- ห้าม rollback database migration อัตโนมัติ

#### Alternative: Dedicated Spring Migration Mode

- เพิ่ม profile เช่น `migration`
- กำหนด `spring.main.web-application-type=none`
- ปิด scheduler, Kafka listener, outbox worker และ archive worker
- ให้ process terminate หลัง Flyway สำเร็จ
- เพิ่ม integration test ยืนยันว่า process exit ได้

### Files to Change

- `k8s/deployment.yaml`
- `k8s/migration-job.yaml` ใหม่
- `src/main/resources/application-migration.yml` หากใช้ migration profile
- CI/CD workflow สำหรับ execute และตรวจ migration job

### Acceptance Criteria

- Migration รันกับ empty PostgreSQL database สำเร็จ
- Migration รันกับ database version ล่าสุดแล้ว exit สำเร็จ
- Migration failure ทำให้ deployment หยุด
- Application pods ไม่เริ่มก่อน migration สำเร็จ
- Migration job ไม่เปิด HTTP port
- Migration job ไม่มี scheduled/background workers
- ทดสอบ upgrade จาก production-like schema snapshot สำเร็จ

### Verification

```bash
kubectl apply -f k8s/migration-job.yaml
kubectl wait --for=condition=complete job/switching-db-migration --timeout=300s
kubectl logs job/switching-db-migration
```

---

## 4. Encrypt Webhook Signing Secrets

### Problem

ปัจจุบัน `WebhookRegistrationEntity` เก็บ signing secret ใน column `secret_plain` เพื่อใช้ลงลายเซ็น HMAC สำหรับ outbound webhook

หาก database ถูกอ่านโดยผู้ไม่มีสิทธิ์ ผู้โจมตีสามารถนำ secret ไปปลอม webhook payload ได้ การเก็บ plaintext จึงไม่เหมาะกับ production financial system

### Required Implementation

- เปลี่ยน `secret_plain` เป็น `secret_ciphertext`
- ใช้ envelope encryption ผ่าน KMS, Vault Transit หรือ HSM
- เก็บ key identifier และ encryption version
- ถอดรหัสเฉพาะใน memory ตอนสร้าง HMAC
- ห้าม log plaintext secret
- รองรับ secret rotation
- รองรับ previous secret ในช่วง grace period
- จำกัดสิทธิ์ database user ไม่ให้ export secret columns โดยไม่จำเป็น

### Suggested Schema

```sql
ALTER TABLE webhook_registrations
    ADD COLUMN secret_ciphertext TEXT,
    ADD COLUMN secret_key_id VARCHAR(200),
    ADD COLUMN secret_version INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN previous_secret_ciphertext TEXT,
    ADD COLUMN previous_secret_expires_at TIMESTAMP;
```

หลัง migrate ข้อมูลสำเร็จ:

```sql
ALTER TABLE webhook_registrations DROP COLUMN secret_plain;
```

### Components

- `SecretEncryptionService`
- `KmsSecretEncryptionService` หรือ `VaultSecretEncryptionService`
- `WebhookSecretRotationService`
- migration utility สำหรับ encrypt existing plaintext values
- startup validation ป้องกัน production จาก local/static encryption key

### Acceptance Criteria

- ไม่มี plaintext webhook secret ใน database
- API คืน plaintext secret เฉพาะตอนสร้างหรือ rotate ครั้งเดียว
- Webhook signature เดิมยัง verify ได้ระหว่าง grace period
- KMS/Vault unavailable ต้อง fail closed
- Secret rotation มี audit log
- Database dump ไม่มี plaintext secret
- Tests ครอบคลุม encrypt, decrypt, rotate และ invalid ciphertext

---

## 5. Implement Real Sanctions Synchronization

### Problem

`SanctionsListSyncService` ยังมี `TODO` สำหรับ:

- BoL/FIU sanctions source
- OFAC SDN
- UN Consolidated Sanctions List

ระบบมี screening flow แล้ว แต่ production ต้องมี data ingestion จริงและตรวจสอบ freshness ได้

### Required Implementation

สร้าง adapter แยกตาม provider:

- `BolFiuSanctionsProvider`
- `OfacSanctionsProvider`
- `UnSanctionsProvider`
- `SanctionsImportService`
- `SanctionsFreshnessMonitor`

แต่ละ provider ต้องรองรับ:

- HTTPS และ certificate validation
- authentication ตามข้อกำหนด provider
- connect/read timeout
- retry with exponential backoff
- checksum หรือ digital-signature verification ถ้ามี
- XML/JSON parsing แบบป้องกัน XXE
- idempotent upsert
- source/version/effective date
- soft deactivation ของรายการที่ถูกถอน
- last-successful-sync timestamp

### Failure Policy

- ห้ามล้าง sanctions table ก่อน parse data ใหม่สำเร็จ
- ใช้ staging table แล้ว atomic swap/upsert
- หาก source sync ล้มเหลว ให้ใช้ last-known-good dataset
- แจ้งเตือนเมื่อ dataset stale เกิน policy
- Production startup ต้อง fail หากไม่มี active sanctions records

### Acceptance Criteria

- Import sample datasets ของทั้งสาม source สำเร็จ
- Duplicate entries ไม่เพิ่ม record ซ้ำ
- Removed entry ถูก mark inactive
- Partial/corrupt download ไม่ทำลายข้อมูลเดิม
- มี metric `sanctions_sync_last_success_timestamp`
- มี alert เมื่อ sync ล้มเหลวหรือ data stale
- Screening test ครอบคลุม exact match, alias, normalized name และ false-positive handling

---

## 6. Make Full Test Suite a Required CI Gate

### Problem

Local full suite ผ่าน `396/396` แต่ GitHub Actions ปัจจุบันรัน integration tests เพียงบาง test classes ดังนั้น pull request อาจผ่าน CI ทั้งที่ test อื่นเสีย

### Required Implementation

- เปลี่ยน CI ให้รัน `./mvnw test`
- แยก unit/integration tests ด้วย Maven profile หรือ naming convention หากต้องการ parallelize
- ห้ามใช้ test list แบบ manual ซึ่งตกหล่นเมื่อเพิ่ม test ใหม่
- เพิ่ม dependency vulnerability scan
- เพิ่ม secret scan
- เพิ่ม SAST
- build image หลัง tests ทั้งหมดผ่านเท่านั้น
- deploy เฉพาะ immutable image ที่ผ่าน scan แล้ว

### Suggested Pipeline

1. Compile
2. Unit tests
3. Full integration tests
4. Package
5. Dependency/SAST/secret scan
6. Docker build
7. Container scan
8. Push image by commit SHA
9. Database migration
10. Deploy staging
11. Smoke tests
12. Manual production approval
13. Production deploy
14. Post-deploy verification

### Acceptance Criteria

- ทุก PR รัน tests ครบโดยอัตโนมัติ
- Test failure ใด ๆ block merge
- HIGH/CRITICAL vulnerability block image promotion
- Test reports ถูกเก็บเป็น CI artifacts
- Branch protection บังคับ required checks
- ไม่มี deployment จาก developer machine โดยตรง

---

## 7. Use Immutable Container Images

### Problem

Kubernetes Deployment ใช้:

```yaml
image: switching-api:latest
```

Tag `latest` เปลี่ยนเนื้อหาได้ ทำให้ไม่สามารถยืนยันได้ว่า pod แต่ละตัวรัน artifact เดียวกัน และ rollback ไม่แน่นอน

### Required Implementation

- Tag image ด้วย Git commit SHA
- Production ควร pin ด้วย image digest
- บันทึก application version, commit SHA และ build timestamp
- expose build information ผ่าน `/actuator/info`
- deploy และ rollback โดยอ้างอิง digest

### Example

```yaml
image: ghcr.io/organization/repository/switching-api@sha256:IMAGE_DIGEST
imagePullPolicy: IfNotPresent
```

### Acceptance Criteria

- ไม่มี `latest` ใน production manifests
- ทุก pod ใน release ใช้ digest เดียวกัน
- สามารถ trace image กลับไปยัง Git commit และ CI run
- rollback ไป release ก่อนหน้าได้ภายในเวลาที่กำหนด

---

# Phase P1: Infrastructure and Operational Readiness

## 8. Production Configuration and Secrets Manager

### Required Work

- เลือก Secrets Manager เช่น Vault, AWS Secrets Manager, GCP Secret Manager หรือ cloud KMS
- ห้าม commit production secret ลง Git
- Kubernetes `secret.yaml` ต้องเป็น template เท่านั้น
- ใช้ External Secrets Operator หรือ CSI Secret Store
- กำหนด rotation policy
- แยก application DB user และ Flyway DB user
- ใช้ PostgreSQL TLS `verify-full` เมื่อ infrastructure รองรับ
- ใช้ Kafka SASL_SSL และตรวจ hostname/certificate
- validate endpoint ทั้งหมดว่าไม่ใช่ localhost/mock/placeholder

### Acceptance Criteria

- Production secrets ไม่อยู่ใน manifest หรือ CI log
- Secret rotation ไม่ต้อง rebuild image
- Database, Kafka และ external APIs ใช้ TLS
- `scripts/check_prod_config.sh` ผ่าน
- `ProductionStartupValidator` ผ่านบน production-like environment

---

## 9. Monitoring and Alerting

### Required Metrics

- request count, latency และ error rate
- transaction success/reject/reversal rate
- outbox backlog และ oldest pending age
- Kafka producer/consumer errors
- database pool usage
- database query latency
- settlement cycle duration
- reconciliation mismatch count
- sanctions sync freshness
- webhook retry/dead-letter count
- liquidity threshold alerts
- pod restarts, OOM และ CPU throttling

### Required Alerts

- API error rate สูงกว่ากำหนด
- P95/P99 latency เกิน SLA
- outbox backlog เพิ่มต่อเนื่อง
- database connection pool exhausted
- sanctions dataset stale
- settlement cycle ไม่เสร็จตามเวลา
- webhook permanent failure
- STR submission failed
- Kafka unavailable
- replica unavailable
- disk/storage capacity ต่ำ

### Acceptance Criteria

- มี Prometheus scraping
- มี Grafana dashboards
- alert ไปยัง on-call channel
- alerts ทุกตัวมี runbook
- ทดสอบ alert ด้วย simulated failure

---

## 10. Backup, Restore, PITR and Failover

### Required Work

- automated full backup
- WAL archiving สำหรับ Point-in-Time Recovery
- encrypted backup
- backup retention policy
- cross-zone หรือ cross-region backup
- documented restore process
- restore drill บน isolated environment
- primary database failover test
- archive database และ object storage recovery

### Acceptance Criteria

- Restore backup สำเร็จและ application อ่านข้อมูลได้
- ตรวจ checksum/count ของ critical tables
- RPO และ RTO ผ่าน business requirement
- มีหลักฐาน restore drill พร้อมวันเวลาและผู้อนุมัติ
- backup failure มี alert

---

## 11. Performance and Capacity Testing

### Required Test Scenarios

| Test | Target |
|---|---|
| Sustained load | 2,000 TPS เป็นเวลา 300 วินาที |
| Burst load | 10,000 TPS เป็นเวลา 60 วินาที |
| VPA lookup | 500 concurrent requests |
| QR payment | 200 concurrent requests |
| Settlement | 500,000 transactions ต่อ cycle |
| Webhook | 10,000 events |
| Soak test | อย่างน้อย 8-24 ชั่วโมง |

### Measurements

- P50, P95 และ P99 latency
- throughput
- error/rejection rate
- CPU/memory
- GC pause
- Hikari pool utilization
- Kafka lag
- database locks และ slow queries
- HPA scale-up time

### Acceptance Criteria

- P95 เป็นไปตาม SLA
- ไม่มี data loss
- ไม่มี duplicate settlement
- ไม่มี pool balance inconsistency
- pod scaling ไม่ทำให้ request สูญหาย
- resource settings อ้างอิงผลทดสอบจริง ไม่ใช่ค่าประมาณ

---

## 12. Security Hardening

### Required Work

- SAST และ dependency scanning
- container image scanning
- secret scanning
- DAST บน staging
- penetration test โดย independent security team
- verify mTLS trust boundary
- verify ingress ลบ client certificate header จาก external request แล้ว inject ค่า trusted เท่านั้น
- SSRF protection สำหรับ webhook URLs
- outbound URL allowlist หรือ egress proxy
- key rotation drill
- review audit-log completeness
- review PII masking
- rate-limit test แบบ distributed

### Special Attention: Webhook SSRF

Webhook registration URL ต้องป้องกัน:

- localhost
- loopback
- link-local
- private network ranges ที่ไม่อนุญาต
- cloud metadata endpoints
- DNS rebinding
- redirect ไปยัง blocked destination

### Acceptance Criteria

- ไม่มี unresolved CRITICAL/HIGH findings
- mTLS, OAuth และ request signing ผ่าน end-to-end test
- audit logs ไม่สามารถแก้ไขโดย application user
- security incident runbook ผ่าน tabletop exercise

---

# Phase P2: Disaster Recovery and Certification

## 13. Disaster Recovery Drill

ทดสอบอย่างน้อย:

- kill application pod ระหว่าง transaction
- kill Kafka broker
- database primary failure
- availability-zone failure
- object storage unavailable
- external RTGS/FIU endpoint timeout
- network partition
- deployment rollback

### Acceptance Criteria

- ไม่มี committed transaction สูญหาย
- outbox replay ไม่สร้าง duplicate business effect
- service recovery อยู่ใน RTO
- data recovery อยู่ใน RPO
- incident timeline และ lessons learned ถูกบันทึก

---

## 14. Compliance and Business Sign-Off

Full Production Go-Live ต้องมีหลักฐาน:

- BoL technical architecture approval
- production license/certification
- AML/CFT workflow approval
- penetration-test report
- DR drill sign-off
- backup/restore sign-off
- capacity-test report
- operations readiness review
- incident response contacts
- change/rollback approval
- data retention and privacy approval

---

# 15. Recommended Sprint Plan

## Sprint 1: Close Immediate Code Blockers

- แยก Flyway migration job
- เพิ่ม migration lifecycle tests
- เปลี่ยน CI ให้รัน full suite
- เปลี่ยน image reference เป็น SHA/digest
- เพิ่ม build metadata

**Exit:** Staging deployment ทำซ้ำและ rollback ได้อย่างแน่นอน

## Sprint 2: Secrets and Compliance Data

- implement webhook secret encryption
- migrate existing webhook secrets
- implement secret rotation
- implement BoL/FIU sanctions provider
- implement OFAC/UN providers
- เพิ่ม data freshness metrics/alerts

**Exit:** ไม่มี plaintext operational secrets และ sanctions data มาจาก production source

## Sprint 3: Observability and Reliability

- Prometheus/Grafana dashboards
- production alerts
- backup/PITR configuration
- restore drill
- failover drill
- complete runbooks

**Exit:** ระบบตรวจจับ failure และกู้คืนได้ตามขั้นตอน

## Sprint 4: Certification

- load/stress/soak tests
- chaos/failover tests
- penetration test
- remediation
- BoL/compliance documentation
- production readiness review

**Exit:** ผ่าน Go-Live gate และได้รับ business/regulatory approval

---

# 16. Production Go/No-Go Checklist

## Code and CI

- [ ] Full test suite ผ่านใน CI
- [ ] Database migration job รันแล้ว exit
- [ ] Container image pin ด้วย digest
- [ ] ไม่มี HIGH/CRITICAL vulnerability
- [ ] ไม่มี plaintext webhook secrets
- [ ] Sanctions sync ใช้ production providers

## Infrastructure

- [ ] Production config validation ผ่าน
- [ ] Secrets Manager ใช้งานจริง
- [ ] Database TLS และ Kafka TLS ผ่าน
- [ ] Monitoring dashboards พร้อม
- [ ] Critical alerts ทดสอบแล้ว
- [ ] Backup และ PITR ทำงาน

## Reliability

- [ ] Restore drill ผ่าน
- [ ] Database failover ผ่าน
- [ ] Pod termination/replay ผ่าน
- [ ] Multi-zone failover ผ่าน
- [ ] Rollback drill ผ่าน

## Performance

- [ ] Sustained load test ผ่าน
- [ ] Burst load test ผ่าน
- [ ] Settlement capacity test ผ่าน
- [ ] Soak test ผ่าน
- [ ] ไม่มี data loss หรือ balance inconsistency

## Security and Compliance

- [ ] Penetration test ผ่าน
- [ ] Security findings ถูกปิดหรือ formally accepted
- [ ] AML/CFT sign-off
- [ ] DR sign-off
- [ ] Operations sign-off
- [ ] BoL certification/license พร้อม

---

# 17. Final Recommendation

งานที่ควรเริ่มทันทีคือ **Kubernetes Flyway migration lifecycle** เพราะเป็น deployment blocker โดยตรง จากนั้นให้ทำ **webhook secret encryption**, **real sanctions synchronization**, **full CI gate** และ **immutable image deployment**

เมื่อ P0 ทั้งหมดเสร็จ ระบบสามารถเข้าสู่ production-like staging หรือ limited pilot ได้ แต่ Full Production Go-Live ควรรอจน load testing, backup/restore, failover, penetration testing, DR drill และ regulatory sign-off ผ่านครบ

**Current decision: NO-GO for Full Production**

**Next milestone: Complete all P0 items and deploy to production-like staging**


## Phase 58A-58J — Regulatory & Ecosystem Assurance

Repository controls are implemented. Execute Phase 58A through 58J on protected regulatory, operations, security, compliance, financial-control, and simulation runners. Supervisory readiness requires PASS evidence from all Phase 58 domains and a verified immutable evidence manifest.
