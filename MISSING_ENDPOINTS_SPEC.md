# Missing Endpoints Specification

> Endpoint ที่ SMOS Portal ต้องการ แต่ backend ยังไม่มี
> Portal path: `/Users/macbookpro/Desktop/LAOFP_Portal/SMOS`
> Portal ใช้ mock data ชั่วคราวใน tabs/widgets ที่ระบุด้านล่าง — ต้องการ 8 endpoints ให้ครบ

---

## 📊 สรุปความพร้อม

| # | Endpoint | Table รองรับ | ความยาก | Wave |
|---|---|---|---|---|
| 1 | `GET /api/transfers?riskScoreMin=` | ✅ transfers + fraud_scores | ⭐ ง่าย | 1 |
| 2 | `GET /api/risk/rules` | ✅ fraud_velocity_rule | ⭐ ง่าย | 1 |
| 3 | `GET /api/risk/scores/distribution` | ✅ fraud_scores | ⭐ ง่าย | 1 |
| 4 | `GET /api/admin/security-events` | 🟡 audit_logs (derive) | ⭐⭐ ปานกลาง | 2 |
| 5 | `GET /v1/operator/report-delivery-history` | ✅ report_delivery_run | ⭐ ง่าย | 1 |
| 6 | `GET /v1/aml/reports` | 🟡 need column / new table | ⭐⭐ ปานกลาง | 2 |
| 7 | `GET /api/fees/decisions` | ✅ fee_assessment | ⭐ ง่าย | 1 |
| 8 | `GET /api/fees/exceptions` | 🔴 need new table | ⭐⭐⭐ ยาก | 3 |

**Wave 1 (พร้อม implement ทันที):** #1, #2, #3, #5, #7 — 5 endpoints, table ครบ, schema ไม่ต้องแก้
**Wave 2 (ปรับ schema เล็กน้อย):** #4, #6
**Wave 3 (สร้าง table ใหม่ + workflow):** #8

---

## 1. `GET /api/transfers?riskScoreMin=X` — Filter transfers by risk score

**Portal usage:** RiskFraud page — tab **High-Risk** และ **Blocked**
**Status:** ✅ ไม่ต้องสร้าง endpoint ใหม่ — แค่เพิ่ม query param ใน controller เดิม
**Controller:** [OperationsTransferQueryController.java](src/main/java/com/example/switching/operations/controller/OperationsTransferQueryController.java)

### Purpose
Portal ต้องการดึงเฉพาะ transactions ที่มี risk score สูง (>=40) และที่ถูก block (>=70) เพื่อแสดงในตาราง High-Risk / Blocked

### Params
| Param | Type | Default | Description |
|---|---|---|---|
| `riskScoreMin` | int (0–100) | none | ค่า min ของ risk score |
| `riskScoreMax` | int (0–100) | none | ค่า max ของ risk score |
| `status` | string | none | (existing) |
| `limit` | int | 100 | (existing) |
| `offset` | int | 0 | (existing) |

### SQL

```sql
SELECT t.*
FROM transfers t
LEFT JOIN LATERAL (
    SELECT MAX(score) AS max_score
    FROM fraud_scores
    WHERE txn_id = t.transfer_ref
) fs ON true
WHERE (?::int IS NULL OR fs.max_score * 100 >= ?)
  AND (?::int IS NULL OR fs.max_score * 100 <= ?)
  AND (?::text IS NULL OR t.current_status = ?)
ORDER BY t.created_at DESC
LIMIT ? OFFSET ?
```

### Response
เหมือน `/api/transfers` เดิม — ไม่เปลี่ยน shape

---

## 2. `GET /api/risk/rules` — List risk rules

**Portal usage:** RiskFraud page — tab **Rules**
**Status:** ✅ Table `fraud_velocity_rule` มีอยู่แล้ว (V54__fraud_velocity_controls.sql)
**Controller:** สร้างใหม่ `RiskRulesController.java` ใน `com.example.switching.risk.controller`

### Purpose
Portal แสดงรายการ rule ที่ risk engine ใช้ตัดสิน + ให้ risk officer toggle enable/disable

### Endpoints
```
GET  /api/risk/rules                — list all rules
POST /api/risk/rules/{id}/enable    — enable a rule
POST /api/risk/rules/{id}/disable   — disable a rule
```

### SQL

```sql
SELECT rule_id, rule_name, description, threshold_value, threshold_unit,
       action_on_breach, enabled, priority, updated_by, updated_at
FROM fraud_velocity_rule
ORDER BY priority ASC
```

### Response
```json
{
  "items": [
    {
      "ruleId": "rl1",
      "ruleName": "Velocity Check",
      "description": "Block if >40 txns/5min from same VPA",
      "threshold": "40 txns/5min",
      "actionOnBreach": "BLOCK",
      "enabled": true,
      "priority": 10,
      "updatedBy": "risk.officer@laofp.la",
      "updatedAt": "2026-07-20T14:00:00Z"
    }
  ]
}
```

### Role guard
`@PreAuthorize("hasAnyRole('OPS', 'ADMIN', 'RISK_OFFICER', 'AUDITOR', 'READ_ONLY')")`
Mutations: `SYSTEM_ADMIN`, `RISK_OFFICER` only

---

## 3. `GET /api/risk/scores/distribution` — Risk score histogram

**Portal usage:** RiskFraud page — histogram chart ด้านบน Alerts tab
**Status:** ✅ Compute จาก `fraud_scores` (V24) — ไม่ต้อง table ใหม่
**Controller:** เพิ่ม endpoint ใน `RiskController.java` หรือ `RiskAlertsController.java`

### Purpose
แสดง bar chart กระจายของ fraud score เป็น 10 bucket (0-10, 11-20, ..., 91-100)

### Params
| Param | Type | Default | Description |
|---|---|---|---|
| `since` | ISO timestamp | 24 hours ago | เริ่มนับตั้งแต่เมื่อไหร่ |
| `until` | ISO timestamp | now | สิ้นสุดถึงเมื่อไหร่ |

### SQL

```sql
SELECT
    CASE
        WHEN score * 100 <  10 THEN '0-10'
        WHEN score * 100 <  20 THEN '11-20'
        WHEN score * 100 <  30 THEN '21-30'
        WHEN score * 100 <  40 THEN '31-40'
        WHEN score * 100 <  50 THEN '41-50'
        WHEN score * 100 <  60 THEN '51-60'
        WHEN score * 100 <  70 THEN '61-70'
        WHEN score * 100 <  80 THEN '71-80'
        WHEN score * 100 <  90 THEN '81-90'
        ELSE                        '91-100'
    END AS range,
    COUNT(*) AS count
FROM fraud_scores
WHERE scored_at >= ? AND scored_at < ?
GROUP BY range
ORDER BY MIN(score)
```

### Response
```json
{
  "items": [
    { "range": "0-10",  "count": 45 },
    { "range": "11-20", "count": 28 },
    { "range": "21-30", "count": 15 },
    { "range": "31-40", "count": 12 },
    { "range": "41-50", "count": 9 },
    { "range": "51-60", "count": 7 },
    { "range": "61-70", "count": 5 },
    { "range": "71-80", "count": 4 },
    { "range": "81-90", "count": 3 },
    { "range": "91-100","count": 2 }
  ],
  "totalScored": 130,
  "windowStart": "2026-07-22T14:00:00Z",
  "windowEnd":   "2026-07-23T14:00:00Z"
}
```

---

## 4. `GET /api/admin/security-events` — Security event log

**Portal usage:** AdminSecurity page — tab **Security Events**
**Status:** 🟡 ไม่มี dedicated table แต่ derive จาก `audit_logs` (V11__audit_log.sql) ได้
**Controller:** สร้างใหม่ `SecurityEventsController.java` ใน `com.example.switching.security.controller`

### Purpose
แสดง event ที่เกี่ยวกับความปลอดภัย: login/logout/MFA/key rotation/break-glass/privilege escalation

### Approach (แนะนำ)
Filter `audit_logs` โดยใช้ `event_type` — ไม่ต้องสร้าง table ใหม่

### SQL

```sql
SELECT audit_id, event_type, actor_username, ip_address, occurred_at, outcome, details
FROM audit_logs
WHERE event_type IN (
    'LOGIN_SUCCESS', 'LOGIN_FAILED', 'MFA_CHALLENGE', 'MFA_SUCCESS', 'MFA_FAILED',
    'KEY_ROTATION', 'PRIVILEGE_ESCALATION', 'PRIVILEGE_ESCALATION_DENIED',
    'SESSION_REVOKED', 'BREAK_GLASS_ACCESS'
)
  AND (?::text IS NULL OR actor_username = ?)
  AND (?::timestamp IS NULL OR occurred_at >= ?)
ORDER BY occurred_at DESC
LIMIT ? OFFSET ?
```

### Params
| Param | Type | Default | Description |
|---|---|---|---|
| `type` | string (CSV) | all security types | Filter event types |
| `actor` | string | none | Filter by username |
| `since` | ISO timestamp | 7 days ago | Start time |
| `limit`, `offset` | int | 100, 0 | Pagination |

### Response
```json
{
  "items": [
    {
      "eventId": "se1",
      "type": "LOGIN_SUCCESS",
      "actor": "Nattapong Srisuk",
      "ip": "192.168.10.5",
      "timestamp": "2026-07-16T14:00:00Z",
      "outcome": "SUCCESS",
      "details": { }
    }
  ],
  "totalItems": 1
}
```

### Role guard
`@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'AUDITOR')")`

### Migration ที่ควรทำ (optional)
เพิ่ม index สำหรับ query นี้:
```sql
CREATE INDEX IF NOT EXISTS idx_audit_security_events
    ON audit_logs(event_type, occurred_at DESC)
    WHERE event_type IN ('LOGIN_SUCCESS', 'LOGIN_FAILED', 'MFA_CHALLENGE', ...);
```

---

## 5. `GET /v1/operator/report-delivery-history` — Delivery log

**Portal usage:** Reports page — tab **Delivery History**
**Status:** ✅ Table `report_delivery_run` + `report_delivery_audit` มีอยู่แล้ว (V94__scheduled_report_delivery.sql)
**Controller:** เพิ่มใน [ReportScheduleController.java](src/main/java/com/example/switching/reportdelivery/ReportScheduleController.java) หรือแยก `ReportDeliveryHistoryController`

### Purpose
แสดง log ของการส่ง report แต่ละครั้ง (สำเร็จ/ล้มเหลว/กำลังทำ)

### SQL

```sql
SELECT r.run_id, s.report_name, r.executed_at, r.status,
       s.format, s.recipient_email, r.artifact_id, r.error_message
FROM report_delivery_run r
JOIN report_delivery_schedule s ON r.schedule_id = s.schedule_id
WHERE (?::text IS NULL OR r.status = ?)
ORDER BY r.executed_at DESC
LIMIT ? OFFSET ?
```

### Params
| Param | Type | Default | Description |
|---|---|---|---|
| `status` | string | all | SUCCESS / FAILED / IN_PROGRESS |
| `since` | ISO timestamp | 30 days ago | |
| `limit`, `offset` | int | 100, 0 | |

### Response
```json
{
  "items": [
    {
      "runId": "run-001",
      "reportName": "Daily Transaction Summary",
      "date": "2026-07-16T06:00:00Z",
      "status": "SUCCESS",
      "format": "PDF",
      "recipient": "ops@laofp.la",
      "artifactId": "art-abc123",
      "errorMessage": null
    }
  ]
}
```

### Portal features ที่เกี่ยวข้อง
- Download button → ใช้ `artifactId` ยิงไป `GET /v1/reports/download/{artifactId}` (มีอยู่แล้ว)
- Retry button (สำหรับ FAILED) → ต้องมี `POST /v1/operator/report-delivery-runs/{runId}/retry` (endpoint แยก, optional)

---

## 6. `GET /v1/aml/reports` — AML report catalog

**Portal usage:** AmlCompliance page — tab **Reports**
**Status:** 🟡 ต้องเลือก 1 ใน 2 option

### Option A (แนะนำ): ใช้ table เดิม + เพิ่ม column
เพิ่ม `category` ใน `report_delivery_schedule` แล้ว filter category = 'AML/STR/SANCTIONS'

```sql
ALTER TABLE report_delivery_schedule ADD COLUMN IF NOT EXISTS category VARCHAR(30);

-- Backfill existing rows
UPDATE report_delivery_schedule SET category = 'OPERATIONS' WHERE category IS NULL;

-- Query
SELECT schedule_id, report_name, category, updated_at, status, recipient_email
FROM report_delivery_schedule
WHERE category IN ('STR', 'AML', 'SANCTIONS', 'LEGAL_HOLD')
ORDER BY updated_at DESC;
```

### Option B: สร้าง table ใหม่ `aml_report`
แยก AML compliance reports ออกจาก operational reports โดยสิ้นเชิง

```sql
CREATE TABLE aml_report (
    report_id      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_name    VARCHAR(200) NOT NULL,
    category       VARCHAR(30) NOT NULL,  -- STR | SANCTIONS | AML_SUMMARY | LEGAL_HOLD
    submitted_at   TIMESTAMP(3),
    submitted_by   VARCHAR(60),
    status         VARCHAR(20) NOT NULL,  -- READY | SUBMITTED | REJECTED
    recipient      VARCHAR(200),          -- e.g., "BOL AML Division"
    reference_id   VARCHAR(80),           -- STR ID or evidence ID
    artifact_id    VARCHAR(80),           -- for download
    created_at     TIMESTAMP(3) NOT NULL DEFAULT NOW()
);
```

### Response
```json
{
  "items": [
    {
      "reportId": "aml-001",
      "name": "STR Submission Package",
      "category": "STR",
      "date": "2026-07-15T00:00:00Z",
      "status": "SUBMITTED",
      "recipient": "BOL AML Division",
      "artifactId": "aml-art-abc"
    }
  ]
}
```

### Role guard
`@PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'OPS_ADMIN', 'RISK_OFFICER', 'AUDITOR')")`

---

## 7. `GET /api/fees/decisions` — Fee decision history

**Portal usage:** FeeManagement page — tab **History**
**Status:** ✅ Table `fee_assessment` มีอยู่แล้ว (V65__tariff_fee_governance.sql)
**Controller:** เพิ่มใน [TariffOperationsController.java](src/main/java/com/example/switching/fees/TariffOperationsController.java) หรือแยก `FeeDecisionsController`

### Purpose
แสดง log ของทุกการคำนวณ fee (จาก `POST /api/operations/tariffs/assess`) เพื่อ audit และ debug

### SQL

```sql
SELECT assessment_id, transaction_reference, participant_code, message_type,
       gross_fee, promotion_discount, net_fee, currency,
       tariff_rule_id, tariff_version_id, assessed_at, assessed_by
FROM fee_assessment
WHERE (?::text IS NULL OR participant_code = ?)
  AND (?::timestamp IS NULL OR assessed_at >= ?)
ORDER BY assessed_at DESC
LIMIT ? OFFSET ?
```

### Params
| Param | Type | Default | Description |
|---|---|---|---|
| `participant` | string | none | Filter by participantCode |
| `since` | ISO timestamp | 7 days ago | |
| `messageType` | string | none | QR / R2P / BILL_PAYMENT / CROSS_BORDER |
| `limit`, `offset` | int | 100, 0 | |

### Response
```json
{
  "items": [
    {
      "decisionId": "dec-001",
      "transactionRef": "TXN-20260722-001",
      "participant": "SCB",
      "messageType": "QR",
      "grossFee": 10.00,
      "promotionDiscount": 5.00,
      "netFee": 5.00,
      "currency": "LAK",
      "ruleId": "FEE-QR-001",
      "assessedAt": "2026-07-22T14:23:01Z"
    }
  ]
}
```

---

## 8. `GET /api/fees/exceptions` — Fee exceptions (waivers/overrides)

**Portal usage:** FeeManagement page — tab **Exceptions**
**Status:** 🔴 ไม่มี dedicated table — ต้องเลือก 1 ใน 2 option

### Option A (approximation): ใช้ `fee_assessment` filter
กรอง rows ที่มี promotion discount หรือ net_fee = 0 (แสดง waivers ที่เกิดขึ้นจริง แต่ไม่ใช่ policy exception)

```sql
SELECT * FROM fee_assessment
WHERE promotion_discount > 0 OR net_fee = 0
ORDER BY assessed_at DESC;
```

### Option B (แนะนำ): สร้าง table ใหม่ `fee_exception` (policy-level)

Fee exception = การอนุมัติให้ participant/rule ได้รับการยกเว้นจาก tariff ตามปกติ (เช่น zero-fee waiver สำหรับ campaign)

```sql
CREATE TABLE fee_exception (
    exception_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tariff_rule_id   UUID NOT NULL REFERENCES tariff_rule(rule_id),
    participant_code VARCHAR(32),                    -- NULL = ครอบทุก participant
    message_type     VARCHAR(30),                    -- NULL = ครอบทุก type
    override_type    VARCHAR(20) NOT NULL,           -- WAIVER | FIXED | PERCENTAGE
    override_value   DECIMAL(20,4),                  -- ค่าที่ใช้แทน (0 = ฟรี, หรือ % override)
    reason           TEXT NOT NULL,
    requested_by     VARCHAR(60) NOT NULL,
    requested_at     TIMESTAMP(3) NOT NULL DEFAULT NOW(),
    approved_by      VARCHAR(60),
    approved_at      TIMESTAMP(3),
    status           VARCHAR(20) NOT NULL,           -- PENDING | APPROVED | REJECTED | ACTIVE | EXPIRED
    valid_from       TIMESTAMP(3),
    valid_until      TIMESTAMP(3),

    CONSTRAINT chk_fee_exception_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ACTIVE', 'EXPIRED')),
    CONSTRAINT chk_fee_exception_override_type
        CHECK (override_type IN ('WAIVER', 'FIXED', 'PERCENTAGE'))
);

CREATE INDEX idx_fee_exception_status ON fee_exception(status, requested_at DESC);
CREATE INDEX idx_fee_exception_participant ON fee_exception(participant_code, status);
```

### Endpoints (full CRUD + approval flow)
```
GET  /api/fees/exceptions?status=PENDING       — list
POST /api/fees/exceptions                       — request new exception
POST /api/fees/exceptions/{id}/approve          — approve (maker-checker)
POST /api/fees/exceptions/{id}/reject           — reject
POST /api/fees/exceptions/{id}/activate         — apply to production
```

### Response
```json
{
  "items": [
    {
      "exceptionId": "exc-001",
      "ruleId": "FEE-QR-091",
      "ruleName": "Partner Zero Fee",
      "participant": "PARTNER-A",
      "messageType": "QR",
      "overrideType": "WAIVER",
      "overrideValue": 0.00,
      "reason": "Q3 marketing campaign — approved by BoD",
      "requestedBy": "ops.admin@laofp.la",
      "requestedAt": "2026-07-20T10:00:00Z",
      "status": "PENDING",
      "validFrom": "2026-08-01T00:00:00Z",
      "validUntil": "2026-10-31T23:59:59Z"
    }
  ]
}
```

### Integration with fee assessment
ตอน `POST /api/operations/tariffs/assess` engine ต้องเช็ค `fee_exception` ที่ `status='ACTIVE'` ก่อน apply tariff ปกติ

---

## 🎯 Implementation Order (แนะนำ)

### Wave 1 — 5 endpoints, ประมาณ 1-2 วัน
- #1, #2, #3, #5, #7 (ทั้งหมด table พร้อม, ไม่ต้องแก้ schema)

### Wave 2 — 2 endpoints, ประมาณ 1 วัน
- #4 (add index + controller filter `audit_logs`)
- #6 (add column `category` + controller)

### Wave 3 — 1 endpoint, ประมาณ 2-3 วัน
- #8 (สร้าง table ใหม่ + full CRUD + approval flow + integrate กับ FeeAssessmentService)

---

## 📝 Checklist ต่อ endpoint

- [ ] Controller class + `@RestController` + `@RequestMapping`
- [ ] `@PreAuthorize` role guard ที่เหมาะสม
- [ ] JdbcTemplate query (follow pattern จาก `RiskAlertsController`)
- [ ] Response format: `{ items, totalItems, returnedItems }` (compatible กับ portal normalizer)
- [ ] Cap `limit` ที่ 500
- [ ] Cap `offset` ที่ 0 minimum
- [ ] Handle nullable filter params ด้วย `?::type IS NULL OR column = ?`
- [ ] Test ผ่าน `curl` + mTLS ก่อน integrate กับ portal
- [ ] Update [API_ENDPOINTS.txt](API_ENDPOINTS.txt) หลัง deploy

---

## 📁 File placement guide

| Endpoint | Directory |
|---|---|
| #1 | `src/main/java/com/example/switching/operations/controller/` (แก้ existing) |
| #2, #3 | `src/main/java/com/example/switching/risk/controller/` (new files) |
| #4 | `src/main/java/com/example/switching/security/controller/` (new file) |
| #5 | `src/main/java/com/example/switching/reportdelivery/` (new file) |
| #6 | `src/main/java/com/example/switching/aml/controller/` (new file) |
| #7, #8 | `src/main/java/com/example/switching/fees/` (new files) |

---

**Generated:** 2026-07-23
**Portal ref:** [API_INTEGRATION_PROGRESS.md](../LAOFP_Portal/SMOS/API_INTEGRATION_PROGRESS.md)
