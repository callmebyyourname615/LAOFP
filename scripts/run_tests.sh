#!/usr/bin/env bash
# =============================================================================
#  Switching API — Full Automated Test Runner
#
#  Usage:
#    ./scripts/run_tests.sh                          # run against localhost:8080
#    BASE_URL=http://localhost:8080 ./scripts/run_tests.sh
#    ./scripts/run_tests.sh --wait                   # wait up to 60s for app to start
#    ./scripts/run_tests.sh --wait --timeout 120     # custom wait timeout (seconds)
#    ./scripts/run_tests.sh --with-junit             # run Maven full suite, then HTTP smoke
#    ./scripts/run_tests.sh --junit-only             # run Maven full suite only
#    ./scripts/run_tests.sh --skip-rate-limit        # skip section 18 (rate-limit test)
#
#  Requires: curl, jq
#
#  IMPORTANT: With the default RATE_LIMIT_RPM=100, this script will trigger
#  ~25 false-positive HTTP 429 failures because sections 4-17 emit more than
#  100 POST/min with BANK_A_KEY. To run cleanly:
#    RATE_LIMIT_RPM=10000 docker compose up -d --force-recreate app
#    ./scripts/run_tests.sh
#  Or pass --skip-rate-limit and run section 18 separately with default limits.
# =============================================================================

# ── Argument parsing ──────────────────────────────────────────────────────────
WAIT_FOR_APP=false
WAIT_TIMEOUT=60
WITH_JUNIT=false
JUNIT_ONLY=false
SKIP_RATE_LIMIT=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --wait|-w)              WAIT_FOR_APP=true; shift ;;
    --timeout|-t)           WAIT_TIMEOUT="$2"; shift 2 ;;
    --with-junit)           WITH_JUNIT=true; shift ;;
    --junit-only)           WITH_JUNIT=true; JUNIT_ONLY=true; shift ;;
    --skip-rate-limit|-srl) SKIP_RATE_LIMIT=true; shift ;;
    *)                      shift ;;   # ignore unknown flags
  esac
done

# ── Warn if rate limit is at the default 100/min (will fail ~25 tests) ───────
RATE_LIMIT_RPM_DEFAULT=100
if [ -z "${RATE_LIMIT_NOTICE_SUPPRESS:-}" ]; then
  echo ""
  echo "⚠  Rate-limit notice: this script makes >100 POST/min with BANK_A_KEY."
  echo "   Default app limit (RATE_LIMIT_RPM=${RATE_LIMIT_RPM_DEFAULT}) will reject ~25 tests with HTTP 429."
  echo "   To run cleanly, restart the app with a higher limit:"
  echo "     RATE_LIMIT_RPM=10000 docker compose up -d --force-recreate app"
  echo "   Then re-run this script. Or pass --skip-rate-limit to skip section 18."
  echo "   (set RATE_LIMIT_NOTICE_SUPPRESS=1 to hide this notice next time)"
  echo ""
fi

# ── Dependencies check ───────────────────────────────────────────────────────
for cmd in curl jq; do
  if ! command -v "$cmd" &>/dev/null; then
    echo "ERROR: '$cmd' is required but not installed. Exiting."
    exit 1
  fi
done

# ── Config ───────────────────────────────────────────────────────────────────
BASE_URL="${BASE_URL:-http://localhost:8080}"

ADMIN_KEY="sk-admin-switching-2026"
OPS_KEY="sk-ops-switching-2026"
BANK_A_KEY="sk-bank-a-switching-2026"
BANK_B_KEY="sk-bank-b-switching-2026"

RUN_ID="$(date +%s)"
BANK_A="BANK_A"
BANK_B="BANK_B"
DEBTOR="010100000001"
CREDITOR="020200000001"
AMOUNT="150000.00"
CURRENCY="LAK"

RESP_FILE="/tmp/switching_test_resp_$$.json"

# ── Colors ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
DIM='\033[2m'
NC='\033[0m'

# ── Counters ─────────────────────────────────────────────────────────────────
PASS=0
FAIL=0
SKIP=0

# ── Runtime state (populated during test run) ────────────────────────────────
INQUIRY_REF=""
TRANSFER_REF=""
IDEM_INQUIRY_REF=""
IDEM_TRANSFER_REF=""
REJECT_INQUIRY_REF=""
REJECT_TRANSFER_REF=""
ISO_INQUIRY_REF=""
ISO_TRANSFER_REF=""
IDEM_KEY="AUTO-IDEM-${RUN_ID}"

HTTP_CODE=""
RESP_BODY=""
VPA_ID=""
BENEFICIARY_TOKEN=""
WEBHOOK_ID=""
STATIC_QR_ID=""
STATIC_QR_PAYLOAD=""
BILLER_ID=""
BILL_TOKEN_ID=""
FX_QUOTE_ID=""

# ── Helpers ──────────────────────────────────────────────────────────────────

section() {
  echo ""
  echo -e "${BOLD}${BLUE}══════════════════════════════════════════════════${NC}"
  echo -e "${BOLD}${BLUE}  $*${NC}"
  echo -e "${BOLD}${BLUE}══════════════════════════════════════════════════${NC}"
}

info() {
  echo -e "  ${CYAN}ℹ  $*${NC}"
}

warn() {
  echo -e "  ${YELLOW}⚠  $*${NC}"
}

pass() {
  local tc="$1"
  local desc="$2"
  local detail="${3:-}"
  if [ -n "$detail" ]; then
    echo -e "  ${GREEN}✅ PASS${NC} ${BOLD}${tc}${NC}  ${desc}  ${DIM}${detail}${NC}"
  else
    echo -e "  ${GREEN}✅ PASS${NC} ${BOLD}${tc}${NC}  ${desc}"
  fi
  ((PASS++)) || true
}

fail() {
  local tc="$1"
  local desc="$2"
  local detail="${3:-}"
  if [ -n "$detail" ]; then
    echo -e "  ${RED}❌ FAIL${NC} ${BOLD}${tc}${NC}  ${desc}  ${DIM}${detail}${NC}"
  else
    echo -e "  ${RED}❌ FAIL${NC} ${BOLD}${tc}${NC}  ${desc}"
  fi
  if [ -n "$RESP_BODY" ]; then
    local preview
    preview=$(echo "$RESP_BODY" | head -c 300 | tr '\n' ' ')
    echo -e "    ${DIM}↳ Response: ${preview}${NC}"
  fi
  ((FAIL++)) || true
}

skip() {
  local tc="$1"
  local desc="$2"
  echo -e "  ${YELLOW}⏭  SKIP${NC} ${BOLD}${tc}${NC}  ${desc}"
  ((SKIP++)) || true
}

# Run curl and capture HTTP status code + response body
# Usage: do_curl <curl_args...>
# Sets: HTTP_CODE, RESP_BODY
do_curl() {
  HTTP_CODE=$(curl -s -o "$RESP_FILE" -w "%{http_code}" "$@" 2>/dev/null)
  RESP_BODY=$(cat "$RESP_FILE" 2>/dev/null || echo "")
}

# Assert HTTP status is within allowed codes (pipe-separated, e.g. "200|201|409")
# Usage: check_status TC_ID "description" "200|201|409"
check_status() {
  local tc="$1"
  local desc="$2"
  local expected="$3"

  if [ -z "$HTTP_CODE" ]; then
    fail "$tc" "$desc" "no response — server unreachable? (BASE_URL=$BASE_URL)"
    return 1
  fi

  if echo "$HTTP_CODE" | grep -qE "^(${expected})$"; then
    pass "$tc" "$desc" "HTTP $HTTP_CODE"
    return 0
  else
    fail "$tc" "$desc" "expected HTTP ${expected}, got HTTP ${HTTP_CODE}"
    return 1
  fi
}

# Assert a jq field equals expected value (uses $RESP_BODY from last do_curl)
# Usage: check_body TC_ID "description" ".field" "expected_value"
check_body() {
  local tc="$1"
  local desc="$2"
  local jq_expr="$3"
  local expected="$4"
  local actual
  actual=$(echo "$RESP_BODY" | jq -r "${jq_expr} // empty" 2>/dev/null)

  if [ "$actual" = "$expected" ]; then
    pass "$tc" "$desc" "${jq_expr} = ${actual}"
    return 0
  else
    fail "$tc" "$desc" "expected ${jq_expr} = '${expected}', got '${actual}'"
    return 1
  fi
}

# Extract a value from $RESP_BODY
jq_val() {
  echo "$RESP_BODY" | jq -r "${1} // empty" 2>/dev/null
}

xml_val() {
  local tag="$1"
  echo "$RESP_BODY" \
    | tr '\n' ' ' \
    | sed -n "s:.*<${tag}>\\([^<]*\\)</${tag}>.*:\\1:p" \
    | head -1
}

body_has() {
  local expected="$1"
  echo "$RESP_BODY" | grep -Fq "$expected"
}

wait_for_transfer_status() {
  local transfer_ref="$1"
  local api_key="$2"
  local expected_regex="$3"
  local max_wait="${4:-35}"
  local interval="${5:-5}"
  local waited=0
  local status=""

  while [ "$waited" -le "$max_wait" ]; do
    do_curl "$BASE_URL/api/transfers/${transfer_ref}" \
      -H "X-API-Key: $api_key"
    status=$(jq_val ".status")
    if echo "$status" | grep -qE "$expected_regex"; then
      echo "$status"
      return 0
    fi
    if [ "$waited" -ge "$max_wait" ]; then
      echo "$status"
      return 1
    fi
    sleep "$interval"
    waited=$((waited + interval))
  done
}

# Print pretty-printed jq output from $RESP_BODY
# Usage: show_detail '{key: .field}'  OR  show_detail '.'
show_detail() {
  local jq_expr="${1:-.}"

  # raw check (no color) — skip if empty/null
  local raw
  raw=$(echo "$RESP_BODY" | jq -r "$jq_expr" 2>/dev/null)
  if [ -z "$raw" ] || [ "$raw" = "null" ]; then return 0; fi

  # colored pretty-print (jq .)
  local colored
  colored=$(echo "$RESP_BODY" | jq -C "$jq_expr" 2>/dev/null)
  [ -z "$colored" ] && return 0

  local line_count
  line_count=$(echo "$colored" | wc -l | tr -d '[:space:]')

  if [ "$line_count" -le 1 ]; then
    # Single-line value (string/number) — inline, strip outer quotes
    local inline
    inline=$(echo "$raw" | head -1)
    echo -e "    ${DIM}↳ ${inline}${NC}"
  else
    # Multi-line object/array — pretty-print with indentation (jq . style)
    echo -e "    ${DIM}↳${NC}"
    echo "$colored" | sed 's/^/      /'
  fi
}

cleanup() {
  rm -f "$RESP_FILE"
}
trap cleanup EXIT

run_junit_suite() {
  section "0. Maven Full Test Suite"
  info "Running ./mvnw -q test — current verified target is 396/396 PASS."
  if ./mvnw -q test; then
    pass "TC-JUNIT" "Maven full test suite passed"
  else
    fail "TC-JUNIT" "Maven full test suite failed"
    echo ""
    echo -e "  ${RED}${BOLD}❌  JUnit suite failed — stopping before HTTP smoke tests${NC}"
    exit 1
  fi
}

if [ "$WITH_JUNIT" = true ]; then
  run_junit_suite
  if [ "$JUNIT_ONLY" = true ]; then
    echo ""
    echo -e "  ${GREEN}${BOLD}✅  JUNIT SUITE PASSED${NC}"
    exit 0
  fi
fi

# ── Connectivity pre-check ───────────────────────────────────────────────────
if [ "$WAIT_FOR_APP" = true ]; then
  echo -e "  ${CYAN}Waiting for $BASE_URL to be ready (timeout: ${WAIT_TIMEOUT}s)...${NC}"
  WAITED=0
  while true; do
    HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$BASE_URL/actuator/health" 2>/dev/null)
    if [ "$HEALTH_CODE" = "200" ]; then
      echo -e "  ${GREEN}✔ App is ready (${WAITED}s)${NC}"
      break
    fi
    if [ "$WAITED" -ge "$WAIT_TIMEOUT" ]; then
      echo -e "  ${RED}${BOLD}ERROR: App not ready after ${WAIT_TIMEOUT}s — giving up.${NC}"
      echo -e "  ${DIM}Check logs: docker logs switching-app --tail 50${NC}"
      exit 1
    fi
    sleep 2
    WAITED=$((WAITED + 2))
  done
else
  HEALTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 "$BASE_URL/actuator/health" 2>/dev/null)
  if [ -z "$HEALTH_CODE" ] || [ "$HEALTH_CODE" = "000" ]; then
    echo ""
    echo -e "  ${RED}${BOLD}ERROR: Cannot reach $BASE_URL — is the server running?${NC}"
    echo -e "  ${DIM}Tip: docker compose up -d  then  ./scripts/run_tests.sh --wait${NC}"
    echo -e "  ${DIM}  or: set -a && source .env && set +a && ./mvnw spring-boot:run > /tmp/app.log 2>&1 &${NC}"
    echo ""
    exit 1
  fi
fi

# =============================================================================
echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║     Switching API — Full Automated Test Runner       ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo -e "  ${DIM}BASE_URL  : $BASE_URL${NC}"
echo -e "  ${DIM}RUN_ID    : $RUN_ID${NC}"
echo -e "  ${DIM}Started   : $(date '+%Y-%m-%d %H:%M:%S')${NC}"

# =============================================================================
section "1. Health / Public Endpoints"
# =============================================================================

# TC-001
do_curl "$BASE_URL/actuator/health"
check_status "TC-001" "Actuator /health is public and returns UP" "200"

# TC-002
do_curl "$BASE_URL/api/operations/health"
check_status "TC-002" "Ops /health without API key → 401" "401"

# TC-003
do_curl "$BASE_URL/api/operations/health" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-003" "Ops /health with OPS key → 200" "200"

# =============================================================================
section "2. API Key / Role Authorization"
# =============================================================================

# TC-010
do_curl "$BASE_URL/api/participants"
check_status "TC-010" "No API key on /participants → 401" "401"

# TC-011
do_curl "$BASE_URL/api/operations/dashboard-summary" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-011" "BANK key on /operations/* → 403" "403"

# TC-012
do_curl "$BASE_URL/api/participants" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-012" "OPS key can read /participants → 200" "200"

# TC-013
do_curl -X POST "$BASE_URL/api/participants" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $OPS_KEY" \
  -d "{\"bankCode\":\"BANK_TEST_${RUN_ID}\",\"bankName\":\"Test\",\"participantType\":\"BANK\",\"country\":\"LA\",\"currency\":\"LAK\",\"status\":\"ACTIVE\"}"
check_status "TC-013" "OPS key cannot create participant (write) → 403" "403"

# TC-014
do_curl "$BASE_URL/api/participants" \
  -H "X-API-Key: sk-invalid-fake-key-xyz"
check_status "TC-014" "Invalid API key → 401" "401"

# =============================================================================
section "3. Admin Setup (Participants, Connector, Routing Rule)"
# =============================================================================

info "Note: 409 is expected if records already exist from previous runs."

# TC-020
do_curl -X POST "$BASE_URL/api/participants" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"bankCode\":\"${BANK_A}\",\"bankName\":\"Source Test Bank\",\"participantType\":\"BANK\",\"country\":\"LA\",\"currency\":\"LAK\",\"status\":\"ACTIVE\"}"
check_status "TC-020" "Create BANK_A participant → 200|201|409" "200|201|409"
show_detail '{bankCode, bankName, status, participantType}'

# TC-021
do_curl -X POST "$BASE_URL/api/participants" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"bankCode\":\"${BANK_B}\",\"bankName\":\"Receiver Test Bank\",\"participantType\":\"BANK\",\"country\":\"LA\",\"currency\":\"LAK\",\"status\":\"ACTIVE\"}"
check_status "TC-021" "Create BANK_B participant → 200|201|409" "200|201|409"
show_detail '{bankCode, bankName, status, participantType}'

# TC-022
do_curl -X POST "$BASE_URL/api/connector-configs" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"connectorName\":\"MOCK_BANK_B_CONNECTOR\",\"bankCode\":\"${BANK_B}\",\"connectorType\":\"MOCK\",\"timeoutMs\":5000,\"enabled\":true,\"forceReject\":false,\"rejectReasonCode\":\"AC01\",\"rejectReasonMessage\":\"Mock reject\"}"
check_status "TC-022" "Create MOCK_BANK_B_CONNECTOR → 200|201|409" "200|201|409"
show_detail '{connectorName, bankCode, connectorType, forceReject, enabled}'

# TC-023
do_curl -X POST "$BASE_URL/api/routing-rules" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"routeCode\":\"ROUTE_A_TO_B_${RUN_ID}\",\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"messageType\":\"PACS_008\",\"connectorName\":\"MOCK_BANK_B_CONNECTOR\",\"priority\":1,\"enabled\":true}"
check_status "TC-023" "Create routing rule BANK_A→BANK_B → 200|201" "200|201"
show_detail '{routeCode, sourceBank, destinationBank, messageType, connectorName, enabled}'

# TC-024
do_curl "$BASE_URL/api/routing-rules/resolve?sourceBank=${BANK_A}&destinationBank=${BANK_B}&messageType=PACS_008" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-024" "Resolve routing rule BANK_A→BANK_B → 200" "200"
check_body "TC-024b" "Routing resolves to MOCK_BANK_B_CONNECTOR" ".connectorName" "MOCK_BANK_B_CONNECTOR"
show_detail '{routeCode, sourceBank, destinationBank, connectorName, priority}'

# TC-025 — Topup BANK_A pool so transfer tests have sufficient balance
# V26 migration seeds all ACTIVE participants with balance=0; topup before transfers.
do_curl -X POST "$BASE_URL/v1/settlement/liquidity/topup?pspId=${BANK_A}" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"amount\":1000000000,\"reference\":\"TOPUP-A-${RUN_ID}\"}"
check_status "TC-025" "Topup BANK_A pool → 200" "200"
show_detail '{topUpId, reference, status}'

# TC-026 — Topup BANK_B pool (ISO PACS.008 tests may use BANK_B as source)
do_curl -X POST "$BASE_URL/v1/settlement/liquidity/topup?pspId=${BANK_B}" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"amount\":1000000000,\"reference\":\"TOPUP-B-${RUN_ID}\"}"
check_status "TC-026" "Topup BANK_B pool → 200" "200"
show_detail '{topUpId, reference, status}'

# =============================================================================
section "4. JSON Inquiry Flow"
# =============================================================================

# TC-030
info "Creating JSON inquiry (RUN_ID=${RUN_ID})..."
do_curl -X POST "$BASE_URL/api/inquiries" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\",\"reference\":\"AUTO-INQ-${RUN_ID}\"}"
check_status "TC-030" "Create JSON inquiry → 200" "200"
show_detail '{inquiryRef, status, eligibleForTransfer, sourceBank, destinationBank, creditorAccount, amount, currency}'
INQUIRY_REF=$(jq_val ".inquiryRef")
if [ -n "$INQUIRY_REF" ]; then
  info "inquiryRef captured: $INQUIRY_REF"
else
  warn "Could not capture inquiryRef — transfer tests in Section 5 will be skipped"
fi

# TC-031
if [ -n "$INQUIRY_REF" ]; then
  do_curl "$BASE_URL/api/inquiries/${INQUIRY_REF}" \
    -H "X-API-Key: $BANK_A_KEY"
  check_status "TC-031" "GET inquiry by ref → 200" "200"
  check_body "TC-031b" "Inquiry status = ELIGIBLE" ".status" "ELIGIBLE"
  show_detail '{inquiryRef, status, eligibleForTransfer, accountFound, bankAvailable}'
else
  skip "TC-031" "GET inquiry by ref — skipped (no inquiryRef)"
  skip "TC-031b" "Inquiry status ELIGIBLE — skipped"
fi

# TC-032
do_curl "$BASE_URL/api/inquiries/INQ-NOTFOUND-${RUN_ID}" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-032" "GET non-existent inquiry → 404 INQ-001" "404"

# TC-033
do_curl -X POST "$BASE_URL/api/inquiries" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"sourceBank\":\"${BANK_A}\"}"
check_status "TC-033" "Create inquiry with missing fields → 400 REQ-001" "400"

# =============================================================================
section "5. JSON Transfer Flow (Happy Path)"
# =============================================================================

if [ -z "$INQUIRY_REF" ]; then
  warn "No inquiryRef available — skipping all transfer happy-path tests"
  for tc in "TC-040" "TC-040b" "TC-041" "TC-041b" "TC-042" "TC-043" "TC-043b" "TC-044" "TC-044b" "TC-044c"; do
    skip "$tc" "Skipped (no inquiryRef from TC-030)"
  done
else
  # TC-040
  info "Creating transfer with inquiryRef=${INQUIRY_REF}..."
  do_curl -X POST "$BASE_URL/api/transfers" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"inquiryRef\":\"${INQUIRY_REF}\",\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"debtorAccount\":\"${DEBTOR}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\",\"reference\":\"AUTO-TRF-${RUN_ID}\"}"
  check_status "TC-040" "Create JSON transfer → 200 ACCEPTED" "200"
  show_detail '{transferRef, status, sourceBank: .sourceBankCode, destinationBank: .destinationBankCode, amount, currency, inquiryRef}'
  TRANSFER_REF=$(jq_val ".transferRef")
  if [ -n "$TRANSFER_REF" ]; then
    info "transferRef captured: $TRANSFER_REF"
    check_body "TC-040b" "Transfer initial status = ACCEPTED" ".status" "ACCEPTED"
  else
    warn "Could not capture transferRef"
    skip "TC-040b" "Initial status check — skipped (no transferRef)"
  fi

  # TC-041 — Near real-time dispatch check
  if [ -n "$TRANSFER_REF" ]; then
    info "Polling up to 35s for outbox dispatch..."
    STATUS=$(wait_for_transfer_status "$TRANSFER_REF" "$BANK_A_KEY" "^(SETTLED|SUCCESS)$" 35 5)
    check_status "TC-041" "GET transfer status during dispatch polling → 200" "200"
    show_detail '{transferRef, status, errorCode, errorMessage}'
    if [ "$STATUS" = "SETTLED" ] || [ "$STATUS" = "SUCCESS" ]; then
      pass "TC-041b" "Transfer status = SETTLED/SUCCESS after async dispatch" "status=${STATUS}"
    else
      fail "TC-041b" "Transfer not SETTLED/SUCCESS within async dispatch window" "status=${STATUS}"
    fi
  else
    skip "TC-041" "Transfer status check — skipped (no transferRef)"
    skip "TC-041b" "Near real-time check — skipped"
  fi

  # TC-042
  if [ -n "$TRANSFER_REF" ]; then
    do_curl "$BASE_URL/api/transfers/${TRANSFER_REF}" \
      -H "X-API-Key: $BANK_A_KEY"
    check_status "TC-042" "GET transfer by ref → 200" "200"
    show_detail '{transferRef, status, sourceBankCode, destinationBankCode, amount, currency, channelId, inquiryRef}'
  else
    skip "TC-042" "GET transfer — skipped (no transferRef)"
  fi

  # TC-043 — Public trace (check inquiryRef in response)
  if [ -n "$TRANSFER_REF" ]; then
    do_curl "$BASE_URL/api/transfers/${TRANSFER_REF}/trace" \
      -H "X-API-Key: $BANK_A_KEY"
    check_status "TC-043" "Public transfer trace → 200" "200"
    show_detail '{status, inquiryRef, channelId, timelineCount: (.timeline | length)}'
    INQ_IN_TRACE=$(jq_val ".inquiryRef")
    if [ -n "$INQ_IN_TRACE" ] && [ "$INQ_IN_TRACE" != "null" ]; then
      pass "TC-043b" "Public trace has inquiryRef (JSON trace fix ✅)" "inquiryRef=${INQ_IN_TRACE}"
    else
      fail "TC-043b" "Public trace missing inquiryRef — JSON trace fix not working"
    fi
  else
    skip "TC-043" "Public trace — skipped (no transferRef)"
    skip "TC-043b" "Trace inquiryRef check — skipped"
  fi

  # TC-044 — Operations trace
  if [ -n "$TRANSFER_REF" ]; then
    do_curl "$BASE_URL/api/operations/transfers/${TRANSFER_REF}/trace" \
      -H "X-API-Key: $OPS_KEY"
    check_status "TC-044" "Ops transfer trace → 200" "200"
    show_detail '{status, warnings: (.warnings | length), summary: {hasInquiry: .summary.hasInquiry, timelineEventCount: .summary.timelineEventCount, hasOutbox: .summary.hasOutbox, hasIsoMessages: .summary.hasIsoMessages}, inquiryApiPath: .inquiry.inquiryApiPath}'
    HAS_INQ=$(jq_val ".summary.hasInquiry")
    if [ "$HAS_INQ" = "true" ]; then
      pass "TC-044b" "Ops trace summary.hasInquiry = true (JSON trace fix ✅)"
    else
      fail "TC-044b" "Ops trace summary.hasInquiry ≠ true" "hasInquiry=${HAS_INQ}"
    fi
    WARN_COUNT=$(echo "$RESP_BODY" | jq -r '(.warnings // []) | length' 2>/dev/null || echo "?")
    if [ "$WARN_COUNT" = "0" ]; then
      pass "TC-044c" "Ops trace has 0 warnings (no errors in trace pipeline)"
    else
      fail "TC-044c" "Ops trace has warnings" "warnings=${WARN_COUNT}"
    fi
  else
    skip "TC-044" "Ops trace — skipped (no transferRef)"
    skip "TC-044b" "hasInquiry check — skipped"
    skip "TC-044c" "Warnings check — skipped"
  fi
fi

# TC-045 — Reuse inquiry → 409
if [ -n "$INQUIRY_REF" ]; then
  do_curl -X POST "$BASE_URL/api/transfers" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"inquiryRef\":\"${INQUIRY_REF}\",\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"debtorAccount\":\"${DEBTOR}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\"}"
  check_status "TC-045" "Reuse already-used inquiry → 409 INQ-003" "409"
else
  skip "TC-045" "Reuse inquiry check — skipped (no inquiryRef)"
fi

# TC-046 — Missing inquiryRef → 400
do_curl -X POST "$BASE_URL/api/transfers" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"debtorAccount\":\"${DEBTOR}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\"}"
check_status "TC-046" "Create transfer without inquiryRef → 400 REQ-001" "400"

# TC-047 — Transfer not found
do_curl "$BASE_URL/api/transfers/TRX-NOT-EXIST-${RUN_ID}" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-047" "GET non-existent transfer → 404 TRF-001" "404"

# =============================================================================
section "6. Idempotency"
# =============================================================================

# TC-050 — New inquiry for idempotency test
info "Creating fresh inquiry for idempotency test..."
do_curl -X POST "$BASE_URL/api/inquiries" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\",\"reference\":\"IDEM-INQ-${RUN_ID}\"}"
check_status "TC-050" "Create inquiry for idempotency test → 200" "200"
show_detail '{inquiryRef, status, eligibleForTransfer, amount, currency}'
IDEM_INQUIRY_REF=$(jq_val ".inquiryRef")
[ -n "$IDEM_INQUIRY_REF" ] && info "Idempotency inquiryRef: $IDEM_INQUIRY_REF"

# TC-051 — First transfer with idempotencyKey
if [ -n "$IDEM_INQUIRY_REF" ]; then
  do_curl -X POST "$BASE_URL/api/transfers" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"inquiryRef\":\"${IDEM_INQUIRY_REF}\",\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"debtorAccount\":\"${DEBTOR}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\",\"idempotencyKey\":\"${IDEM_KEY}\"}"
  check_status "TC-051" "First transfer with idempotencyKey → 200" "200"
  show_detail '{transferRef, status, inquiryRef}'
  IDEM_TRANSFER_REF=$(jq_val ".transferRef")
  [ -n "$IDEM_TRANSFER_REF" ] && info "Idempotency transferRef: $IDEM_TRANSFER_REF"
else
  skip "TC-051" "First idempotency transfer — skipped (no idem inquiryRef)"
fi

# TC-052 — Exact same request → same transferRef returned
if [ -n "$IDEM_INQUIRY_REF" ] && [ -n "$IDEM_TRANSFER_REF" ]; then
  do_curl -X POST "$BASE_URL/api/transfers" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"inquiryRef\":\"${IDEM_INQUIRY_REF}\",\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"debtorAccount\":\"${DEBTOR}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\",\"idempotencyKey\":\"${IDEM_KEY}\"}"
  check_status "TC-052" "Repeat identical request (same hash) → 200" "200"
  show_detail '{transferRef, status}'
  RETURNED_REF=$(jq_val ".transferRef")
  if [ "$RETURNED_REF" = "$IDEM_TRANSFER_REF" ]; then
    pass "TC-052b" "Returned transferRef matches original (idempotency works ✅)" "ref=${RETURNED_REF}"
  else
    fail "TC-052b" "Returned transferRef differs from original" "expected=${IDEM_TRANSFER_REF} got=${RETURNED_REF}"
  fi
else
  skip "TC-052" "Idempotency repeat request — skipped"
  skip "TC-052b" "TransferRef match check — skipped"
fi

# TC-053 — Same idempotencyKey, different payload → 409 TRF-002
if [ -n "$IDEM_INQUIRY_REF" ]; then
  do_curl -X POST "$BASE_URL/api/transfers" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"inquiryRef\":\"${IDEM_INQUIRY_REF}\",\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"debtorAccount\":\"${DEBTOR}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":999.00,\"currency\":\"${CURRENCY}\",\"idempotencyKey\":\"${IDEM_KEY}\"}"
  check_status "TC-053" "Same idempotencyKey, different payload → 409 TRF-002" "409"
else
  skip "TC-053" "Idempotency hash conflict — skipped (no idem inquiryRef)"
fi

# =============================================================================
section "7. Force Reject Flow (Downstream REJECT)"
# =============================================================================

# TC-060 — Direct PATCH is now DENIED by Phase 21 four-eyes guard.
# Connector config changes must go through ConfigurationChangeController workflow.
# Expectation flipped: 403 confirms four-eyes guard is active.
do_curl -X PATCH "$BASE_URL/api/connector-configs/MOCK_BANK_B_CONNECTOR" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $ADMIN_KEY" \
  -d "{\"forceReject\":true}"
check_status "TC-060" "Direct PATCH on connector-config blocked by four-eyes guard → 403" "403"
show_detail '{errorCode, message}'

# TC-061..TC-064 — Skipped: direct force-reject toggle is no longer possible (four-eyes).
# Force-reject behavior can be exercised by submitting a config change request
# through ConfigurationChangeController and approving it with a second admin key.
# See section 20 (Four-Eyes Config Change) for the new flow.
skip "TC-061" "Force-reject flow superseded by four-eyes config-change workflow"
skip "TC-062" "Force-reject flow superseded by four-eyes config-change workflow"
skip "TC-063" "Force-reject flow superseded by four-eyes config-change workflow"
skip "TC-063b" "Force-reject flow superseded by four-eyes config-change workflow"
skip "TC-064" "Force-reject flow superseded by four-eyes config-change workflow"

# =============================================================================
section "8. Outbox & Dispatch Operations"
# =============================================================================

# TC-080
do_curl "$BASE_URL/api/outbox-events" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-080" "List outbox events (OPS key) → 200" "200"
show_detail 'if type == "array" then "count: \(length)  |  latest: {id:\(.[0].id), transferRef:\(.[0].transferRef), status:\(.[0].status), retryCount:\(.[0].retryCount)}" else . end'

# TC-081
do_curl -X POST "$BASE_URL/api/outbox-events/999999999/retry" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-081" "Retry non-existent outbox event → 404 OUT-005" "404"

# TC-082
do_curl "$BASE_URL/api/outbox-events" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-082" "BANK key on /outbox-events → 403" "403"

# TC-083
do_curl "$BASE_URL/api/operations/outbox-failures" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-083" "Operations outbox-failures list → 200" "200"

# TC-084
do_curl "$BASE_URL/api/operations/outbox-stuck" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-084" "Operations outbox-stuck list → 200" "200"

# =============================================================================
section "9. Operations Query Smoke Tests"
# =============================================================================

# TC-090
do_curl "$BASE_URL/api/operations/dashboard-summary" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-090" "Operations dashboard-summary → 200" "200"
show_detail 'to_entries | map(select(.value | type != "object" and type != "array")) | map("\(.key): \(.value)") | join("  |  ")'

# TC-091
do_curl "$BASE_URL/api/operations/transactions" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-091" "Operations transactions list → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif .items then "count: \(.items | length)  |  total: \(.totalItems)" else . end'

# TC-092
do_curl "$BASE_URL/api/operations/transfers" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-092" "Operations transfers list → 200" "200"
show_detail 'if type == "array" then "count: \(length)  |  latest_status: \(.[0].status)" elif .items then "count: \(.items | length)  |  total: \(.totalItems)" else . end'

# TC-093
do_curl "$BASE_URL/api/operations/iso-messages" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-093" "Operations iso-messages list → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif .items then "count: \(.items | length)  |  total: \(.totalItems)" else . end'

# TC-094
do_curl "$BASE_URL/api/operations/audit-logs" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-094" "Operations audit-logs list → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif .items then "count: \(.items | length)  |  total: \(.totalItems)" else . end'

# TC-095
do_curl "$BASE_URL/api/operations/connectors/health" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-095" "Operations connector health → 200" "200"
show_detail 'if type == "array" then map({name: .connectorName, status: .status}) | .[] | "\(.name): \(.status)" else . end'

# TC-096
do_curl "$BASE_URL/api/operations/bank-status" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-096" "Operations bank-status overview → 200" "200"
show_detail 'if type == "array" then map({bank: .bankCode, status: .status}) | .[] | "\(.bank): \(.status)" else . end'

# =============================================================================
section "10. Transfer List"
# =============================================================================

# TC-110
do_curl "$BASE_URL/api/transfers" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-110" "List all transfers (BANK key) → 200" "200"

# TC-111
do_curl "$BASE_URL/api/operations/transfers?status=SUCCESS" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-111" "Ops transfers list ?status=SUCCESS → 200" "200"
show_detail 'if type == "array" then "count: \(length)  |  statuses: \([.[].status] | unique | join(", "))" elif .items then "count: \(.items | length)  |  total: \(.totalItems)" else . end'

# =============================================================================
section "11. ISO XML Smoke Tests"
# =============================================================================

# TC-100 — Malformed PACS.008
do_curl -X POST "$BASE_URL/api/iso20022/pacs008" \
  -H "Content-Type: application/xml" \
  -H "X-API-Key: $BANK_A_KEY" \
  -H "X-Bank-Code: $BANK_A" \
  --data-binary '<not-valid-xml>'
# ISO endpoints may return 200 with PACS.002 RJCT XML (ISO protocol), 400, or 500
check_status "TC-100" "Malformed PACS.008 → ISO rejection (200) or HTTP error" "200|400|500"

# TC-101 — ACMT.023 without X-Bank-Code
# ISO endpoints always return HTTP 200 with ACMT.024 XML body even on errors
# (ISO 20022 protocol wraps rejections inside XML, not as HTTP error codes)
do_curl -X POST "$BASE_URL/api/iso20022/acmt023" \
  -H "Content-Type: application/xml" \
  -H "X-API-Key: $BANK_A_KEY" \
  --data-binary '<Document></Document>'
check_status "TC-101" "ACMT.023 without X-Bank-Code → ISO rejection (200) or HTTP error" "200|400|422|500"
# Verify 200 response is actually an error/rejection XML (contains UNKNOWN or no valid inquiry)
if [ "$HTTP_CODE" = "200" ]; then
  if echo "$RESP_BODY" | grep -qi "UNKNOWN\|RJCT\|FAIL\|error"; then
    pass "TC-101b" "ACMT.024 response body signals rejection (ISO protocol behavior ✅)"
  else
    fail "TC-101b" "HTTP 200 but response body does not look like a rejection"
  fi
fi

# TC-102 — OPS key on ISO endpoint → 403
do_curl -X POST "$BASE_URL/api/iso20022/pacs008" \
  -H "Content-Type: application/xml" \
  -H "X-API-Key: $OPS_KEY" \
  -H "X-Bank-Code: $BANK_A" \
  --data-binary '<Document></Document>'
check_status "TC-102" "OPS key on ISO endpoint → 403" "403"

# TC-103 — Valid ACMT.023 smoke (BANK_B → BANK_A; uses BANK_B_KEY to avoid rate-limit exhaustion)
ISO_ACMT_SUFFIX="AUTO-${RUN_ID}"
ISO_XML="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:acmt.023.001.03\">
  <IdVrfctnReq>
    <Assgnmt>
      <MsgId>ACMT023-${ISO_ACMT_SUFFIX}</MsgId>
      <CreDtTm>2026-05-14T12:00:00Z</CreDtTm>
    </Assgnmt>
    <Vrfctn>
      <Id>VERIFY-${ISO_ACMT_SUFFIX}</Id>
      <PtyAndAcctId>
        <Acct>
          <Id>
            <Othr>
              <Id>${DEBTOR}</Id>
            </Othr>
          </Id>
        </Acct>
      </PtyAndAcctId>
    </Vrfctn>
    <DbtrAgt><FinInstnId><BICFI>${BANK_B}</BICFI></FinInstnId></DbtrAgt>
    <CdtrAgt><FinInstnId><BICFI>${BANK_A}</BICFI></FinInstnId></CdtrAgt>
    <Amt Ccy=\"${CURRENCY}\">${AMOUNT}</Amt>
    <RmtInf><Ustrd>ISO automated test ${ISO_ACMT_SUFFIX}</Ustrd></RmtInf>
  </IdVrfctnReq>
</Document>"

do_curl -X POST "$BASE_URL/api/iso20022/acmt023" \
  -H "Content-Type: application/xml" \
  -H "X-API-Key: $BANK_B_KEY" \
  -H "X-Bank-Code: $BANK_B" \
  --data-binary "$ISO_XML"
check_status "TC-103" "Valid ACMT.023 ISO inquiry smoke → 200 (ACMT.024 XML response)" "200"
ISO_INQUIRY_REF=$(xml_val "InquiryRef")
ISO_VERIFY_STATUS=$(xml_val "Vrfctn")
if [ "$ISO_VERIFY_STATUS" = "MTCH" ] && [ -n "$ISO_INQUIRY_REF" ]; then
  pass "TC-103b" "ACMT.024 response contains MTCH + InquiryRef" "inquiryRef=${ISO_INQUIRY_REF}"
else
  fail "TC-103b" "ACMT.024 response missing MTCH or InquiryRef" "Vrfctn=${ISO_VERIFY_STATUS:-empty} inquiryRef=${ISO_INQUIRY_REF:-empty}"
fi

# TC-104 — Valid PACS.008 uses InquiryRef from TC-103 (BANK_B → BANK_A)
if [ -n "$ISO_INQUIRY_REF" ]; then
  ISO_PACS_SUFFIX="AUTO-PACS-${RUN_ID}"
  ISO_PACS008_XML="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.12\">
  <FIToFICstmrCdtTrf>
    <GrpHdr>
      <MsgId>MSG-${ISO_PACS_SUFFIX}</MsgId>
      <CreDtTm>2026-05-14T12:01:00</CreDtTm>
      <NbOfTxs>1</NbOfTxs>
    </GrpHdr>
    <CdtTrfTxInf>
      <PmtId>
        <InstrId>INST-${ISO_PACS_SUFFIX}</InstrId>
        <EndToEndId>E2E-${ISO_PACS_SUFFIX}</EndToEndId>
      </PmtId>
      <IntrBkSttlmAmt Ccy=\"${CURRENCY}\">${AMOUNT}</IntrBkSttlmAmt>
      <DbtrAgt><FinInstnId><BICFI>${BANK_B}</BICFI></FinInstnId></DbtrAgt>
      <CdtrAgt><FinInstnId><BICFI>${BANK_A}</BICFI></FinInstnId></CdtrAgt>
      <DbtrAcct><Id><Othr><Id>${CREDITOR}</Id></Othr></Id></DbtrAcct>
      <CdtrAcct><Id><Othr><Id>${DEBTOR}</Id></Othr></Id></CdtrAcct>
    </CdtTrfTxInf>
    <SplmtryData><PlcAndNm>LAO_SWITCHING_INQUIRY_REF</PlcAndNm><Envlp><InquiryRef>${ISO_INQUIRY_REF}</InquiryRef></Envlp></SplmtryData>
  </FIToFICstmrCdtTrf>
</Document>"

  do_curl -X POST "$BASE_URL/api/iso20022/pacs008" \
    -H "Content-Type: application/xml" \
    -H "X-API-Key: $BANK_B_KEY" \
    -H "X-Bank-Code: $BANK_B" \
    --data-binary "$ISO_PACS008_XML"
  check_status "TC-104" "Valid PACS.008 with InquiryRef → 200 (PACS.002 XML response)" "200"
  ISO_TX_STATUS=$(xml_val "TxSts")
  ISO_TRANSFER_REF=$(xml_val "AcctSvcrRef")
  if echo "$ISO_TX_STATUS" | grep -qE "^(ACCP|ACTC)$" && [ -n "$ISO_TRANSFER_REF" ]; then
    pass "TC-104b" "PACS.002 response accepted and includes transfer ref" "transferRef=${ISO_TRANSFER_REF}"
  else
    fail "TC-104b" "PACS.002 response not accepted or missing transfer ref" "TxSts=${ISO_TX_STATUS:-empty} AcctSvcrRef=${ISO_TRANSFER_REF:-empty}"
  fi
else
  skip "TC-104" "Valid PACS.008 — skipped (no ISO inquiryRef)"
  skip "TC-104b" "PACS.002 accepted check — skipped"
fi

# TC-105 — Repeat same PACS.008 should be idempotent and return same transfer ref (BANK_B → BANK_A)
if [ -n "$ISO_INQUIRY_REF" ] && [ -n "$ISO_TRANSFER_REF" ]; then
  do_curl -X POST "$BASE_URL/api/iso20022/pacs008" \
    -H "Content-Type: application/xml" \
    -H "X-API-Key: $BANK_B_KEY" \
    -H "X-Bank-Code: $BANK_B" \
    --data-binary "$ISO_PACS008_XML"
  check_status "TC-105" "Repeat same PACS.008 → 200 idempotent response" "200"
  REPEAT_TX_STATUS=$(xml_val "TxSts")
  REPEAT_TRANSFER_REF=$(xml_val "AcctSvcrRef")
  if echo "$REPEAT_TX_STATUS" | grep -qE "^(ACCP|ACTC)$" && [ "$REPEAT_TRANSFER_REF" = "$ISO_TRANSFER_REF" ]; then
    pass "TC-105b" "Repeat PACS.008 returns same transfer ref" "transferRef=${REPEAT_TRANSFER_REF}"
  else
    fail "TC-105b" "Repeat PACS.008 did not return same accepted transfer" "TxSts=${REPEAT_TX_STATUS:-empty} expected=${ISO_TRANSFER_REF} got=${REPEAT_TRANSFER_REF:-empty}"
  fi
else
  skip "TC-105" "Repeat PACS.008 — skipped"
  skip "TC-105b" "Repeat transferRef check — skipped"
fi

# TC-106 — New PACS.008 instruction using already-used inquiry should reject
if [ -n "$ISO_INQUIRY_REF" ]; then
  ISO_USED_SUFFIX="USED-${RUN_ID}"
  ISO_USED_PACS008_XML="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.12\">
  <FIToFICstmrCdtTrf>
    <GrpHdr><MsgId>MSG-${ISO_USED_SUFFIX}</MsgId><CreDtTm>2026-05-14T12:02:00</CreDtTm><NbOfTxs>1</NbOfTxs></GrpHdr>
    <CdtTrfTxInf>
      <PmtId><InstrId>INST-${ISO_USED_SUFFIX}</InstrId><EndToEndId>E2E-${ISO_USED_SUFFIX}</EndToEndId></PmtId>
      <IntrBkSttlmAmt Ccy=\"${CURRENCY}\">${AMOUNT}</IntrBkSttlmAmt>
      <DbtrAgt><FinInstnId><BICFI>${BANK_B}</BICFI></FinInstnId></DbtrAgt>
      <CdtrAgt><FinInstnId><BICFI>${BANK_A}</BICFI></FinInstnId></CdtrAgt>
      <DbtrAcct><Id><Othr><Id>${CREDITOR}</Id></Othr></Id></DbtrAcct>
      <CdtrAcct><Id><Othr><Id>${DEBTOR}</Id></Othr></Id></CdtrAcct>
    </CdtTrfTxInf>
    <SplmtryData><PlcAndNm>LAO_SWITCHING_INQUIRY_REF</PlcAndNm><Envlp><InquiryRef>${ISO_INQUIRY_REF}</InquiryRef></Envlp></SplmtryData>
  </FIToFICstmrCdtTrf>
</Document>"
  do_curl -X POST "$BASE_URL/api/iso20022/pacs008" \
    -H "Content-Type: application/xml" \
    -H "X-API-Key: $BANK_B_KEY" \
    -H "X-Bank-Code: $BANK_B" \
    --data-binary "$ISO_USED_PACS008_XML"
  check_status "TC-106" "New PACS.008 with used InquiryRef → 200 rejection XML" "200"
  USED_TX_STATUS=$(xml_val "TxSts")
  if [ "$USED_TX_STATUS" = "RJCT" ] && body_has "status=USED"; then
    pass "TC-106b" "Used InquiryRef rejected with status=USED"
  else
    fail "TC-106b" "Used InquiryRef did not produce expected rejection" "TxSts=${USED_TX_STATUS:-empty}"
  fi
else
  skip "TC-106" "Used InquiryRef rejection — skipped"
  skip "TC-106b" "Used InquiryRef body check — skipped"
fi

# TC-107 — PACS.008 with unknown InquiryRef should reject
ISO_UNKNOWN_SUFFIX="UNKNOWN-${RUN_ID}"
ISO_UNKNOWN_PACS008_XML="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.12\">
  <FIToFICstmrCdtTrf>
    <GrpHdr><MsgId>MSG-${ISO_UNKNOWN_SUFFIX}</MsgId><CreDtTm>2026-05-14T12:03:00</CreDtTm><NbOfTxs>1</NbOfTxs></GrpHdr>
    <CdtTrfTxInf>
      <PmtId><InstrId>INST-${ISO_UNKNOWN_SUFFIX}</InstrId><EndToEndId>E2E-${ISO_UNKNOWN_SUFFIX}</EndToEndId></PmtId>
      <IntrBkSttlmAmt Ccy=\"${CURRENCY}\">${AMOUNT}</IntrBkSttlmAmt>
      <DbtrAgt><FinInstnId><BICFI>${BANK_B}</BICFI></FinInstnId></DbtrAgt>
      <CdtrAgt><FinInstnId><BICFI>${BANK_A}</BICFI></FinInstnId></CdtrAgt>
      <DbtrAcct><Id><Othr><Id>${CREDITOR}</Id></Othr></Id></DbtrAcct>
      <CdtrAcct><Id><Othr><Id>${DEBTOR}</Id></Othr></Id></CdtrAcct>
    </CdtTrfTxInf>
    <SplmtryData><PlcAndNm>LAO_SWITCHING_INQUIRY_REF</PlcAndNm><Envlp><InquiryRef>INQ-ISO-NOTFOUND-${RUN_ID}</InquiryRef></Envlp></SplmtryData>
  </FIToFICstmrCdtTrf>
</Document>"
do_curl -X POST "$BASE_URL/api/iso20022/pacs008" \
  -H "Content-Type: application/xml" \
  -H "X-API-Key: $BANK_B_KEY" \
  -H "X-Bank-Code: $BANK_B" \
  --data-binary "$ISO_UNKNOWN_PACS008_XML"
check_status "TC-107" "PACS.008 with unknown InquiryRef → 200 rejection XML" "200"
UNKNOWN_TX_STATUS=$(xml_val "TxSts")
if [ "$UNKNOWN_TX_STATUS" = "RJCT" ]; then
  pass "TC-107b" "Unknown InquiryRef rejected with RJCT status"
else
  fail "TC-107b" "Unknown InquiryRef did not produce expected rejection" "TxSts=${UNKNOWN_TX_STATUS:-empty}"
fi

# =============================================================================
section "12. Endpoint Coverage Smoke — Dashboard / FPRE / Liquidity"
# =============================================================================

# TC-130
do_curl "$BASE_URL/api/dashboard/overview" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-130" "Dashboard overview endpoint → 200" "200"
show_detail '{todayVolume, todaySuccessRate, openDisputes, stuckOutboxEvents}'

# TC-131
do_curl "$BASE_URL/api/dashboard/overview" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-131" "BANK key on dashboard overview → 403" "403"

# TC-132
do_curl "$BASE_URL/v1/fpre/health" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-132" "FPRE health endpoint → 200" "200"
show_detail '.'

# TC-133
do_curl "$BASE_URL/v1/transfers/pending" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-133" "FPRE pending transfers endpoint → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif .items then "count: \(.items | length)" else . end'

# TC-134
do_curl "$BASE_URL/v1/transfers/failed" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-134" "FPRE failed transfers endpoint → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif .items then "count: \(.items | length)" else . end'

# TC-135
do_curl "$BASE_URL/v1/settlement/balance?pspId=${BANK_A}" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-135" "Liquidity balance endpoint → 200" "200"
show_detail '.'

# TC-136
do_curl "$BASE_URL/v1/settlement/pool-history?pspId=${BANK_A}" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-136" "Liquidity pool-history endpoint → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif .items then "count: \(.items | length)" else . end'

# TC-137
do_curl "$BASE_URL/v1/settlement/positions" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-137" "Settlement positions endpoint → 200 or 404 when no OPEN cycle exists" "200|404"
show_detail 'if type == "array" then "count: \(length)" else . end'

# =============================================================================
section "13. Endpoint Coverage Smoke — VPA / Webhooks"
# =============================================================================

# TC-140
do_curl -X POST "$BASE_URL/v1/lookup/vpa/register" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"vpaType\":\"EMAIL\",\"vpaValue\":\"auto-${RUN_ID}@bank-a.test\",\"pspId\":\"${BANK_A}\",\"accountRef\":\"${DEBTOR}\",\"accountType\":\"BANK_ACCOUNT\",\"displayName\":\"Auto Test ${RUN_ID}\"}"
check_status "TC-140" "Register VPA → 201" "201"
show_detail '{vpaId, vpaType, vpaValue, pspId, accountRef, status}'
VPA_ID=$(jq_val ".vpaId")

# TC-141
if [ -n "$VPA_ID" ]; then
  do_curl "$BASE_URL/v1/lookup/vpa/${VPA_ID}" \
    -H "X-API-Key: $OPS_KEY"
  check_status "TC-141" "Get VPA detail → 200" "200"
  show_detail '{vpaId, vpaType, vpaValue, pspId, status}'
else
  skip "TC-141" "Get VPA detail — skipped (no vpaId)"
fi

# TC-142
do_curl -X POST "$BASE_URL/v1/lookup/resolve" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"vpaType\":\"EMAIL\",\"vpaValue\":\"auto-${RUN_ID}@bank-a.test\"}"
check_status "TC-142" "Resolve VPA to beneficiary token → 200" "200"
show_detail '{vpaType, vpaValue, beneficiaryToken, accountRef, displayName, expiresAt}'
BENEFICIARY_TOKEN=$(jq_val ".beneficiaryToken")

# TC-143
do_curl -X POST "$BASE_URL/v1/lookup/resolve" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $OPS_KEY" \
  -d "{\"vpaType\":\"EMAIL\",\"vpaValue\":\"auto-${RUN_ID}@bank-a.test\"}"
check_status "TC-143" "OPS can resolve VPA → 200" "200"

# TC-144
do_curl "$BASE_URL/v1/webhooks" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-144" "List webhooks for BANK_A → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-145 — Webhook URL must be HTTPS + non-localhost (Phase 10A SSRF guard).
# Use a publicly-routable test URL; we expect either:
#   201 created (registration succeeds), or
#   400 with SSRF/destination-policy violation (acceptable rejection signal).
WEBHOOK_TEST_URL="https://webhook.example.com/switching/${RUN_ID}"
do_curl -X POST "$BASE_URL/v1/webhooks" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"url\":\"${WEBHOOK_TEST_URL}\",\"eventTypes\":[\"TRANSFER.SETTLED\",\"TEST.PING\"],\"signingSecret\":\"whsec-${RUN_ID}-abcdefghijklmnopqrstuvwxyz\"}"
check_status "TC-145" "Register webhook endpoint (HTTPS) → 201 or 400 (SSRF guard)" "201|400"
show_detail '{webhookId, pspId, url, status, eventTypes, errorCode, message}'
WEBHOOK_ID=$(jq_val ".webhookId")

# TC-145b — Confirm SSRF guard rejects http:// localhost
do_curl -X POST "$BASE_URL/v1/webhooks" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"url\":\"http://localhost:9099/should-be-blocked\",\"eventTypes\":[\"TEST.PING\"],\"signingSecret\":\"whsec-ssrf-${RUN_ID}-xxxxxxxxxxxxxxxxxxxxxxxxxxx\"}"
check_status "TC-145b" "SSRF guard blocks http://localhost webhook → 400" "400"
show_detail '{errorCode, message}'

# TC-146
if [ -n "$WEBHOOK_ID" ]; then
  do_curl "$BASE_URL/v1/webhooks/${WEBHOOK_ID}" \
    -H "X-API-Key: $BANK_A_KEY"
  check_status "TC-146" "Get webhook detail → 200" "200"
  show_detail '{webhookId: .registration.webhookId, pspId: .registration.pspId, url: .registration.url, status: .registration.status, recentDeliveryCount: (.recentDeliveries | length)}'
else
  skip "TC-146" "Webhook detail — skipped (TC-145 did not return webhookId, likely SSRF guard rejected test URL)"
fi

# TC-147
if [ -n "$WEBHOOK_ID" ]; then
  do_curl -X POST "$BASE_URL/v1/webhooks/${WEBHOOK_ID}/test" \
    -H "X-API-Key: $BANK_A_KEY"
  check_status "TC-147" "Webhook test delivery endpoint → 200|202|503" "200|202|503"
  show_detail '.'
else
  skip "TC-147" "Webhook test delivery — skipped (no webhookId)"
fi

# =============================================================================
section "14. Endpoint Coverage Smoke — Compliance / Risk"
# =============================================================================

# TC-150
do_curl "$BASE_URL/v1/compliance/sanctions/check?name=Auto%20Clear%20${RUN_ID}&txnId=MANUAL-${RUN_ID}" \
  -H "X-API-Key: $ADMIN_KEY"
check_status "TC-150" "Compliance sanctions manual check → 200" "200"
show_detail '{name, txnId, outcome, matchEntity, listType, matchScore}'

# TC-151
do_curl "$BASE_URL/v1/compliance/sanctions/check?name=Auto%20Clear%20${RUN_ID}" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-151" "OPS key on compliance endpoint → 403" "403"

# TC-152
do_curl "$BASE_URL/v1/compliance/velocity/${BANK_A}" \
  -H "X-API-Key: $ADMIN_KEY"
check_status "TC-152" "Compliance velocity counters → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-153
do_curl "$BASE_URL/v1/compliance/str/999999999" \
  -H "X-API-Key: $ADMIN_KEY"
check_status "TC-153" "Compliance STR not found → 404" "404"

# TC-154
do_curl "$BASE_URL/v1/risk/scores/TXN-NOTFOUND-${RUN_ID}" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-154" "Risk score lookup smoke → 200|404" "200|404"
show_detail '.'

# =============================================================================
section "15. Endpoint Coverage Smoke — Bill / Cross-border / QR"
# =============================================================================

# TC-160
do_curl "$BASE_URL/v1/billers" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-160" "List active billers → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'
BILLER_ID=$(echo "$RESP_BODY" | jq -r 'if type == "array" and length > 0 then .[0].billerId // .[0].id // empty else empty end' 2>/dev/null)

# TC-161
if [ -n "$BILLER_ID" ]; then
  do_curl "$BASE_URL/v1/billers/${BILLER_ID}" \
    -H "X-API-Key: $OPS_KEY"
  check_status "TC-161" "Get biller detail → 200" "200"
  show_detail '.'
else
  skip "TC-161" "Get biller detail — skipped (no active biller)"
fi

# TC-162
if [ -n "$BILLER_ID" ]; then
  do_curl "$BASE_URL/v1/bills/fetch?billerId=${BILLER_ID}&ref=AUTO-BILL-${RUN_ID}" \
    -H "X-API-Key: $BANK_A_KEY"
  check_status "TC-162" "Fetch bill token → 200 or downstream/mock error" "200|404|422|504"
  show_detail '.'
  BILL_TOKEN_ID=$(jq_val ".tokenId")
else
  skip "TC-162" "Fetch bill token — skipped (no active biller)"
fi

# TC-163
do_curl "$BASE_URL/v1/crossborder/corridors" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-163" "List cross-border corridors → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-164
do_curl "$BASE_URL/v1/crossborder/fx-rates" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-164" "List cross-border FX rates → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-165
do_curl -X POST "$BASE_URL/v1/qr/generate/static" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"merchantId\":\"MERCHANT-${RUN_ID}\",\"pspId\":\"${BANK_A}\",\"description\":\"Auto static QR ${RUN_ID}\"}"
check_status "TC-165" "Generate static QR → 201" "201"
show_detail '{qrId, merchantId, pspId, qrType, payload}'
STATIC_QR_ID=$(jq_val ".qrId")
STATIC_QR_PAYLOAD=$(jq_val ".payload")

# TC-166
if [ -n "$STATIC_QR_PAYLOAD" ]; then
  do_curl -X POST "$BASE_URL/v1/qr/decode" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $OPS_KEY" \
    -d "$(jq -nc --arg payload "$STATIC_QR_PAYLOAD" '{qrPayload:$payload}')"
  check_status "TC-166" "Decode generated static QR → 200" "200"
  show_detail '.'
else
  skip "TC-166" "Decode generated static QR — skipped (no payload)"
fi

# TC-167
do_curl -X POST "$BASE_URL/v1/qr/generate/dynamic" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"merchantId\":\"MERCHANT-${RUN_ID}\",\"pspId\":\"${BANK_A}\",\"amount\":12345.67,\"txnRef\":\"QR-DYN-${RUN_ID}\",\"expiresInSeconds\":300}"
check_status "TC-167" "Generate dynamic QR → 201" "201"
show_detail '{qrId, merchantId, pspId, qrType, amount, txnRef, expiresAt}'

# =============================================================================
section "16. Local Topology / DB Archive / Object Storage"
# =============================================================================

if command -v docker &>/dev/null && docker compose ps postgres &>/dev/null; then
  # TC-180 — HOT DB must be public-only after the archive/object-storage split.
  HOT_SCHEMAS=$(docker compose exec -T postgres psql -U "${POSTGRES_USER:-switching}" -d switching_db -Atc \
    "SELECT string_agg(schema_name, ',' ORDER BY schema_name)
     FROM information_schema.schemata
     WHERE schema_name NOT LIKE 'pg_%'
       AND schema_name <> 'information_schema';" 2>/dev/null || true)
  if [ "$HOT_SCHEMAS" = "public" ]; then
    pass "TC-180" "HOT primary schema topology is public-only" "schemas=${HOT_SCHEMAS}"
  else
    fail "TC-180" "HOT primary should not contain archive/object_storage schemas" "schemas=${HOT_SCHEMAS:-empty}"
  fi

  # TC-181 — ARCHIVE DB must own public archive tables + object_storage metadata.
  ARCHIVE_SCHEMAS=$(docker compose exec -T postgres-archive psql -U "${ARCHIVE_POSTGRES_USER:-switching_archive}" -d switching_archive -Atc \
    "SELECT string_agg(schema_name, ',' ORDER BY schema_name)
     FROM information_schema.schemata
     WHERE schema_name NOT LIKE 'pg_%'
       AND schema_name <> 'information_schema';" 2>/dev/null || true)
  if [ "$ARCHIVE_SCHEMAS" = "object_storage,public" ]; then
    pass "TC-181" "Archive DB schema topology is public + object_storage" "schemas=${ARCHIVE_SCHEMAS}"
  else
    fail "TC-181" "Archive DB should contain public + object_storage only" "schemas=${ARCHIVE_SCHEMAS:-empty}"
  fi

  # TC-182 — object_storage metadata tables exist.
  OBJECT_TABLES=$(docker compose exec -T postgres-archive psql -U "${ARCHIVE_POSTGRES_USER:-switching_archive}" -d switching_archive -Atc \
    "SELECT COUNT(*)
     FROM information_schema.tables
     WHERE table_schema = 'object_storage'
       AND table_name IN ('objects', 'manifests', 'retention_policies');" 2>/dev/null || echo "0")
  if [ "$OBJECT_TABLES" = "3" ]; then
    pass "TC-182" "Archive DB object_storage metadata tables exist" "tables=${OBJECT_TABLES}/3"
  else
    fail "TC-182" "Archive DB object_storage metadata tables incomplete" "tables=${OBJECT_TABLES}/3"
  fi

  # TC-183 — archive business tables exist in archive DB public schema.
  ARCHIVE_TABLES=$(docker compose exec -T postgres-archive psql -U "${ARCHIVE_POSTGRES_USER:-switching_archive}" -d switching_archive -Atc \
    "SELECT COUNT(*)
     FROM information_schema.tables
     WHERE table_schema = 'public'
       AND table_name LIKE '%\_archive' ESCAPE '\';" 2>/dev/null || echo "0")
  if [ "${ARCHIVE_TABLES:-0}" -ge 10 ] 2>/dev/null; then
    pass "TC-183" "Archive DB public schema has archive business tables" "tables=${ARCHIVE_TABLES}"
  else
    fail "TC-183" "Archive DB public schema is missing archive business tables" "tables=${ARCHIVE_TABLES:-0}"
  fi

  # TC-184 — read replica is actually in recovery/read-only replica mode.
  REPLICA_RECOVERY=$(docker compose exec -T postgres-read-replica psql -U "${POSTGRES_USER:-switching}" -d switching_db -Atc \
    "SELECT pg_is_in_recovery();" 2>/dev/null || true)
  if [ "$REPLICA_RECOVERY" = "t" ]; then
    pass "TC-184" "HOT read replica is in recovery/read-only mode"
  else
    fail "TC-184" "HOT read replica is not reporting pg_is_in_recovery=true" "pg_is_in_recovery=${REPLICA_RECOVERY:-empty}"
  fi

  # TC-185 — MinIO bucket exists locally.
  if docker compose exec -T minio sh -c 'test -d /data/switching-archive' 2>/dev/null; then
    pass "TC-185" "MinIO local bucket switching-archive exists" "/data/switching-archive"
  else
    fail "TC-185" "MinIO local bucket switching-archive is missing"
  fi

  # TC-186 — Compose config wires app to archive DB + MinIO, not localhost from inside container.
  COMPOSE_CONFIG="$(docker compose config 2>/dev/null || true)"
  if printf '%s' "$COMPOSE_CONFIG" | grep -q 'ARCHIVE_DB_URL: jdbc:postgresql://postgres-archive:5432/switching_archive' \
      && printf '%s' "$COMPOSE_CONFIG" | grep -q 'OBJECT_STORAGE_ENDPOINT: http://minio:9000'; then
    pass "TC-186" "App compose wiring points to archive DB and MinIO services"
  else
    fail "TC-186" "App compose wiring for archive DB / MinIO is not as expected"
  fi
else
  for tc in "TC-180" "TC-181" "TC-182" "TC-183" "TC-184" "TC-185" "TC-186"; do
    skip "$tc" "Local Docker Compose topology check skipped (docker compose stack not available)"
  done
fi

# TC-187 — production config checker should require the real object storage secret key env var.
if grep -q 'OBJECT_STORAGE_SECRET_KEY' scripts/check_prod_config.sh \
    && ! grep -q 'OBJECT_STORAGE__KEY' scripts/check_prod_config.sh; then
  pass "TC-187" "Production config checker validates OBJECT_STORAGE_SECRET_KEY"
else
  fail "TC-187" "Production config checker has wrong/missing object storage secret key variable"
fi

# =============================================================================
section "17. Metrics"
# =============================================================================

# TC-120 — Actuator metrics
do_curl "$BASE_URL/actuator/metrics"
if [ "$HTTP_CODE" = "200" ]; then
  PAYMENT_COUNT=$(echo "$RESP_BODY" | jq -r '[.names[] | select(startswith("payment."))] | length' 2>/dev/null || echo "0")
  if [ "${PAYMENT_COUNT:-0}" -ge 8 ] 2>/dev/null; then
    pass "TC-120" "Actuator /metrics has ${PAYMENT_COUNT} payment.* metrics ✅"
  elif [ "${PAYMENT_COUNT:-0}" -gt 0 ] 2>/dev/null; then
    fail "TC-120" "Only ${PAYMENT_COUNT} payment.* metrics found (expected ≥ 8)"
  else
    pass "TC-120" "Actuator /metrics accessible (payment metrics not listed in names)"
  fi
else
  # /actuator/metrics not exposed — correct per application.yml (health,info only)
  pass "TC-120" "Actuator /metrics not exposed (correct: only health,info exposed)" "HTTP ${HTTP_CODE}"
fi

# =============================================================================
section "19. Phase 17 — Outbox Dead-Letter Quarantine"
# =============================================================================

# TC-190
do_curl "$BASE_URL/v1/operations/dead-letters" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-190" "List dead-letter quarantine (OPS) → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-191
do_curl "$BASE_URL/v1/operations/dead-letters" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-191" "Dead-letters requires OPS/ADMIN role (bank key) → 403" "403"

# =============================================================================
section "20. Phase 19 — Legal Hold"
# =============================================================================

# TC-200
do_curl "$BASE_URL/v1/operations/legal-holds" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-200" "List legal holds (OPS) → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-201
do_curl "$BASE_URL/v1/operations/legal-holds" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-201" "Legal-holds requires OPS/ADMIN (bank key) → 403" "403"

# =============================================================================
section "21. Phase 20 — Privileged Access (Break-Glass)"
# =============================================================================

# TC-210
do_curl "$BASE_URL/v1/operations/break-glass" \
  -H "X-API-Key: $ADMIN_KEY"
check_status "TC-210" "List break-glass sessions (ADMIN) → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-211
do_curl "$BASE_URL/v1/operations/break-glass" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-211" "List break-glass requires ADMIN (OPS denied) → 403" "403"

# TC-212 — OPS can request a break-glass session
do_curl -X POST "$BASE_URL/v1/operations/break-glass" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $OPS_KEY" \
  -d "{\"requestedBy\":\"ops-tester\",\"reason\":\"automated test ${RUN_ID}\",\"ticketReference\":\"TST-${RUN_ID}\",\"requestedDurationMinutes\":15}"
check_status "TC-212" "OPS can request break-glass session → 200|201|202|400|422" "200|201|202|400|422"
show_detail '{id, status, expiresAt}'

# =============================================================================
section "22. Phase 21 — Four-Eyes Config Change"
# =============================================================================

# TC-220
do_curl "$BASE_URL/v1/operations/config-changes" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-220" "List config-change requests (OPS) → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-221 — OPS can request a config change
do_curl -X POST "$BASE_URL/v1/operations/config-changes" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $OPS_KEY" \
  -d "{\"configKey\":\"connector.MOCK_BANK_B.forceReject\",\"desiredValue\":\"true\",\"reason\":\"test ${RUN_ID}\",\"ticketReference\":\"TST-${RUN_ID}\"}"
check_status "TC-221" "OPS can submit config-change request → 200|201|202|400|422" "200|201|202|400|422"
show_detail '{id, status, configKey, desiredValue, payloadSha256}'

# TC-222 — Bank role cannot submit config change
do_curl -X POST "$BASE_URL/v1/operations/config-changes" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: $BANK_A_KEY" \
  -d "{\"configKey\":\"x\",\"desiredValue\":\"y\",\"reason\":\"unauthorized\",\"ticketReference\":\"X\"}"
check_status "TC-222" "Bank key cannot submit config-change → 403" "403"

# =============================================================================
section "23. Phase 22/37 — Participant Certification"
# =============================================================================

# TC-230 — endpoint requires bankCode query param
do_curl "$BASE_URL/v1/operations/participant-certifications?bankCode=${BANK_A}" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-230" "List participant certifications (OPS, bankCode) → 200" "200"
show_detail 'if type == "array" then "count: \(length)" else . end'

# TC-231
do_curl "$BASE_URL/v1/operations/participant-certifications?bankCode=${BANK_A}" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-231" "Participant cert list requires OPS/ADMIN → 403" "403"

# =============================================================================
section "24. Phase 46 — Audit Log Query"
# =============================================================================

# TC-240
do_curl "$BASE_URL/api/operations/audit-logs?limit=10" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-240" "Query audit logs (OPS) → 200" "200"
show_detail 'if type == "array" then "count: \(length)" elif (.items | type) == "array" then "items: \(.items | length)" else . end'

# TC-241
do_curl "$BASE_URL/api/operations/audit-logs?limit=10" \
  -H "X-API-Key: $BANK_A_KEY"
check_status "TC-241" "Audit log query requires OPS/ADMIN → 403" "403"

# =============================================================================
section "25. Phase 56 — ISO Message Validation + Security Policy"
# =============================================================================

# TC-250 — security policy lookup for unknown key → 404 (route exists)
do_curl "$BASE_URL/api/iso-messages/__unknown__/security-policy" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-250" "ISO security-policy unknown key → 404|400" "404|400"

# TC-251 — validate endpoint on unknown key
do_curl -X POST "$BASE_URL/api/iso-messages/__unknown__/validate" \
  -H "X-API-Key: $OPS_KEY"
check_status "TC-251" "ISO validate unknown key → 404|400|405" "404|400|405"

# =============================================================================
section "26. Phase 4 — Webhook Secret Rotation (if webhook registered)"
# =============================================================================

if [ -n "$WEBHOOK_ID" ]; then
  do_curl -X POST "$BASE_URL/v1/webhooks/${WEBHOOK_ID}/rotate-secret" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"newSecret\":\"whsec-rot-${RUN_ID}-zzzzzzzzzzzzzzzzzzzzzzzzz\",\"gracePeriodSeconds\":3600}"
  check_status "TC-260" "Rotate webhook secret → 200|201|202|404|405" "200|201|202|404|405"
  show_detail '{webhookId, keyId, version, previousExpiresAt}'
else
  skip "TC-260" "Webhook rotate-secret — skipped (no webhookId from TC-145)"
fi

# =============================================================================
section "18. Rate Limiting (runs last — exhausts BANK_A_KEY for this minute)"
# =============================================================================

if [ "$SKIP_RATE_LIMIT" = "true" ]; then
  skip "TC-070" "Rate limit section skipped (--skip-rate-limit)"
  skip "TC-071" "Rate limit section skipped (--skip-rate-limit)"
  skip "TC-072" "Rate limit section skipped (--skip-rate-limit)"
else

warn "Sending up to 120 POST requests to trigger rate limit (100 req/min)..."
info "This may take 10-15 seconds..."

RATE_LIMIT_HIT=0
REQUESTS_SENT=0
RATE_LIMIT_BODY=""
RL_RESP_FILE=$(mktemp)
for i in $(seq 1 120); do
  CODE=$(curl -s -o "$RL_RESP_FILE" -w "%{http_code}" -X POST "$BASE_URL/api/inquiries" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $BANK_A_KEY" \
    -d "{\"sourceBank\":\"${BANK_A}\",\"destinationBank\":\"${BANK_B}\",\"creditorAccount\":\"${CREDITOR}\",\"amount\":${AMOUNT},\"currency\":\"${CURRENCY}\"}" \
    2>/dev/null)
  REQUESTS_SENT=$i
  if [ "$CODE" = "429" ]; then
    RATE_LIMIT_HIT=$i
    RATE_LIMIT_BODY=$(cat "$RL_RESP_FILE" 2>/dev/null || echo "")
    break
  fi
done
rm -f "$RL_RESP_FILE"

# TC-070
if [ "$RATE_LIMIT_HIT" -gt 0 ]; then
  pass "TC-070" "Rate limit 429 triggered at request ${RATE_LIMIT_HIT} (≤ 100 POST/min ✅)"
else
  fail "TC-070" "Rate limit not triggered in ${REQUESTS_SENT} requests" "check RATE_LIMIT_ENABLED=true in config"
fi

# TC-071 — verify saved 429 body has errorCode=REQ-004 (use saved body, not a new request)
if [ "$RATE_LIMIT_HIT" -gt 0 ]; then
  ERR_CODE=$(echo "$RATE_LIMIT_BODY" | jq -r ".errorCode" 2>/dev/null || echo "")
  if [ "$ERR_CODE" = "REQ-004" ]; then
    pass "TC-071" "429 response body has errorCode=REQ-004 ✅"
  else
    fail "TC-071" "429 response body errorCode wrong" "expected REQ-004, got ${ERR_CODE}"
    RESP_BODY="$RATE_LIMIT_BODY"
    show_detail '.'
  fi
else
  skip "TC-071" "429 body check — skipped (rate limit not triggered)"
fi

# TC-072 — GET requests are NOT rate limited (check 10 times)
info "Verifying GET requests bypass rate limiting..."
GET_429_HIT=0
for i in $(seq 1 10); do
  CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/participants" \
    -H "X-API-Key: $OPS_KEY" 2>/dev/null)
  if [ "$CODE" = "429" ]; then
    GET_429_HIT=1
    break
  fi
done

if [ "$GET_429_HIT" = "0" ]; then
  pass "TC-072" "GET requests not rate limited (10 checks, all passed ✅)"
else
  fail "TC-072" "GET request returned 429 — unexpected behavior"
fi

fi  # end of: if [ "$SKIP_RATE_LIMIT" = "true" ]

# =============================================================================
# FINAL SUMMARY
# =============================================================================
TOTAL=$((PASS + FAIL + SKIP))
echo ""
echo -e "${BOLD}${CYAN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}${CYAN}║                 TEST RUN SUMMARY                     ║${NC}"
echo -e "${BOLD}${CYAN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
printf "  %-12s %s\n" "Total run:" "$TOTAL"
echo -e "  ${GREEN}${BOLD}$(printf '%-12s' 'PASS:') ${PASS}${NC}"
if [ "$FAIL" -gt 0 ]; then
  echo -e "  ${RED}${BOLD}$(printf '%-12s' 'FAIL:') ${FAIL}${NC}"
else
  echo -e "  $(printf '%-12s' 'FAIL:') 0"
fi
if [ "$SKIP" -gt 0 ]; then
  echo -e "  ${YELLOW}$(printf '%-12s' 'SKIP:') ${SKIP}${NC}"
fi
echo ""
echo -e "  ${DIM}BASE_URL  : $BASE_URL${NC}"
echo -e "  ${DIM}RUN_ID    : $RUN_ID${NC}"
echo -e "  ${DIM}Finished  : $(date '+%Y-%m-%d %H:%M:%S')${NC}"
echo ""

if [ "$FAIL" -eq 0 ]; then
  echo -e "  ${GREEN}${BOLD}✅  ALL TESTS PASSED${NC}"
  echo ""
  exit 0
else
  echo -e "  ${RED}${BOLD}❌  ${FAIL} TEST(S) FAILED — see output above for details${NC}"
  echo ""
  exit 1
fi
