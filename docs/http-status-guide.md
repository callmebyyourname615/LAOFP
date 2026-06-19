# HTTP Status Code Guide — Switching API Test Runner

> คู่มืออ่านผล `./scripts/run_tests.sh`
> อธิบายว่าแต่ละ HTTP status code ที่ปรากฏใน output หมายความว่าอะไร
> และควร investigate อะไรเมื่อได้ผลไม่ตรงที่คาด

---

## Quick Reference Table

| Code | ชื่อ | ความหมายในระบบนี้ | เจอใน TC |
|------|------|-------------------|----------|
| `200` | OK | ทำสำเร็จ, resource ถูกสร้าง/อัปเดต/ดึงมา | TC-030, 040, 042, 051 ฯลฯ |
| `201` | Created | resource ถูกสร้างใหม่ (บาง endpoint) | TC-020, 021, 022, 023 |
| `400` | Bad Request | request ผิดรูปแบบหรือ field ขาด | TC-033, 046, 101 |
| `401` | Unauthorized | ไม่มี API key หรือ key ไม่ถูกต้อง | TC-002, 010, 014 |
| `403` | Forbidden | มี API key แต่ role ไม่มีสิทธิ์ | TC-011, 013, 082, 102 |
| `404` | Not Found | ไม่พบ resource ที่ขอ | TC-032, 047, 081 |
| `409` | Conflict | มีข้อมูลซ้ำ หรือ state ขัดแย้ง | TC-045, 053 |
| `422` | Unprocessable | format ถูก แต่ business rule ไม่ผ่าน | TC-045 (บางกรณี) |
| `429` | Too Many Requests | ยิง API เกิน rate limit (100 req/min) | TC-070, 071 |
| `500` | Internal Server Error | server เกิด error ที่ไม่คาดคิด | ไม่ควรเจอ |
| `503` | Service Unavailable | downstream/connector ไม่พร้อม | ไม่ควรเจอ |
| `000` | No Response | server ไม่ตอบ (ไม่ได้ start หรือ port ผิด) | ทุก TC ถ้า server down |

---

## รายละเอียดแต่ละ Code

---

### `000` — No Response (Server Unreachable)

```
❌ FAIL TC-001  Actuator /health is public and returns UP
                expected HTTP 200, got HTTP 000
```

**หมายความว่า:** `curl` ไม่ได้รับ response ใดๆ เลย — server ไม่ได้ทำงานอยู่

**สาเหตุที่พบบ่อย:**
- ยังไม่ได้ start server
- Port 8080 ถูกใช้งานโดย process อื่น
- `BASE_URL` ชี้ไปผิด URL

**วิธีแก้:**
```bash
# ตรวจ port
lsof -ti:8080

# Start server
set -a && source .env && set +a
./mvnw spring-boot:run > /tmp/app.log 2>&1 &

# รอ ~15 วิ แล้วลอง run test ใหม่
```

---

### `200` — OK

```
✅ PASS TC-030  Create JSON inquiry → 200  (HTTP 200)
    ↳
      {
        "inquiryRef": "INQ-1747...",
        "status": "ELIGIBLE",
        ...
      }
```

**หมายความว่า:** request สำเร็จ ระบบประมวลผลครบถ้วน

**เจอใน context ไหน:**

| TC | Endpoint | ความหมายของ 200 |
|----|----------|-----------------|
| TC-030 | `POST /api/inquiries` | inquiry ถูกสร้าง status=ELIGIBLE |
| TC-040 | `POST /api/transfers` | transfer ถูกรับเข้าระบบ status=RECEIVED |
| TC-041 | `GET /api/transfers/{ref}` | ดึง transfer สำเร็จ (ตรวจ status field แยก) |
| TC-051 | `POST /api/transfers` (idempotency) | คืน transfer เดิมกลับมา ไม่สร้างใหม่ |
| TC-060/064 | `PATCH /api/connector-configs/...` | อัปเดต connector config สำเร็จ |
| TC-100/101 | `POST /api/iso20022/...` | **ISO protocol** — 200 แม้ request ผิด เพราะ error อยู่ใน XML body |

> ⚠️ **ISO Endpoints เป็นกรณีพิเศษ:** `/api/iso20022/pacs008` และ `/api/iso20022/acmt023`
> ตอบกลับด้วย HTTP 200 เสมอ แต่ error จะอยู่ใน XML body (PACS.002 RJCT หรือ ACMT.024 UNKNOWN)
> นี่คือ behavior ตาม ISO 20022 standard — **ไม่ใช่ bug**

---

### `201` — Created

```
✅ PASS TC-023  Create routing rule BANK_A→BANK_B → 200|201  (HTTP 201)
    ↳
      {
        "routeCode": "ROUTE_A_TO_B_...",
        "sourceBank": "BANK_A",
        ...
      }
```

**หมายความว่า:** resource ใหม่ถูกสร้างสำเร็จ (บาง endpoint ตอบ 201 แทน 200)

**เจอใน:** TC-020, 021, 022, 023 (admin setup — participant, connector, routing rule)

> script ยอมรับทั้ง `200` และ `201` สำหรับ create operations เพราะ Spring Boot
> บาง version คืน 200 บาง version คืน 201 — ทั้งสองถูกต้อง

---

### `400` — Bad Request

```
✅ PASS TC-033  Create inquiry with missing fields → 400 REQ-001  (HTTP 400)
    ↳ { "errorCode": "REQ-001", "error": "BAD_REQUEST", "message": "Request validation failed" }
```

**หมายความว่า:** request ที่ส่งมามีปัญหา ระบบปฏิเสธก่อนประมวลผล

**Error codes ที่เจอ:**

| errorCode | สาเหตุ | เจอใน |
|-----------|--------|-------|
| `REQ-001` | field ขาด หรือ validation ไม่ผ่าน | TC-033, 046 |
| `REQ-002` | JSON malformed (parse ไม่ได้) | — |
| `INQ-002` | inquiry validation ไม่ผ่าน (amount/currency ไม่ตรง) | — |

**ถ้าได้ 400 โดยไม่คาด:**
- ตรวจ `↳ Response:` ว่า field ไหนผิด
- ตรวจ `message` และ `details` ใน error body
- ตรวจว่า `BANK_A`, `BANK_B` participant ถูกสร้างแล้ว (TC-020/021 ผ่านหรือเปล่า)

---

### `401` — Unauthorized

```
✅ PASS TC-002  Ops /health without API key → 401  (HTTP 401)
```

**หมายความว่า:** ไม่มี `X-API-Key` header หรือ key ไม่มีในระบบ

**เจอใน:** TC-002, TC-010, TC-014

**ถ้าได้ 401 โดยไม่คาด (test ที่ควร pass):**
- ตรวจว่า key ถูกต้อง: `sk-admin-switching-2026`, `sk-ops-switching-2026`, etc.
- ตรวจว่า API key auth เปิดอยู่: `API_KEY_AUTH_ENABLED=true` ใน `.env`
- ตรวจ table `api_keys` ใน DB ว่า key ถูก seed แล้ว (migration V14)

---

### `403` — Forbidden

```
✅ PASS TC-011  BANK key on /operations/* → 403  (HTTP 403)
```

**หมายความว่า:** API key ถูกต้องและพบในระบบ **แต่ role ไม่มีสิทธิ์** เข้า endpoint นั้น

**Role matrix:**

| Role | สิ่งที่ทำได้ | สิ่งที่ทำไม่ได้ |
|------|------------|----------------|
| `BANK` | สร้าง inquiry/transfer, ส่ง ISO XML | เข้า `/api/operations/*`, `/api/outbox-events` |
| `OPS` | อ่านข้อมูลทุกอย่าง, outbox | สร้าง participant/routing/connector |
| `ADMIN` | ทุกอย่าง | — |

**เจอใน:** TC-011 (BANK→ops), TC-013 (OPS→create), TC-082 (BANK→outbox), TC-102 (OPS→ISO)

---

### `404` — Not Found

```
✅ PASS TC-032  GET non-existent inquiry → 404 INQ-001  (HTTP 404)
    ↳ { "errorCode": "INQ-001", "error": "NOT_FOUND", "message": "Inquiry not found" }
```

**หมายความว่า:** resource ที่ขอไม่มีอยู่ใน DB

**Error codes ที่เจอ:**

| errorCode | หมายความว่า | เจอใน |
|-----------|------------|-------|
| `INQ-001` | ไม่พบ inquiry ตาม inquiryRef | TC-032 |
| `TRF-001` | ไม่พบ transfer ตาม transferRef | TC-047 |
| `OUT-005` | ไม่พบ outbox event ตาม id | TC-081 |
| `PRT-001` | ไม่พบ participant ตาม bankCode | — |
| `CON-001` | ไม่พบ connector config | — |

**ถ้าได้ 404 โดยไม่คาด:**
- TC-031 (GET inquiry): inquiry ไม่ถูกสร้าง → ตรวจว่า TC-030 ผ่านหรือเปล่า
- TC-042 (GET transfer): transfer ไม่ถูกสร้าง → ตรวจว่า TC-040 ผ่านหรือเปล่า
- TC-024 (resolve route): routing rule ไม่มี → run TC-023 ก่อน

---

### `409` — Conflict

```
✅ PASS TC-045  Reuse already-used inquiry → 409 INQ-003  (HTTP 409)
    ↳ { "errorCode": "INQ-003", "error": "CONFLICT", "message": "Inquiry already used by transfer" }
```

**หมายความว่า:** state ขัดแย้ง ทำซ้ำไม่ได้

**Error codes ที่เจอ:**

| errorCode | สาเหตุ | เจอใน |
|-----------|--------|-------|
| `INQ-003` | inquiry ถูก transfer อื่นใช้ไปแล้ว | TC-045 |
| `TRF-002` | idempotency key เดิม แต่ payload ต่างกัน | TC-053 |
| `INF-DB-002` | DB unique constraint violation | TC-020/021/022 ถ้า record ซ้ำ |
| `OUT-004` | outbox event ไม่อยู่ในสถานะที่ retry ได้ | — |
| `PRT-003` | participant bankCode ซ้ำ | — |
| `RTE-002` | routing rule routeCode ซ้ำ | — |
| `CON-002` | connector name ซ้ำ | — |

> **TC-020/021/022** ยอมรับ 409 เป็นเรื่องปกติ — หมายความว่า record มีอยู่แล้วจาก run ก่อนหน้า
> ไม่ใช่ failure เพราะ test ใช้ `check_status "..." "200|201|409"`

---

### `422` — Unprocessable Entity

```
✅ PASS TC-045  ... → 422 (บางกรณี)
    ↳ { "errorCode": "PRT-002", "error": "UNPROCESSABLE_ENTITY" }
```

**หมายความว่า:** format และ field ถูกต้องหมด แต่ business rule ไม่ผ่าน

**Error codes ที่เจอ:**

| errorCode | สาเหตุ |
|-----------|--------|
| `PRT-002` | participant ไม่ได้อยู่ในสถานะ ACTIVE |
| `RTE-001` | ไม่พบ routing rule สำหรับคู่ bank นี้ |

**ถ้าได้ 422 โดยไม่คาด:**
- BANK_A หรือ BANK_B status ไม่ใช่ `ACTIVE` → ตรวจ TC-020/021
- ไม่มี routing rule → ตรวจ TC-023

---

### `429` — Too Many Requests

```
✅ PASS TC-070  Rate limit 429 triggered at request 92 (≤ 100 POST/min ✅)
✅ PASS TC-071  429 response body has errorCode=REQ-004 ✅
    ↳ { "errorCode": "REQ-004", "error": "TOO_MANY_REQUESTS", "message": "Rate limit exceeded" }
```

**หมายความว่า:** API key นั้นส่ง POST/PUT/PATCH/DELETE เกิน 100 ครั้งใน 1 นาที

**พฤติกรรมของ rate limit:**
- นับเฉพาะ **write methods** (POST, PUT, PATCH, DELETE)
- **GET ไม่นับ** (TC-072 verify ข้อนี้)
- bucket refill ทุก 1 นาที
- แต่ละ API key มี bucket แยกกัน

**หมายเหตุ:** TC-070–072 วิ่งสุดท้ายใน script เพื่อไม่ให้รบกวน test section อื่น
หลัง run เสร็จ `BANK_A_KEY` จะถูก rate limit อยู่ประมาณ 1 นาที

---

### `500` — Internal Server Error

```
❌ FAIL TC-094  Operations audit-logs list → 200  (HTTP 500)
    ↳ { "errorCode": "SYS-001", "error": "INTERNAL_SERVER_ERROR", "message": "Internal server error" }
```

**หมายความว่า:** server เกิด exception ที่ไม่ได้ handle หรือ error ใน layer ลึก

**สาเหตุที่พบบ่อย:**

| สาเหตุ | วิธีตรวจ |
|--------|----------|
| DB connection ขาด | ดู log `tail -f /tmp/app.log` |
| SQL query ผิด | ดู `caused by` ใน server log |
| Exception ไม่ได้ register ใน GlobalExceptionHandler | ดู exception class ใน log |
| `MESSAGE_CRYPTO_KEY_BASE64` ไม่ถูกต้อง | ตรวจ `.env` |

**วิธี debug:**
```bash
# ดู server log แบบ real-time
tail -f /tmp/app.log | grep -E "ERROR|Exception|Caused"

# ดู request ที่ fail
curl -v "$BASE_URL/api/operations/audit-logs" -H "X-API-Key: sk-ops-switching-2026"
```

---

### `503` — Service Unavailable

```
↳ { "errorCode": "NET-001", "error": "SERVICE_UNAVAILABLE", "message": "Downstream connection failed" }
```

**หมายความว่า:** ติดต่อ downstream bank ไม่ได้ (connector layer)

**ในระบบนี้ไม่ควรเจอ** เพราะใช้ `MockBankConnector` ซึ่งทำงานใน process เดียวกัน
ถ้าเจอ 503 ให้ตรวจว่า connector config `enabled=true` และ `connectorType=MOCK`

---

## Flow ของ Test Script และ Expected HTTP Codes

```
Section 1  Health
  TC-001  GET /actuator/health                    → 200 ✅
  TC-002  GET /api/operations/health (no key)     → 401 ✅
  TC-003  GET /api/operations/health (OPS key)    → 200 ✅

Section 2  Auth
  TC-010  GET /api/participants (no key)           → 401 ✅
  TC-011  GET /api/operations/* (BANK key)         → 403 ✅
  TC-012  GET /api/participants (OPS key)          → 200 ✅
  TC-013  POST /api/participants (OPS key)         → 403 ✅  ← OPS ไม่มีสิทธิ์ write
  TC-014  GET /api/participants (invalid key)      → 401 ✅

Section 3  Admin Setup
  TC-020  POST /api/participants BANK_A            → 200|201|409 ✅
  TC-021  POST /api/participants BANK_B            → 200|201|409 ✅
  TC-022  POST /api/connector-configs              → 200|201|409 ✅
  TC-023  POST /api/routing-rules                  → 200|201 ✅
  TC-024  GET /api/routing-rules/resolve           → 200 ✅

Section 4  JSON Inquiry
  TC-030  POST /api/inquiries                      → 200 ✅  ← ได้ inquiryRef
  TC-031  GET /api/inquiries/{ref}                 → 200 ✅  ← status=ELIGIBLE
  TC-032  GET /api/inquiries/NOTFOUND              → 404 ✅  ← INQ-001
  TC-033  POST /api/inquiries (missing fields)     → 400 ✅  ← REQ-001

Section 5  Transfer Happy Path
  TC-040  POST /api/transfers                      → 200 ✅  ← status=RECEIVED
  TC-041  GET /api/transfers/{ref} (after 2s)      → 200 ✅  ← status=SUCCESS
  TC-042  GET /api/transfers/{ref}                 → 200 ✅
  TC-043  GET /api/transfers/{ref}/trace           → 200 ✅  ← inquiryRef ≠ null
  TC-044  GET /api/operations/transfers/.../trace  → 200 ✅  ← hasInquiry=true
  TC-045  POST /api/transfers (reuse inquiry)      → 409 ✅  ← INQ-003
  TC-046  POST /api/transfers (no inquiryRef)      → 400 ✅  ← REQ-001
  TC-047  GET /api/transfers/NOTFOUND              → 404 ✅  ← TRF-001

Section 6  Idempotency
  TC-050  POST /api/inquiries (new)                → 200 ✅
  TC-051  POST /api/transfers (idem key)           → 200 ✅  ← ได้ transferRef
  TC-052  POST /api/transfers (same request)       → 200 ✅  ← ได้ transferRef เดิม
  TC-053  POST /api/transfers (diff payload)       → 409 ✅  ← TRF-002

Section 7  Force Reject
  TC-060  PATCH connector forceReject=true         → 200 ✅
  TC-061  POST /api/inquiries (new)                → 200 ✅
  TC-062  POST /api/transfers                      → 200 ✅  ← RECEIVED
  TC-063  GET /api/transfers/{ref} (after 2s)      → 200 ✅  ← status=FAILED ⚠️
  TC-064  PATCH connector forceReject=false        → 200 ✅  ← cleanup

Section 8  Outbox
  TC-080  GET /api/outbox-events                   → 200 ✅
  TC-081  POST /api/outbox-events/999999/retry     → 404 ✅  ← OUT-005
  TC-082  GET /api/outbox-events (BANK key)        → 403 ✅
  TC-083  GET /api/operations/outbox-failures      → 200 ✅
  TC-084  GET /api/operations/outbox-stuck         → 200 ✅

Section 9  Operations
  TC-090  GET /api/operations/dashboard-summary    → 200 ✅
  TC-091  GET /api/operations/transactions         → 200 ✅
  TC-092  GET /api/operations/transfers            → 200 ✅
  TC-093  GET /api/operations/iso-messages         → 200 ✅
  TC-094  GET /api/operations/audit-logs           → 200 ✅
  TC-095  GET /api/operations/connectors/health    → 200 ✅
  TC-096  GET /api/operations/bank-status          → 200 ✅

Section 10  Transfer List
  TC-110  GET /api/transfers                       → 200 ✅
  TC-111  GET /api/operations/transfers?status=... → 200 ✅

Section 11  ISO XML
  TC-100  POST /api/iso20022/pacs008 (malformed)   → 200|400|500 ✅  ← ISO rejection
  TC-101  POST /api/iso20022/acmt023 (no bankcode) → 200|400|422 ✅  ← ISO rejection
  TC-102  POST /api/iso20022/pacs008 (OPS key)     → 403 ✅
  TC-103  POST /api/iso20022/acmt023 (valid XML)   → 200 ✅  ← ACMT.024 XML body

Section 12  Metrics
  TC-120  GET /actuator/metrics                    → 200 หรือ 404 ✅  ← depends on config

Section 13  Rate Limiting
  TC-070  POST loop จน rate limit                  → 429 ✅  ← REQ-004
  TC-071  POST ต่อเมื่อ limit เต็ม                 → 429 ✅  ← errorCode=REQ-004
  TC-072  GET loop 10 ครั้ง                        → 200 ✅  ← ไม่ถูก rate limit
```

---

## อ่าน Output ของ Script

### PASS ปกติ
```
  ✅ PASS TC-030  Create JSON inquiry → 200  (HTTP 200)
    ↳
      {
        "inquiryRef": "INQ-...",
        "status": "ELIGIBLE"
      }
```

### FAIL — code ไม่ตรง
```
  ❌ FAIL TC-094  Operations audit-logs list → 200  expected HTTP 200, got HTTP 500
    ↳ Response: {"errorCode":"SYS-001","message":"Internal server error",...}
```
→ **สิ่งที่ต้องทำ:** เปิด server log ดู exception จริง

### FAIL — body ไม่ตรง
```
  ❌ FAIL TC-044b  Ops trace summary.hasInquiry = true  expected .summary.hasInquiry = 'true', got 'false'
```
→ **สิ่งที่ต้องทำ:** ตรวจว่า JSON trace fix (`findJsonPathInquiry`) ถูก deploy แล้ว

### SKIP — dependency ขาด
```
  ⏭  SKIP TC-041  Transfer status check — skipped (no transferRef)
```
→ **สิ่งที่ต้องทำ:** ดูว่า TC ก่อนหน้า (TC-040) ผ่านหรือเปล่า และทำไมไม่ได้ `transferRef`

### INFO / WARN
```
  ℹ  inquiryRef captured: INQ-1747...
  ⚠  Still RECEIVED after 2s — waiting 3 more seconds...
```
→ ไม่ใช่ failure แค่แจ้งสถานะระหว่าง run

---

## Debug Checklist เมื่อ test ไม่ผ่าน

```
[ ] server ทำงานอยู่?
      curl http://localhost:8080/actuator/health

[ ] .env ถูก source แล้ว?
      set -a && source .env && set +a

[ ] DB up และมีข้อมูล seed?
      mysql -u root -p switching_db -e "SELECT COUNT(*) FROM api_keys"

[ ] Section 3 (Admin setup) ผ่านก่อน section อื่น?
      TC-020 → 021 → 022 → 023 ต้องผ่านก่อน TC-030+

[ ] force_reject กลับเป็น false แล้ว?
      curl -X PATCH http://localhost:8080/api/connector-configs/MOCK_BANK_B_CONNECTOR \
        -H "Content-Type: application/json" \
        -H "X-API-Key: sk-admin-switching-2026" \
        -d '{"forceReject": false}'

[ ] rate limit หมดอายุแล้ว?
      รอ 1 นาทีถ้าเพิ่ง run TC-070

[ ] server log มี ERROR?
      tail -100 /tmp/app.log | grep -E "ERROR|Exception"
```
