#!/usr/bin/env bash
set -euo pipefail

# ──────────────────────────────────────────────────────────────────────────────
# curl_e2e_tests.sh — End-to-End test ผ่าน HTTP สำหรับ Switching API
# ──────────────────────────────────────────────────────────────────────────────
# Usage:
#   ./scripts/curl_e2e_tests.sh
#   BASE_URL=http://localhost:8080 ./scripts/curl_e2e_tests.sh
# ──────────────────────────────────────────────────────────────────────────────

BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_ID="${RUN_ID:-$(date +%s)}"
PASS=0
FAIL=0

LAST_BODY=""
LAST_STATUS=""

# ── Helpers ───────────────────────────────────────────────────────────────────

request() {
  local method="$1"
  local path="$2"
  local body="${3:-}"

  local response
  if [[ -n "$body" ]]; then
    response="$(curl -sS -X "$method" "${BASE_URL}${path}" \
      -H "Content-Type: application/json" \
      -d "$body" \
      -w $'\n%{http_code}')"
  else
    response="$(curl -sS -X "$method" "${BASE_URL}${path}" \
      -H "Content-Type: application/json" \
      -w $'\n%{http_code}')"
  fi

  LAST_STATUS="$(tail -n 1 <<< "$response")"
  LAST_BODY="$(sed '$d' <<< "$response")"
}

expect_status() {
  local expected="$1"
  local label="$2"
  if [[ "$LAST_STATUS" != "$expected" ]]; then
    echo "❌ FAIL [$label] — คาด HTTP $expected ได้ $LAST_STATUS"
    echo "   body: $LAST_BODY"
    FAIL=$((FAIL + 1))
    return 1
  fi
  return 0
}

expect_contains() {
  local expected="$1"
  local label="$2"
  if ! grep -Fq "$expected" <<< "$LAST_BODY"; then
    echo "❌ FAIL [$label] — ไม่พบ '$expected' ใน response"
    echo "   body: $LAST_BODY"
    FAIL=$((FAIL + 1))
    return 1
  fi
  return 0
}

expect_not_contains() {
  local unexpected="$1"
  local label="$2"
  if grep -Fq "$unexpected" <<< "$LAST_BODY"; then
    echo "❌ FAIL [$label] — ไม่ควรพบ '$unexpected' ใน response"
    echo "   body: $LAST_BODY"
    FAIL=$((FAIL + 1))
    return 1
  fi
  return 0
}

pass() {
  echo "✅ PASS: $1"
  PASS=$((PASS + 1))
}

extract() {
  echo "$LAST_BODY" | grep -o "\"$1\":\"[^\"]*\"" | head -1 | cut -d'"' -f4
}

# ── Setup ─────────────────────────────────────────────────────────────────────

echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Switching API — E2E Test"
echo " BASE_URL : $BASE_URL"
echo " RUN_ID   : $RUN_ID"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

BANK_A="BANK_A"
BANK_B="BANK_B"
DEBTOR="010100000001"
CREDITOR="020200000001"
AMOUNT="150000.00"
CURRENCY="LAK"

# ── Section 1: Health ─────────────────────────────────────────────────────────

echo "▶ Section 1: Health"

request GET "/actuator/health"
expect_status 200 "health" && expect_contains '"status":"UP"' "health:UP" && pass "actuator/health คืน UP"

request GET "/api/operations/health"
expect_status 200 "ops-health" && pass "operations/health คืน 200"

echo ""

# ── Section 2: Inquiry ────────────────────────────────────────────────────────

echo "▶ Section 2: Inquiry"

# 2-1 สร้าง inquiry สำเร็จ
request POST "/api/inquiries" "{
  \"sourceBank\": \"$BANK_A\",
  \"destinationBank\": \"$BANK_B\",
  \"creditorAccount\": \"$CREDITOR\",
  \"amount\": $AMOUNT,
  \"currency\": \"$CURRENCY\"
}"
expect_status 200 "inquiry:create" && expect_contains '"inquiryRef"' "inquiry:ref-present" && pass "สร้าง inquiry สำเร็จ"
INQUIRY_REF="$(extract inquiryRef)"
echo "   inquiryRef = $INQUIRY_REF"

# 2-2 ดึง inquiry ที่สร้างแล้ว
request GET "/api/inquiries/$INQUIRY_REF"
expect_status 200 "inquiry:get" && expect_contains "\"$INQUIRY_REF\"" "inquiry:match-ref" && pass "ดึง inquiry ด้วย inquiryRef สำเร็จ"

# 2-3 ดึง inquiry ที่ไม่มี → 404
request GET "/api/inquiries/INQ-NOT-FOUND-$RUN_ID"
expect_status 404 "inquiry:notfound" && pass "inquiry ที่ไม่มีคืน 404"

# 2-4 ขาด field บังคับ → 400
request POST "/api/inquiries" "{
  \"sourceBank\": \"$BANK_A\"
}"
expect_status 400 "inquiry:validation" && pass "inquiry ขาด field บังคับคืน 400"

echo ""

# ── Section 3: Transfer ───────────────────────────────────────────────────────

echo "▶ Section 3: Transfer"

# 3-1 สร้าง transfer จาก inquiry
request POST "/api/transfers" "{
  \"inquiryRef\": \"$INQUIRY_REF\",
  \"sourceBank\": \"$BANK_A\",
  \"destinationBank\": \"$BANK_B\",
  \"debtorAccount\": \"$DEBTOR\",
  \"creditorAccount\": \"$CREDITOR\",
  \"amount\": $AMOUNT,
  \"currency\": \"$CURRENCY\"
}"
expect_status 200 "transfer:create" && expect_contains '"transferRef"' "transfer:ref-present" && pass "สร้าง transfer สำเร็จ"
TRANSFER_REF="$(extract transferRef)"
echo "   transferRef = $TRANSFER_REF"

# 3-2 ดึง transfer
request GET "/api/transfers/$TRANSFER_REF"
expect_status 200 "transfer:get" && expect_contains "\"$TRANSFER_REF\"" "transfer:match-ref" && pass "ดึง transfer ด้วย transferRef สำเร็จ"

# 3-3 ใช้ inquiry เดิมซ้ำ → reject
request POST "/api/transfers" "{
  \"inquiryRef\": \"$INQUIRY_REF\",
  \"sourceBank\": \"$BANK_A\",
  \"destinationBank\": \"$BANK_B\",
  \"debtorAccount\": \"$DEBTOR\",
  \"creditorAccount\": \"$CREDITOR\",
  \"amount\": $AMOUNT,
  \"currency\": \"$CURRENCY\"
}"
if [[ "$LAST_STATUS" == "409" || "$LAST_STATUS" == "422" ]]; then
  pass "inquiry ถูกใช้ซ้ำ → reject ด้วย $LAST_STATUS"
else
  echo "❌ FAIL [transfer:inquiry-reuse] — คาด 409/422 ได้ $LAST_STATUS"
  FAIL=$((FAIL + 1))
fi

# 3-4 Idempotency — สร้าง inquiry ใหม่ แล้วส่ง transfer พร้อม idempotencyKey 2 ครั้ง
request POST "/api/inquiries" "{
  \"sourceBank\": \"$BANK_A\",
  \"destinationBank\": \"$BANK_B\",
  \"creditorAccount\": \"$CREDITOR\",
  \"amount\": $AMOUNT,
  \"currency\": \"$CURRENCY\"
}"
INQUIRY_REF_IDEM="$(extract inquiryRef)"

IDEM_KEY="IDEM-$RUN_ID"
request POST "/api/transfers" "{
  \"inquiryRef\": \"$INQUIRY_REF_IDEM\",
  \"sourceBank\": \"$BANK_A\",
  \"destinationBank\": \"$BANK_B\",
  \"debtorAccount\": \"$DEBTOR\",
  \"creditorAccount\": \"$CREDITOR\",
  \"amount\": $AMOUNT,
  \"currency\": \"$CURRENCY\",
  \"idempotencyKey\": \"$IDEM_KEY\"
}"
TRANSFER_REF_IDEM_1="$(extract transferRef)"

request POST "/api/transfers" "{
  \"inquiryRef\": \"$INQUIRY_REF_IDEM\",
  \"sourceBank\": \"$BANK_A\",
  \"destinationBank\": \"$BANK_B\",
  \"debtorAccount\": \"$DEBTOR\",
  \"creditorAccount\": \"$CREDITOR\",
  \"amount\": $AMOUNT,
  \"currency\": \"$CURRENCY\",
  \"idempotencyKey\": \"$IDEM_KEY\"
}"
TRANSFER_REF_IDEM_2="$(extract transferRef)"

if [[ "$TRANSFER_REF_IDEM_1" == "$TRANSFER_REF_IDEM_2" ]]; then
  pass "idempotency — transferRef เหมือนกันเมื่อใช้ key เดิม"
else
  echo "❌ FAIL [transfer:idempotency] — ได้ transferRef ต่างกัน: $TRANSFER_REF_IDEM_1 vs $TRANSFER_REF_IDEM_2"
  FAIL=$((FAIL + 1))
fi

# 3-5 Transfer list
request GET "/api/transfers"
expect_status 200 "transfer:list" && pass "transfer list คืน 200"

# 3-6 Transfer ที่ไม่มี → 404
request GET "/api/transfers/TRX-NOT-FOUND-$RUN_ID"
expect_status 404 "transfer:notfound" && pass "transfer ที่ไม่มีคืน 404"

echo ""

# ── Section 4: ISO Messages ───────────────────────────────────────────────────

echo "▶ Section 4: ISO Messages"

request GET "/api/iso-messages"
expect_status 200 "iso:list" && pass "iso-messages list คืน 200"

echo ""

# ── Section 5: Outbox ─────────────────────────────────────────────────────────

echo "▶ Section 5: Outbox"

request GET "/api/outbox-events"
expect_status 200 "outbox:list" && pass "outbox-events list คืน 200"

# retry event ที่ไม่มี → 404
request POST "/api/outbox-events/999999999/retry"
expect_status 404 "outbox:retry-notfound" && pass "retry outbox ที่ไม่มีคืน 404"

echo ""

# ── Section 6: Participants ───────────────────────────────────────────────────

echo "▶ Section 6: Participants"

request GET "/api/participants"
expect_status 200 "participant:list" && expect_contains '"bankCode"' "participant:has-data" && pass "participant list คืน 200"

request GET "/api/participants/$BANK_A"
expect_status 200 "participant:get" && expect_contains "\"$BANK_A\"" "participant:match" && pass "ดึง BANK_A สำเร็จ"

request GET "/api/participants/BANK-NOT-FOUND-$RUN_ID"
expect_status 404 "participant:notfound" && pass "participant ที่ไม่มีคืน 404"

echo ""

# ── Section 7: Routing Rules ──────────────────────────────────────────────────

echo "▶ Section 7: Routing Rules"

request GET "/api/routing-rules"
expect_status 200 "routing:list" && pass "routing-rules list คืน 200"

echo ""

# ── Section 8: Connector Configs ─────────────────────────────────────────────

echo "▶ Section 8: Connector Configs"

request GET "/api/connector-configs"
expect_status 200 "connector:list" && pass "connector-configs list คืน 200"

echo ""

# ── Section 9: Operations ─────────────────────────────────────────────────────

echo "▶ Section 9: Operations APIs"

request GET "/api/operations/dashboard-summary"
expect_status 200 "ops:dashboard" && pass "dashboard-summary คืน 200"

request GET "/api/operations/transactions"
expect_status 200 "ops:transactions" && pass "operations/transactions คืน 200"

request GET "/api/operations/iso-messages"
expect_status 200 "ops:iso-messages" && pass "operations/iso-messages คืน 200"

request GET "/api/operations/audit-logs"
expect_status 200 "ops:audit-logs" && pass "operations/audit-logs คืน 200"

request GET "/api/operations/outbox-failures"
expect_status 200 "ops:outbox-failures" && pass "operations/outbox-failures คืน 200"

request GET "/api/operations/outbox-stuck"
expect_status 200 "ops:outbox-stuck" && pass "operations/outbox-stuck คืน 200"

request GET "/api/operations/transfers/$TRANSFER_REF/trace"
expect_status 200 "ops:trace" && expect_contains "\"$TRANSFER_REF\"" "ops:trace-match" && pass "operations/transfer trace คืน 200"

echo ""

# ── Section 10: Transfer Trace (public) ───────────────────────────────────────

echo "▶ Section 10: Transfer Trace"

request GET "/api/transfers/$TRANSFER_REF/trace"
expect_status 200 "trace:get" && expect_contains "\"$TRANSFER_REF\"" "trace:match" && pass "transfer trace คืน 200"

echo ""

# ── Summary ───────────────────────────────────────────────────────────────────

TOTAL=$((PASS + FAIL))
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " ผล: $PASS/$TOTAL passed"
if [[ $FAIL -gt 0 ]]; then
  echo " ❌ $FAIL test failed"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  exit 1
else
  echo " ✅ ทั้งหมดผ่าน"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
fi
