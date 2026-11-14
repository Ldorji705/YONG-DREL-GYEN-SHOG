#!/usr/bin/env bash

set -uo pipefail

EVENT_URL="http://localhost:8084/api/events"
PRIZE_URL="http://localhost:8086/api/prizes"

TOKEN=$(jq -r '.accessToken' /tmp/yongdrel-login.json)

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "ERROR: Missing JWT token. Log in again."
  exit 1
fi

PASS=0
FAIL=0
BODY=""
STATUS=""
EVENT_ID=""
PRIZE_ID=""

request() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local authenticated="${4:-yes}"
  local response

  local args=(-sS -X "$method" "$url" -H "Content-Type: application/json")

  if [[ "$authenticated" == "yes" ]]; then
    args+=(-H "Authorization: Bearer $TOKEN")
  fi

  if [[ -n "$data" ]]; then
    args+=(-d "$data")
  fi

  if ! response=$(curl "${args[@]}" -w $'\n%{http_code}'); then
    BODY=""
    STATUS="000"
    return
  fi

  STATUS="${response##*$'\n'}"
  BODY="${response%$'\n'*}"
}

check() {
  local name="$1"
  local expected="$2"
  local actual="$3"

  if [[ "$expected" == "$actual" ]]; then
    echo "PASS: $name (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo "FAIL: $name (expected $expected, got $actual)"
    if [[ -n "$BODY" ]]; then
      echo "Response: $BODY"
    fi
    FAIL=$((FAIL + 1))
  fi
}

echo ""
echo "===================================="
echo " EVENT SERVICE CRUD TESTS"
echo "===================================="

EVENT_CREATE='{
  "title": "CRUD Test Lottery",
  "description": "Temporary event for API testing",
  "status": "DRAFT",
  "startDate": "2026-12-01T10:00:00",
  "endDate": "2026-12-10T18:00:00"
}'

EVENT_UPDATE='{
  "title": "Updated CRUD Test Lottery",
  "description": "Updated event description",
  "status": "SCHEDULED",
  "startDate": "2026-12-02T10:00:00",
  "endDate": "2026-12-12T18:00:00"
}'

request POST "$EVENT_URL" "$EVENT_CREATE"
check "Create event" 201 "$STATUS"

if [[ "$STATUS" == "201" ]]; then
  EVENT_ID=$(printf '%s' "$BODY" | jq -r '.id // empty' 2>/dev/null)
fi

request GET "$EVENT_URL"
check "List all events" 200 "$STATUS"

if [[ -n "$EVENT_ID" ]]; then
  request GET "$EVENT_URL/$EVENT_ID"
  check "Get event by ID" 200 "$STATUS"

  request PUT "$EVENT_URL/$EVENT_ID" "$EVENT_UPDATE"
  check "Update event" 200 "$STATUS"

  if [[ "$STATUS" == "200" ]]; then
    TITLE=$(printf '%s' "$BODY" | jq -r '.title // empty')
    if [[ "$TITLE" == "Updated CRUD Test Lottery" ]]; then
      echo "PASS: Updated event title verified"
      PASS=$((PASS + 1))
    else
      echo "FAIL: Updated event title not saved"
      FAIL=$((FAIL + 1))
    fi
  fi

  request DELETE "$EVENT_URL/$EVENT_ID"
  check "Delete event" 204 "$STATUS"

  if [[ "$STATUS" == "204" ]]; then
    request GET "$EVENT_URL/$EVENT_ID"
    check "Deleted event returns 404" 404 "$STATUS"
  fi
else
  echo "SKIP: Event ID-dependent tests (creation failed or ID missing)"
  FAIL=$((FAIL + 1))
fi

INVALID_EVENT='{
  "title": "",
  "description": "Invalid event",
  "status": "DRAFT",
  "startDate": "2026-12-10T10:00:00",
  "endDate": "2026-12-01T10:00:00"
}'

request POST "$EVENT_URL" "$INVALID_EVENT"
check "Reject invalid event" 400 "$STATUS"

request GET "$EVENT_URL" "" "no"
check "Reject unauthenticated event access" 401 "$STATUS"

echo ""
echo "===================================="
echo " PRIZE CATALOG CRUD TESTS"
echo "===================================="

PRIZE_CREATE='{
  "name": "CRUD Test Grand Prize",
  "description": "Temporary prize for testing",
  "value": 50000.00,
  "quantity": 1,
  "status": "AVAILABLE"
}'

PRIZE_UPDATE='{
  "name": "Updated CRUD Test Prize",
  "description": "Updated prize description",
  "value": 75000.00,
  "quantity": 2,
  "status": "AVAILABLE"
}'

request POST "$PRIZE_URL" "$PRIZE_CREATE"
check "Create prize" 201 "$STATUS"

if [[ "$STATUS" == "201" ]]; then
  PRIZE_ID=$(printf '%s' "$BODY" | jq -r '.id // empty' 2>/dev/null)
fi

request GET "$PRIZE_URL"
check "List all prizes" 200 "$STATUS"

if [[ -n "$PRIZE_ID" ]]; then
  request GET "$PRIZE_URL/$PRIZE_ID"
  check "Get prize by ID" 200 "$STATUS"

  request PUT "$PRIZE_URL/$PRIZE_ID" "$PRIZE_UPDATE"
  check "Update prize" 200 "$STATUS"

  if [[ "$STATUS" == "200" ]]; then
    NAME=$(printf '%s' "$BODY" | jq -r '.name // empty')
    if [[ "$NAME" == "Updated CRUD Test Prize" ]]; then
      echo "PASS: Updated prize name verified"
      PASS=$((PASS + 1))
    else
      echo "FAIL: Updated prize name not saved"
      FAIL=$((FAIL + 1))
    fi
  fi

  request DELETE "$PRIZE_URL/$PRIZE_ID"
  check "Delete prize" 204 "$STATUS"

  if [[ "$STATUS" == "204" ]]; then
    request GET "$PRIZE_URL/$PRIZE_ID"
    check "Deleted prize returns 404" 404 "$STATUS"
  fi
else
  echo "SKIP: Prize ID-dependent tests (creation failed or ID missing)"
  FAIL=$((FAIL + 1))
fi

INVALID_PRIZE='{
  "name": "",
  "description": "Invalid prize",
  "value": -100,
  "quantity": -1,
  "status": "AVAILABLE"
}'

request POST "$PRIZE_URL" "$INVALID_PRIZE"
check "Reject invalid prize" 400 "$STATUS"

request GET "$PRIZE_URL" "" "no"
check "Reject unauthenticated prize access" 401 "$STATUS"

echo ""
echo "===================================="
echo " FINAL TEST SUMMARY"
echo "===================================="
echo "Passed: $PASS"
echo "Failed: $FAIL"
echo "===================================="

if [[ "$FAIL" -eq 0 ]]; then
  echo "ALL EXECUTED TESTS PASSED"
  exit 0
else
  echo "SOME TESTS FAILED"
  exit 1
fi
