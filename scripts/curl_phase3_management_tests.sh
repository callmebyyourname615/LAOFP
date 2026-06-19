#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_ID="${RUN_ID:-$(date +%s)}"

BANK_CODE="TST_${RUN_ID}"
BANK_NAME="Test Bank ${RUN_ID}"
CONNECTOR_NAME="MOCK_${BANK_CODE}_CONNECTOR"
ROUTE_CODE="ROUTE_BANK_A_TO_${BANK_CODE}_PACS008_PRIMARY"

LAST_BODY=""
LAST_STATUS=""

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

  if [[ "$LAST_STATUS" != "$expected" ]]; then
    echo "FAILED: expected HTTP $expected but got $LAST_STATUS"
    echo "$LAST_BODY"
    exit 1
  fi
}

expect_body_contains() {
  local expected="$1"

  if ! grep -Fq "$expected" <<< "$LAST_BODY"; then
    echo "FAILED: expected response body to contain: $expected"
    echo "$LAST_BODY"
    exit 1
  fi
}

pass() {
  echo "PASS: $1"
}

tc_create_participant_success() {
  request POST "/api/participants" "{
    \"bankCode\": \"${BANK_CODE}\",
    \"bankName\": \"${BANK_NAME}\",
    \"status\": \"ACTIVE\",
    \"participantType\": \"BANK\",
    \"country\": \"TH\",
    \"currency\": \"THB\"
  }"

  expect_status 201
  expect_body_contains "\"bankCode\":\"${BANK_CODE}\""
  expect_body_contains "\"status\":\"ACTIVE\""
  pass "create participant success"
}

tc_create_participant_duplicate_conflict() {
  request POST "/api/participants" "{
    \"bankCode\": \"${BANK_CODE}\",
    \"bankName\": \"Duplicate ${BANK_NAME}\",
    \"country\": \"TH\",
    \"currency\": \"THB\"
  }"

  expect_status 409
  expect_body_contains "\"errorCode\":\"PRT-003\""
  expect_body_contains "Participant already exists: ${BANK_CODE}"
  pass "create participant duplicate conflict"
}

tc_update_participant_status() {
  request PATCH "/api/participants/${BANK_CODE}" '{
    "status": "INACTIVE"
  }'

  expect_status 200
  expect_body_contains "\"bankCode\":\"${BANK_CODE}\""
  expect_body_contains "\"status\":\"INACTIVE\""
  pass "update participant status"
}

tc_create_participant_missing_required_field() {
  request POST "/api/participants" "{
    \"bankCode\": \"MISS_${RUN_ID}\"
  }"

  expect_status 400
  expect_body_contains "\"errorCode\":\"REQ-001\""
  expect_body_contains "bankName is required"
  pass "create participant missing required field"
}

tc_update_participant_not_found() {
  request PATCH "/api/participants/NO_BANK_${RUN_ID}" '{
    "status": "INACTIVE"
  }'

  expect_status 404
  expect_body_contains "\"errorCode\":\"PRT-001\""
  expect_body_contains "Participant not found: NO_BANK_${RUN_ID}"
  pass "update participant not found"
}

tc_create_connector_config_success() {
  request POST "/api/connector-configs" "{
    \"connectorName\": \"${CONNECTOR_NAME}\",
    \"bankCode\": \"${BANK_CODE}\",
    \"connectorType\": \"MOCK\",
    \"timeoutMs\": 5000,
    \"enabled\": true,
    \"forceReject\": false,
    \"rejectReasonCode\": \"AC01\",
    \"rejectReasonMessage\": \"Mock rejected transfer\"
  }"

  expect_status 201
  expect_body_contains "\"connectorName\":\"${CONNECTOR_NAME}\""
  expect_body_contains "\"bankCode\":\"${BANK_CODE}\""
  expect_body_contains "\"connectorType\":\"MOCK\""
  pass "create connector config success"
}

tc_create_connector_config_duplicate_conflict() {
  request POST "/api/connector-configs" "{
    \"connectorName\": \"${CONNECTOR_NAME}\",
    \"bankCode\": \"${BANK_CODE}\",
    \"connectorType\": \"MOCK\"
  }"

  expect_status 409
  expect_body_contains "\"errorCode\":\"CON-002\""
  expect_body_contains "Connector config already exists: ${CONNECTOR_NAME}"
  pass "create connector config duplicate conflict"
}

tc_update_connector_config_force_reject() {
  request PATCH "/api/connector-configs/${CONNECTOR_NAME}" '{
    "enabled": true,
    "forceReject": true,
    "rejectReasonCode": "AC04",
    "rejectReasonMessage": "Closed account - force reject"
  }'

  expect_status 200
  expect_body_contains "\"forceReject\":true"
  expect_body_contains "\"rejectReasonCode\":\"AC04\""
  pass "update connector config force reject"
}

tc_create_connector_config_invalid_type() {
  request POST "/api/connector-configs" "{
    \"connectorName\": \"BAD_${CONNECTOR_NAME}\",
    \"bankCode\": \"${BANK_CODE}\",
    \"connectorType\": \"FTP\"
  }"

  expect_status 400
  expect_body_contains "\"errorCode\":\"REQ-001\""
  expect_body_contains "Invalid connectorType: FTP"
  pass "create connector config invalid type"
}

tc_create_routing_rule_success() {
  request POST "/api/routing-rules" "{
    \"routeCode\": \"${ROUTE_CODE}\",
    \"sourceBank\": \"BANK_A\",
    \"destinationBank\": \"${BANK_CODE}\",
    \"messageType\": \"PACS_008\",
    \"connectorName\": \"${CONNECTOR_NAME}\",
    \"priority\": 1,
    \"enabled\": true
  }"

  expect_status 201
  expect_body_contains "\"routeCode\":\"${ROUTE_CODE}\""
  expect_body_contains "\"connectorName\":\"${CONNECTOR_NAME}\""
  pass "create routing rule success"
}

tc_create_routing_rule_duplicate_conflict() {
  request POST "/api/routing-rules" "{
    \"routeCode\": \"${ROUTE_CODE}\",
    \"sourceBank\": \"BANK_A\",
    \"destinationBank\": \"${BANK_CODE}\",
    \"messageType\": \"PACS_008\",
    \"connectorName\": \"${CONNECTOR_NAME}\"
  }"

  expect_status 409
  expect_body_contains "\"errorCode\":\"RTE-002\""
  expect_body_contains "Routing rule already exists: ${ROUTE_CODE}"
  pass "create routing rule duplicate conflict"
}

tc_update_routing_rule_disable() {
  request PATCH "/api/routing-rules/${ROUTE_CODE}" '{
    "enabled": false
  }'

  expect_status 200
  expect_body_contains "\"enabled\":false"
  pass "update routing rule disable"
}

tc_resolve_routing_rule_disabled_not_found() {
  request GET "/api/routing-rules/resolve?sourceBank=BANK_A&destinationBank=${BANK_CODE}&messageType=PACS_008"

  expect_status 422
  expect_body_contains "\"errorCode\":\"RTE-001\""
  pass "resolve disabled routing rule not found"
}

tc_update_routing_rule_enable() {
  request PATCH "/api/routing-rules/${ROUTE_CODE}" '{
    "enabled": true
  }'

  expect_status 200
  expect_body_contains "\"enabled\":true"
  pass "update routing rule enable"
}

tc_resolve_routing_rule_success() {
  request GET "/api/routing-rules/resolve?sourceBank=BANK_A&destinationBank=${BANK_CODE}&messageType=PACS_008"

  expect_status 200
  expect_body_contains "\"routeCode\":\"${ROUTE_CODE}\""
  expect_body_contains "\"connectorName\":\"${CONNECTOR_NAME}\""
  pass "resolve routing rule success"
}

tc_create_routing_rule_invalid_message_type() {
  request POST "/api/routing-rules" "{
    \"routeCode\": \"BAD_ROUTE_${RUN_ID}\",
    \"sourceBank\": \"BANK_A\",
    \"destinationBank\": \"${BANK_CODE}\",
    \"messageType\": \"INVALID_TYPE\",
    \"connectorName\": \"${CONNECTOR_NAME}\"
  }"

  expect_status 400
  expect_body_contains "\"errorCode\":\"REQ-001\""
  expect_body_contains "Invalid messageType: INVALID_TYPE"
  pass "create routing rule invalid message type"
}

run_all() {
  echo "BASE_URL=${BASE_URL}"
  echo "RUN_ID=${RUN_ID}"

  tc_create_participant_success
  tc_create_participant_duplicate_conflict
  tc_update_participant_status
  tc_create_participant_missing_required_field
  tc_update_participant_not_found

  request PATCH "/api/participants/${BANK_CODE}" '{"status":"ACTIVE"}'
  expect_status 200

  tc_create_connector_config_success
  tc_create_connector_config_duplicate_conflict
  tc_update_connector_config_force_reject
  tc_create_connector_config_invalid_type

  tc_create_routing_rule_success
  tc_create_routing_rule_duplicate_conflict
  tc_update_routing_rule_disable
  tc_resolve_routing_rule_disabled_not_found
  tc_update_routing_rule_enable
  tc_resolve_routing_rule_success
  tc_create_routing_rule_invalid_message_type

  echo "ALL CURL TEST CASES PASSED"
}

"${1:-run_all}"
