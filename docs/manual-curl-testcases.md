# Switching API — Manual curl Test Cases

> ชุด testcase แบบ manual สำหรับยิงด้วย `curl` เอง
> Last updated: 2026-05-14 — Phase 3 complete (Rate Limiting, Near Real-time Dispatch, JSON Trace Fix)

---

## 0. Setup — ตั้งค่าก่อนยิงทุกครั้ง

```bash
export BASE_URL="http://localhost:8080"

# API Keys (seeded in V14__create_api_keys.sql)
export ADMIN_KEY="sk-admin-switching-2026"
export OPS_KEY="sk-ops-switching-2026"
export BANK_A_KEY="sk-bank-a-switching-2026"
export BANK_B_KEY="sk-bank-b-switching-2026"

# Test data
export RUN_ID="$(date +%s)"
export BANK_A="BANK_A"
export BANK_B="BANK_B"
export DEBTOR="010100000001"
export CREDITOR="020200000001"
export AMOUNT="150000.00"
export CURRENCY="LAK"

# Content-type helpers
export H_JSON="Content-Type: application/json"
export H_XML="Content-Type: application/xml"
```

> ⚠️ **inquiry ใช้ได้ครั้งเดียวเท่านั้น** — สร้าง inquiry ใหม่ทุกครั้งก่อน transfer แต่ละครั้ง

---

## 1. Health / Public Endpoints

### TC-001 — Actuator health เป็น public endpoint

```bash
curl -s "$BASE_URL/actuator/health" | jq .
```

Expected:
- HTTP `200`
- `"status": "UP"`

### TC-002 — Operations health ไม่มี API key ต้อง block

```bash
curl -i "$BASE_URL/api/operations/health"
```

Expected:
- HTTP `401`
- Body มี `SEC-001`

### TC-003 — Operations health ด้วย OPS key ต้องผ่าน

```bash
curl -i "$BASE_URL/api/operations/health" \
  -H "X-API-Key: $OPS_KEY"
```

Expected:
- HTTP `200`

---

## 2. API Key / Role Authorization

### TC-010 — ไม่มี API key บน protected endpoint → 401

```bash
curl -i "$BASE_URL/api/participants"
```

Expected:
- HTTP `401`

### TC-011 — BANK key เข้า operations ไม่ได้ → 403

```bash
curl -i "$BASE_URL/api/operations/dashboard-summary" \
  -H "X-API-Key: $BANK_A_KEY"
```

Expected:
- HTTP `403`
- Body มี `SEC-002`

### TC-012 — OPS key อ่าน participants ได้

```bash
curl -i "$BASE_URL/api/participants" \
  -H "X-API-Key: $OPS_KEY"
```

Expected:
- HTTP `200`

### TC-013 — OPS key สร้าง participant ไม่ได้ → 403

```bash
curl -i -X POST "$BASE_URL/api/participants" \
  -H "$H_JSON" \
  -H "X-API-Key: $OPS_KEY" \
  -d "{
    \"bankCode\": \"BANK_TEST_$RUN_ID\",
    \"bankName\": \"Test Bank\",
    \"participantType\": \"BANK\",
    \"country\": \"LA\",
    \"currency\": \"LAK\",
    \"status\": \"ACTIVE\"
  }"
```

Expected:
- HTTP `403`

### TC-014 — Invalid API key → 401

```bash
curl -i "$BASE_URL/api/participants" \
  -H "X-API-Key: sk-fake-key-does-not-exist"
```

Expected:
- HTTP `401`

---

## 3. Admin Setup (ครั้งแรกหรือ DB ใหม่)

ถ้า DB มีข้อมูลอยู่แล้วจาก migrations จะได้ `409` ซึ่งปกติ

### TC-020 — สร้าง BANK_A participant

```bash
curl -i -X POST "$BASE_URL/api/participants" \
  -H "$H_JSON" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{
    \"bankCode\": \"$BANK_A\",
    \"bankName\": \"Source Test Bank\",
    \"participantType\": \"BANK\",
    \"country\": \"LA\",
    \"currency\": \"LAK\",
    \"status\": \"ACTIVE\"
  }"
```

Expected: `200` หรือ `409` ถ้ามีอยู่แล้ว

### TC-021 — สร้าง BANK_B participant

```bash
curl -i -X POST "$BASE_URL/api/participants" \
  -H "$H_JSON" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{
    \"bankCode\": \"$BANK_B\",
    \"bankName\": \"Receiver Test Bank\",
    \"participantType\": \"BANK\",
    \"country\": \"LA\",
    \"currency\": \"LAK\",
    \"status\": \"ACTIVE\"
  }"
```

Expected: `200` หรือ `409`

### TC-022 — สร้าง connector config สำหรับ BANK_B

```bash
curl -i -X POST "$BASE_URL/api/connector-configs" \
  -H "$H_JSON" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{
    \"connectorName\": \"MOCK_BANK_B_CONNECTOR\",
    \"bankCode\": \"$BANK_B\",
    \"connectorType\": \"MOCK\",
    \"timeoutMs\": 5000,
    \"enabled\": true,
    \"forceReject\": false,
    \"rejectReasonCode\": \"AC01\",
    \"rejectReasonMessage\": \"Mock reject\"
  }"
```

Expected: `200` หรือ `409`

### TC-023 — สร้าง routing rule BANK_A → BANK_B

```bash
curl -i -X POST "$BASE_URL/api/routing-rules" \
  -H "$H_JSON" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{
    \"routeCode\": \"ROUTE_A_TO_B_PACS008_$RUN_ID\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"messageType\": \"PACS_008\",
    \"connectorName\": \"MOCK_BANK_B_CONNECTOR\",
    \"priority\": 1,
    \"enabled\": true
  }"
```

Expected: `200`

### TC-024 — Resolve routing rule

```bash
curl -s "$BASE_URL/api/routing-rules/resolve?sourceBank=$BANK_A&destinationBank=$BANK_B&messageType=PACS_008" \
  -H "X-API-Key: $OPS_KEY" | jq .
```

Expected:
- HTTP `200`
- Body มี `connectorName: "MOCK_BANK_B_CONNECTOR"`

---

## 4. JSON Inquiry Flow

### TC-030 — สร้าง inquiry สำเร็จ

```bash
curl -s -X POST "$BASE_URL/api/inquiries" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"reference\": \"MANUAL-INQ-$RUN_ID\"
  }" | jq .
```

Expected:
- HTTP `200`
- Body มี `inquiryRef`

```bash
# บันทึก inquiryRef ที่ได้
export INQUIRY_REF="<วาง inquiryRef ที่นี่>"
```

### TC-031 — ดู inquiry ที่สร้างไว้

```bash
curl -s "$BASE_URL/api/inquiries/$INQUIRY_REF" \
  -H "X-API-Key: $BANK_A_KEY" | jq .
```

Expected:
- HTTP `200`
- `inquiryRef` ตรงกับ `$INQUIRY_REF`
- `status: "ELIGIBLE"`

### TC-032 — ดู inquiry ที่ไม่มี → 404

```bash
curl -i "$BASE_URL/api/inquiries/INQ-NOTFOUND-$RUN_ID" \
  -H "X-API-Key: $BANK_A_KEY"
```

Expected: HTTP `404`

### TC-033 — สร้าง inquiry ที่ field ไม่ครบ → 400

```bash
curl -i -X POST "$BASE_URL/api/inquiries" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"sourceBank\": \"$BANK_A\"}"
```

Expected:
- HTTP `400`
- Body มี `REQ-001`

---

## 5. JSON Transfer Flow (Happy Path)

> ⚠️ ต้องสร้าง inquiry ใหม่ก่อนทุกครั้ง — ใช้ `$INQUIRY_REF` จาก TC-030

### TC-040 — สร้าง transfer สำเร็จ

```bash
curl -s -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"inquiryRef\": \"$INQUIRY_REF\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"reference\": \"MANUAL-TRF-$RUN_ID\"
  }" | jq .
```

Expected:
- HTTP `200`
- Body มี `transferRef`
- `status: "RECEIVED"`

```bash
# บันทึก transferRef
export TRANSFER_REF="<วาง transferRef ที่นี่>"
```

### TC-041 — ตรวจ transfer status (near real-time — รอไม่นาน)

รอ ~2 วิ แล้วเช็ค — ถ้า near real-time ทำงาน ควรเป็น `SUCCESS` แล้ว

```bash
sleep 2

curl -s "$BASE_URL/api/transfers/$TRANSFER_REF" \
  -H "X-API-Key: $BANK_A_KEY" | jq '{transferRef, status, errorCode}'
```

Expected:
- `status: "SUCCESS"`
- ถ้ายังเป็น `RECEIVED` รอเพิ่มอีก 3 วิ แล้วยิงใหม่

### TC-042 — ดู transfer ด้วย GET

```bash
curl -s "$BASE_URL/api/transfers/$TRANSFER_REF" \
  -H "X-API-Key: $BANK_A_KEY" | jq .
```

Expected:
- HTTP `200`
- `transferRef` ตรง

### TC-043 — ดู transfer trace (JSON path — ต้อง hasInquiry: true)

```bash
curl -s "$BASE_URL/api/transfers/$TRANSFER_REF/trace" \
  -H "X-API-Key: $BANK_A_KEY" | jq '{
    status,
    inquiryRef,
    summary: .summary,
    timelineTypes: [.timeline[].eventCategory]
  }'
```

Expected:
- `summary.hasInquiry: true`  ✅ (ถ้า false แสดงว่า fallback ไม่ทำงาน)
- `timeline` มี `"JSON_INQUIRY"` อยู่ใน list
- `timelineEventCount` ≥ 5

### TC-044 — ดู operations trace

```bash
curl -s "$BASE_URL/api/operations/transfers/$TRANSFER_REF/trace" \
  -H "X-API-Key: $OPS_KEY" | jq '{
    status,
    warnings,
    summary,
    inquirySource: .inquiry.inquiryApiPath,
    timelineCount: (.timeline | length)
  }'
```

Expected:
- `warnings: []` (ไม่มี warning)
- `summary.hasInquiry: true`
- `inquiry.inquiryApiPath` ขึ้นต้นด้วย `/api/inquiries/` (ไม่ใช่ `/api/iso-inquiries/`)
- timeline count ≥ 5

### TC-045 — Reuse inquiry เดิม → 409/422

```bash
curl -i -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"inquiryRef\": \"$INQUIRY_REF\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\"
  }"
```

Expected:
- HTTP `409` (INQ-003 Inquiry already used)

### TC-046 — Transfer ไม่มี inquiryRef → 400

```bash
curl -i -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\"
  }"
```

Expected:
- HTTP `400`
- Body มี `REQ-001`

### TC-047 — Transfer ไม่มี → 404

```bash
curl -i "$BASE_URL/api/transfers/TRX-NOT-EXIST-$RUN_ID" \
  -H "X-API-Key: $BANK_A_KEY"
```

Expected:
- HTTP `404`
- Body มี `TRF-001`

---

## 6. Idempotency

> ⚠️ Idempotency test ต้องสร้าง inquiry ใหม่แยกต่างหาก (ไม่ใช้ `$INQUIRY_REF` เดิมที่ใช้แล้ว)

### TC-050 — สร้าง inquiry ใหม่สำหรับ idempotency test

```bash
curl -s -X POST "$BASE_URL/api/inquiries" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"reference\": \"IDEM-INQ-$RUN_ID\"
  }" | jq .
```

Expected: HTTP `200`, ได้ `inquiryRef` ใหม่

```bash
export IDEM_INQUIRY_REF="<วาง inquiryRef ที่นี่>"
export IDEM_KEY="MANUAL-IDEM-$RUN_ID"
```

### TC-051 — First transfer with idempotencyKey

```bash
curl -s -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"inquiryRef\": \"$IDEM_INQUIRY_REF\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"idempotencyKey\": \"$IDEM_KEY\"
  }" | jq '{transferRef, status}'
```

Expected:
- HTTP `200`
- ได้ `transferRef`

```bash
export IDEM_TRANSFER_REF="<วาง transferRef ที่นี่>"
```

### TC-052 — ส่ง request เดิมซ้ำ → ได้ transferRef เดิม

```bash
curl -s -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"inquiryRef\": \"$IDEM_INQUIRY_REF\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"idempotencyKey\": \"$IDEM_KEY\"
  }" | jq '{transferRef, status}'
```

Expected:
- HTTP `200`
- `transferRef` ต้องเท่ากับ `$IDEM_TRANSFER_REF`

### TC-053 — idempotencyKey เดิม แต่ payload ต่างกัน → 409

```bash
curl -i -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"inquiryRef\": \"$IDEM_INQUIRY_REF\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": 999.00,
    \"currency\": \"$CURRENCY\",
    \"idempotencyKey\": \"$IDEM_KEY\"
  }"
```

Expected:
- HTTP `409`
- Body มี `TRF-002`

---

## 7. Force Reject Flow (Transfer ถูก downstream ปฏิเสธ)

### TC-060 — เปิด force_reject บน connector

```bash
curl -i -X PATCH "$BASE_URL/api/connector-configs/MOCK_BANK_B_CONNECTOR" \
  -H "$H_JSON" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"forceReject\": true}"
```

Expected: HTTP `200`

### TC-061 — สร้าง inquiry ใหม่ (สำหรับ reject test)

```bash
curl -s -X POST "$BASE_URL/api/inquiries" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"reference\": \"REJECT-INQ-$RUN_ID\"
  }" | jq .
```

```bash
export REJECT_INQUIRY_REF="<วาง inquiryRef ที่นี่>"
```

### TC-062 — สร้าง transfer → ควร FAILED เพราะ downstream reject

```bash
curl -s -X POST "$BASE_URL/api/transfers" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"inquiryRef\": \"$REJECT_INQUIRY_REF\",
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"debtorAccount\": \"$DEBTOR\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\",
    \"reference\": \"REJECT-TRF-$RUN_ID\"
  }" | jq '{transferRef, status}'
```

```bash
export REJECT_TRANSFER_REF="<วาง transferRef ที่นี่>"
```

### TC-063 — ตรวจ status หลัง ~2 วิ → ต้อง FAILED

```bash
sleep 2

curl -s "$BASE_URL/api/transfers/$REJECT_TRANSFER_REF" \
  -H "X-API-Key: $BANK_A_KEY" | jq '{transferRef, status, errorCode, errorMessage}'
```

Expected:
- `status: "FAILED"`
- `errorCode: "EXT-001"` (downstream rejected)

### TC-064 — ปิด force_reject กลับเหมือนเดิม

```bash
curl -i -X PATCH "$BASE_URL/api/connector-configs/MOCK_BANK_B_CONNECTOR" \
  -H "$H_JSON" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"forceReject\": false}"
```

Expected: HTTP `200`

---

## 8. Rate Limiting

> Rate limit ทำงานกับ POST/PUT/PATCH/DELETE เท่านั้น (GET ไม่นับ)
> Default = 100 req/min ต่อ API Key หรือ IP
> ทดสอบโดยยิง POST เร็วๆ หลายครั้ง

### TC-070 — ยิง POST เยอะๆ อย่างรวดเร็ว → ต้องได้ 429

```bash
# สร้าง inquiry ก่อน (ใช้ reference เดิมซ้ำก็ได้ เพราะเราต้องการแค่ 429 จาก rate limit)
for i in $(seq 1 110); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/inquiries" \
    -H "$H_JSON" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{
      \"sourceBank\": \"$BANK_A\",
      \"destinationBank\": \"$BANK_B\",
      \"creditorAccount\": \"$CREDITOR\",
      \"amount\": $AMOUNT,
      \"currency\": \"$CURRENCY\"
    }")
  echo "Request $i: HTTP $STATUS"
  if [ "$STATUS" = "429" ]; then
    echo "✅ Rate limit triggered at request $i"
    break
  fi
done
```

Expected:
- Request แรกๆ ได้ `200`
- เมื่อเกิน 100 ครั้งใน 1 นาที → ได้ `429`

### TC-071 — ตรวจ response body ของ 429

```bash
# ยิง POST จนเกิน limit แล้วดู body
curl -s -X POST "$BASE_URL/api/inquiries" \
  -H "$H_JSON" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{
    \"sourceBank\": \"$BANK_A\",
    \"destinationBank\": \"$BANK_B\",
    \"creditorAccount\": \"$CREDITOR\",
    \"amount\": $AMOUNT,
    \"currency\": \"$CURRENCY\"
  }" | jq .
```

Expected (เมื่อ limit เต็ม):
- HTTP `429`
- Body มี `"errorCode": "REQ-004"`
- Body มี `"error": "TOO_MANY_REQUESTS"`

### TC-072 — GET ไม่ถูก rate limit

```bash
# GET ไม่นับ limit — ยิง 200 ครั้งต้องไม่ได้ 429
for i in $(seq 1 200); do
  STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/participants" \
    -H "X-API-Key: $OPS_KEY")
  if [ "$STATUS" = "429" ]; then
    echo "❌ GET ถูก rate limit ที่ request $i — ผิดปกติ"
    break
  fi
done
echo "✅ GET 200 ครั้งไม่ถูก rate limit"
```

Expected:
- ไม่เคยได้ `429`

---

## 9. Outbox / Dispatch / Operations

### TC-080 — List outbox events

```bash
curl -s "$BASE_URL/api/outbox-events" \
  -H "X-API-Key: $OPS_KEY" | jq '{count: length, first: .[0]}'
```

Expected: HTTP `200`

### TC-081 — Retry outbox ที่ไม่มี → 404

```bash
curl -i -X POST "$BASE_URL/api/outbox-events/999999999/retry" \
  -H "X-API-Key: $OPS_KEY"
```

Expected: HTTP `404`

### TC-082 — BANK key เข้า outbox ไม่ได้ → 403

```bash
curl -i "$BASE_URL/api/outbox-events" \
  -H "X-API-Key: $BANK_A_KEY"
```

Expected: HTTP `403`

### TC-083 — Operations outbox failures list

```bash
curl -s "$BASE_URL/api/operations/outbox-failures" \
  -H "X-API-Key: $OPS_KEY" | jq .
```

Expected: HTTP `200`

### TC-084 — Operations outbox stuck list

```bash
curl -s "$BASE_URL/api/operations/outbox-stuck" \
  -H "X-API-Key: $OPS_KEY" | jq .
```

Expected: HTTP `200`

---

## 10. Operations Query Smoke Tests

### TC-090 — Dashboard summary

```bash
curl -s "$BASE_URL/api/operations/dashboard-summary" \
  -H "X-API-Key: $OPS_KEY" | jq .
```

Expected: HTTP `200`

### TC-091 — Transactions list

```bash
curl -s "$BASE_URL/api/operations/transactions" \
  -H "X-API-Key: $OPS_KEY" | jq '{count: length}'
```

Expected: HTTP `200`

### TC-092 — Transfers list (ops)

```bash
curl -s "$BASE_URL/api/operations/transfers" \
  -H "X-API-Key: $OPS_KEY" | jq '{count: length}'
```

Expected: HTTP `200`

### TC-093 — ISO messages list

```bash
curl -s "$BASE_URL/api/operations/iso-messages" \
  -H "X-API-Key: $OPS_KEY" | jq '{count: length}'
```

Expected: HTTP `200`

### TC-094 — Audit logs list

```bash
curl -s "$BASE_URL/api/operations/audit-logs" \
  -H "X-API-Key: $OPS_KEY" | jq '{count: length}'
```

Expected: HTTP `200`

### TC-095 — Connector health

```bash
curl -s "$BASE_URL/api/operations/connectors/health" \
  -H "X-API-Key: $OPS_KEY" | jq .
```

Expected: HTTP `200`

### TC-096 — Bank status overview

```bash
curl -s "$BASE_URL/api/operations/bank-status" \
  -H "X-API-Key: $OPS_KEY" | jq .
```

Expected: HTTP `200`

---

## 11. ISO XML Smoke Tests

### TC-100 — PACS.008 malformed XML → clean error

```bash
curl -i -X POST "$BASE_URL/api/iso20022/pacs008" \
  -H "$H_XML" \
  -H "X-API-Key: $BANK_A_KEY" \
  -H "X-Bank-Code: $BANK_A" \
  --data-binary '<not-valid-xml>'
```

Expected: HTTP `400` หรือ `500`

### TC-101 — ACMT.023 ไม่มี X-Bank-Code header → error

```bash
curl -i -X POST "$BASE_URL/api/iso20022/acmt023" \
  -H "$H_XML" \
  -H "X-API-Key: $BANK_A_KEY" \
  --data-binary '<Document></Document>'
```

Expected: HTTP `400`

### TC-102 — OPS key ส่ง ISO message ไม่ได้ → 403

```bash
curl -i -X POST "$BASE_URL/api/iso20022/pacs008" \
  -H "$H_XML" \
  -H "X-API-Key: $OPS_KEY" \
  -H "X-Bank-Code: $BANK_A" \
  --data-binary '<Document></Document>'
```

Expected: HTTP `403`

### TC-103 — ISO inquiry (ACMT.023) smoke test

```bash
curl -i -X POST "$BASE_URL/api/iso20022/acmt023" \
  -H "$H_XML" \
  -H "X-API-Key: $BANK_A_KEY" \
  -H "X-Bank-Code: $BANK_A" \
  --data-binary '<?xml version="1.0" encoding="UTF-8"?>
<Document xmlns="urn:iso:std:iso:20022:tech:xsd:acmt.023.001.01">
  <AcctMgmtInqDef>
    <MsgId>
      <MsgId>ACMT023-SMOKE-'"$RUN_ID"'</MsgId>
      <CreDtTm>2026-05-14T12:00:00</CreDtTm>
    </MsgId>
    <AcctMgmtInqDtls>
      <InstrId>INSTR-'"$RUN_ID"'</InstrId>
      <EndToEndId>E2E-'"$RUN_ID"'</EndToEndId>
      <DbtrAgt><FinInstnId><Othr><Id>'"$BANK_A"'</Id></Othr></FinInstnId></DbtrAgt>
      <CdtrAgt><FinInstnId><Othr><Id>'"$BANK_B"'</Id></Othr></FinInstnId></CdtrAgt>
      <CdtrAcct><Id><Othr><Id>'"$CREDITOR"'</Id></Othr></Id></CdtrAcct>
    </AcctMgmtInqDtls>
  </AcctMgmtInqDef>
</Document>'
```

Expected:
- HTTP `200`
- Response body เป็น XML ACMT.024

---

## 12. Transfer List

### TC-110 — List all transfers

```bash
curl -s "$BASE_URL/api/transfers" \
  -H "X-API-Key: $BANK_A_KEY" | jq '{count: length}'
```

Expected: HTTP `200`

### TC-111 — List transfers (OPS view)

```bash
curl -s "$BASE_URL/api/operations/transfers?status=SUCCESS" \
  -H "X-API-Key: $OPS_KEY" | jq '{count: length, first: .[0].status}'
```

Expected: HTTP `200`

---

## 13. Metrics Smoke Test

```bash
curl -s "$BASE_URL/actuator/metrics" | jq '.names | map(select(startswith("payment.")))' 
```

Expected — ต้องมี metrics เหล่านี้:
```
payment.transfer.created
payment.transfer.failed
payment.outbox.dispatch.success
payment.outbox.dispatch.failed
payment.outbox.dispatch.duration
payment.outbox.pending.count
payment.outbox.processing.count
payment.outbox.failed.count
```

ดู metric แต่ละตัว:

```bash
curl -s "$BASE_URL/actuator/metrics/payment.outbox.pending.count" | jq .
curl -s "$BASE_URL/actuator/metrics/payment.outbox.dispatch.success" | jq .
curl -s "$BASE_URL/actuator/metrics/payment.transfer.created" | jq .
```

---

## 14. Quick Manual Run Order

วิ่งตามลำดับนี้สำหรับ full pass:

| Step | Section | หมายเหตุ |
|------|---------|----------|
| 1 | TC-001 → TC-014 | Health + Auth |
| 2 | TC-020 → TC-024 | Admin setup (ถ้า DB ว่าง) |
| 3 | TC-030 → TC-033 | Inquiry flow |
| 4 | TC-040 → TC-047 | Transfer flow + trace verification |
| 5 | TC-050 → TC-053 | Idempotency (inquiry ใหม่) |
| 6 | TC-060 → TC-064 | Force reject flow |
| 7 | TC-070 → TC-072 | Rate limiting |
| 8 | TC-080 → TC-084 | Outbox ops |
| 9 | TC-090 → TC-096 | Ops dashboard smoke |
| 10 | TC-100 → TC-103 | ISO XML smoke |
| 11 | TC-110 → TC-111 | Transfer list |
| 12 | Section 13 | Metrics |

---

## 15. สรุปผลที่ควรได้ (Summary Checklist)

```
[ ] TC-001  Actuator health = 200 UP
[ ] TC-002  Ops health no key = 401
[ ] TC-010  Missing key = 401
[ ] TC-011  BANK key on ops = 403
[ ] TC-030  Create inquiry = 200 + inquiryRef
[ ] TC-040  Create transfer = 200 + transferRef
[ ] TC-041  Transfer status after 2s = SUCCESS  ← near real-time
[ ] TC-043  Trace hasInquiry = true             ← JSON trace fix
[ ] TC-043  Timeline contains JSON_INQUIRY      ← JSON trace fix
[ ] TC-044  inquiry.inquiryApiPath = /api/inquiries/...
[ ] TC-045  Reuse inquiry = 409
[ ] TC-051  Idempotency first = 200 + transferRef
[ ] TC-052  Idempotency repeat = 200 same transferRef
[ ] TC-053  Idempotency diff payload = 409
[ ] TC-062  Force reject transfer = RECEIVED
[ ] TC-063  Force reject status after 2s = FAILED + EXT-001
[ ] TC-070  Rate limit 429 หลัง 100 POST
[ ] TC-072  GET ไม่ถูก rate limit
[ ] TC-100  Malformed XML = 400/500
[ ] TC-102  OPS key on ISO = 403
```
