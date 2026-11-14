#!/usr/bin/env bash
# Authenticated end-to-end API test of the Audit Log Service through the API Gateway,
# including the cross-service flow Fraud & Risk Service --RabbitMQ--> Audit Log Service.
#
# Usage:
#   JWT_PRIVATE_KEY=../../infrastructure/keys/private.pem ./scripts/api-smoke-test.sh
#
# Env:
#   JWT_PRIVATE_KEY  RSA private key of the Auth Service (dev keys only), used to mint test tokens
#   GATEWAY_URL      default http://localhost:8080
#   SERVICE_URL      default http://localhost:8091   (health check)
#   SKIP_FRAUD_FLOW  set to 1 if fraud-risk-service is not running
#   EVIDENCE_DIR     default docs/evidence
set -uo pipefail

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
SERVICE_URL=${SERVICE_URL:-http://localhost:8091}
EVIDENCE_DIR=${EVIDENCE_DIR:-docs/evidence}
SKIP_FRAUD_FLOW=${SKIP_FRAUD_FLOW:-0}
: "${JWT_PRIVATE_KEY:?set JWT_PRIVATE_KEY to the Auth Service dev private key (PEM)}"

mkdir -p "$EVIDENCE_DIR"
LOG="$EVIDENCE_DIR/api-smoke-test-$(date +%Y%m%d-%H%M%S).txt"
exec > >(tee "$LOG") 2>&1

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
# mint <sub> <role> [tenantId] [iss] [aud] [expOffsetSeconds]
mint() {
  local sub=$1 role=$2 tenant=${3:-} iss=${4:-yongdrel-auth} aud=${5:-yongdrel-api} off=${6:-900}
  local now; now=$(date +%s)
  local t=""; [ -n "$tenant" ] && t=",\"tenantId\":$tenant"
  local h p s
  h=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  p=$(printf '{"iss":"%s","sub":"%s","aud":["%s"],"iat":%d,"exp":%d,"role":"%s"%s}' \
        "$iss" "$sub" "$aud" "$now" "$((now + off))" "$role" "$t" | b64url)
  s=$(printf '%s.%s' "$h" "$p" | openssl dgst -sha256 -sign "$JWT_PRIVATE_KEY" | b64url)
  printf '%s.%s.%s' "$h" "$p" "$s"
}
uuid() { (uuidgen 2>/dev/null || cat /proc/sys/kernel/random/uuid) | tr 'A-Z' 'a-z'; }

PASS=0; FAIL=0; BODY=""; HEADERS=""
# check <name> <expectedStatus> <token|-> <method> <path> [json] [extra header]
check() {
  local name=$1 expected=$2 token=$3 method=$4 path=$5 data=${6:-} extra=${7:-}
  local args=(-s -D /tmp/audit-smoke-h.$$ -o /tmp/audit-smoke-b.$$ -w '%{http_code}' -X "$method" "$GATEWAY_URL$path")
  [ "$token" != "-" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  [ -n "$extra" ] && args+=(-H "$extra")
  local status; status=$(curl "${args[@]}")
  BODY=$(cat /tmp/audit-smoke-b.$$ 2>/dev/null); HEADERS=$(cat /tmp/audit-smoke-h.$$ 2>/dev/null)
  rm -f /tmp/audit-smoke-b.$$ /tmp/audit-smoke-h.$$
  if [ "$status" = "$expected" ]; then
    PASS=$((PASS + 1)); printf 'PASS  %-58s %s %s -> %s\n' "$name" "$method" "$path" "$status"
  else
    FAIL=$((FAIL + 1)); printf 'FAIL  %-58s %s %s -> %s (expected %s)\n      body: %s\n' \
      "$name" "$method" "$path" "$status" "$expected" "$BODY"
  fi
}
expect() {  # expect <name> <haystack> <needle>
  if [[ "$2" == *"$3"* ]]; then PASS=$((PASS + 1)); printf 'PASS  %-58s contains %s\n' "$1" "$3"
  else FAIL=$((FAIL + 1)); printf 'FAIL  %-58s missing %s\n      got: %s\n' "$1" "$3" "$2"; fi
}
json_num() { printf '%s' "$BODY" | grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }

RUN=$(date +%s)
T=$((RUN % 100000 + 1000)); T_OTHER=$((T + 1)); S=$((RUN % 100000 + 5000)); S_OTHER=$((S + 1))
C="smoke-$RUN"
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SERVICE=$(mint 2 SERVICE); ADMIN=$(mint 1 ADMIN)
ORG=$(mint 3 ORGANIZER "$T"); ORG_OTHER=$(mint 4 ORGANIZER "$T_OTHER"); ORG_NO_TENANT=$(mint 5 ORGANIZER)
STUDENT=$(mint "$S" USER); STUDENT_OTHER=$(mint "$S_OTHER" USER)

event_json() {  # event_json <eventId> <actorType> <actorId|null> <action> [sourceService] [correlation|-] [occurredAt]
  local corr=""; [ "${6:-$C}" != "-" ] && corr="\"correlationId\":\"${6:-$C}\","
  local actor="\"actorId\":$3,"; [ "$3" = null ] && actor=""
  local role=""; [ "$2" = USER ] && role="\"actorRole\":\"STUDENT\","
  printf '{"eventId":"%s","tenantId":%s,"actorType":"%s",%s%s"action":"%s","outcome":"SUCCESS","targetType":"ORDER","targetId":"%s","sourceService":"%s",%s"occurredAt":"%s","details":{"amount":100,"run":%s}}' \
    "$1" "$T" "$2" "$actor" "$role" "$4" "$RUN" "${5:-payment-proof-service}" "$corr" "${7:-$NOW}" "$RUN"
}

echo "Audit Log Service API smoke test  gateway=$GATEWAY_URL  run=$RUN  tenant=$T  student=$S  $(date -u +%FT%TZ)"
echo

echo "== Health"
code=$(curl -s -o /dev/null -w '%{http_code}' "$SERVICE_URL/actuator/health")
if [ "$code" = 200 ]; then PASS=$((PASS + 1)); echo "PASS  health endpoint -> 200 $(curl -s "$SERVICE_URL/actuator/health")"
else FAIL=$((FAIL + 1)); echo "FAIL  health endpoint -> $code"; fi

echo; echo "== 401 invalid JWT"
check "no token"              401 -  GET /api/audit/events
check "garbage token"         401 "not.a.jwt" GET /api/audit/events
check "tampered signature"    401 "${ADMIN%?}x" GET /api/audit/events
check "expired token"         401 "$(mint 1 ADMIN '' yongdrel-auth yongdrel-api -60)" GET /api/audit/events
check "wrong issuer"          401 "$(mint 1 ADMIN '' evil-issuer)" GET /api/audit/events
check "wrong audience"        401 "$(mint 1 ADMIN '' yongdrel-auth other-api)" GET /api/audit/events
check "non-numeric subject"   401 "$(mint root ADMIN)" GET /api/audit/events

echo; echo "== Record audit events (append-only)"
E1=$(uuid); E2=$(uuid); E3=$(uuid)
check "service records a USER action"        201 "$SERVICE" POST /api/audit/events "$(event_json "$E1" USER "$S" PAYMENT_PROOF_UPLOADED)"
expect "integrity seal valid"                "$BODY" '"integrityValid":true'
expect "correlation id echoed in header"     "$HEADERS" "$C"
ID1=$(json_num id)
check "admin records a SYSTEM action"        201 "$ADMIN" POST /api/audit/events "$(event_json "$E2" SYSTEM null ORDER_CONFIRMED order-service)"
ID2=$(json_num id)
check "correlation id taken from header"     201 "$SERVICE" POST /api/audit/events \
  "$(event_json "$E3" USER "$S" TICKET_RESERVED ticket-inventory-service -)" "X-Correlation-Id: hdr-$RUN"
expect "body carries header correlation id"  "$BODY" "\"correlationId\":\"hdr-$RUN\""

echo; echo "== 409 duplicate / 422 bad cross-service reference"
check "same eventId again"                   409 "$SERVICE" POST /api/audit/events "$(event_json "$E1" USER "$S" PAYMENT_PROOF_UPLOADED)"
check "unknown sourceService"                422 "$SERVICE" POST /api/audit/events "$(event_json "$(uuid)" USER "$S" ORDER_CREATED billing-service)"

echo; echo "== 400 bad input"
check "validation errors"                    400 "$SERVICE" POST /api/audit/events \
  '{"eventId":"11111111-1111-1111-1111-111111111111","actorType":"USER","action":"paid","outcome":"SUCCESS","targetType":"ORDER","targetId":"a/b","sourceService":"Bad Name","occurredAt":"2026-01-01T00:00:00Z"}'
expect "fieldErrors returned"                "$BODY" '"fieldErrors"'
check "occurredAt in the future"             400 "$SERVICE" POST /api/audit/events "$(event_json "$(uuid)" USER "$S" ORDER_CREATED order-service "$C" 2099-01-01T00:00:00Z)"
check "eventId not a UUID"                   400 "$SERVICE" POST /api/audit/events '{"eventId":"123"}'
check "unknown enum value"                   400 "$SERVICE" POST /api/audit/events '{"actorType":"ROBOT"}'
check "malformed JSON"                       400 "$SERVICE" POST /api/audit/events '{"eventId":'
check "page size 0"                          400 "$ADMIN" GET "/api/audit/events?size=0"
check "reversed time range"                  400 "$ADMIN" GET "/api/audit/events?from=2026-12-01T00:00:00Z&to=2026-11-01T00:00:00Z"
check "bad date format"                      400 "$ADMIN" GET "/api/audit/events?from=yesterday"
check "bad correlation id"                   400 "$ADMIN" GET "/api/audit/correlations/bad%20id"
check "non-numeric id"                       400 "$ADMIN" GET /api/audit/events/abc

echo; echo "== Restricted search"
check "admin searches the correlation"       200 "$ADMIN" GET "/api/audit/events?correlationId=$C"
expect "admin sees both events"              "$BODY" '"totalElements":2'
check "organizer sees own tenant"            200 "$ORG" GET "/api/audit/events?correlationId=$C"
expect "organizer sees both events"          "$BODY" '"totalElements":2'
check "other organizer is scoped away"       200 "$ORG_OTHER" GET "/api/audit/events?correlationId=$C"
expect "other organizer sees nothing"        "$BODY" '"totalElements":0'
check "student sees own actions only"        200 "$STUDENT" GET "/api/audit/events?correlationId=$C"
expect "student sees 1 event"                "$BODY" '"totalElements":1'
check "filters: action + outcome + target"   200 "$ADMIN" GET "/api/audit/events?action=PAYMENT_PROOF_UPLOADED&outcome=SUCCESS&targetType=ORDER&targetId=$RUN&tenantId=$T"
expect "filtered result"                     "$BODY" '"totalElements":1'
check "correlation timeline"                 200 "$ORG" GET "/api/audit/correlations/$C"
expect "timeline holds ORDER_CONFIRMED"      "$BODY" '"ORDER_CONFIRMED"'
check "get one event"                        200 "$STUDENT" GET "/api/audit/events/$ID1"

echo; echo "== 403 unauthorized"
check "student cannot write"                 403 "$STUDENT" POST /api/audit/events "$(event_json "$(uuid)" USER "$S" ORDER_CREATED)"
check "organizer cannot write"               403 "$ORG" POST /api/audit/events "$(event_json "$(uuid)" USER "$S" ORDER_CREATED)"
check "service cannot read"                  403 "$SERVICE" GET /api/audit/events
check "organizer asks for other tenant"      403 "$ORG" GET "/api/audit/events?tenantId=$T_OTHER"
check "student asks for another actor"       403 "$STUDENT" GET "/api/audit/events?actorId=$S_OTHER"
check "organizer token without tenantId"     403 "$ORG_NO_TENANT" GET /api/audit/events

echo; echo "== 404 absent record"
check "unknown event id"                     404 "$ADMIN" GET /api/audit/events/999999999
check "other tenant's event (hidden)"        404 "$ORG_OTHER" GET "/api/audit/events/$ID1"
check "other student's event (hidden)"       404 "$STUDENT_OTHER" GET "/api/audit/events/$ID1"
check "SYSTEM event hidden from student"     404 "$STUDENT" GET "/api/audit/events/$ID2"
check "unknown correlation"                  404 "$ADMIN" GET "/api/audit/correlations/none-$RUN"

echo; echo "== 405 append-only"
check "PUT is not allowed"                   405 "$ADMIN" PUT "/api/audit/events/$ID1" "$(event_json "$E1" USER "$S" ORDER_CREATED)"
check "DELETE is not allowed"                405 "$ADMIN" DELETE "/api/audit/events/$ID1"

if [ "$SKIP_FRAUD_FLOW" != 1 ]; then
  echo; echo "== Cross-service flow: fraud-risk-service -> RabbitMQ -> audit-log-service"
  FRAUD_ORG=$(mint 3 ORGANIZER "$T")
  REF="AUD-$RUN"
  check "fraud: first use of reference"      201 "$SERVICE" POST /api/fraud/checks \
    "{\"tenantId\":$T,\"eventId\":5,\"orderId\":$((RUN * 10 + 1)),\"buyerId\":$S,\"paymentReference\":\"$REF\",\"amount\":100}"
  check "fraud: reused reference -> flag"    201 "$SERVICE" POST /api/fraud/checks \
    "{\"tenantId\":$T,\"eventId\":5,\"orderId\":$((RUN * 10 + 2)),\"buyerId\":$S_OTHER,\"paymentReference\":\"$REF\",\"amount\":100}"
  FLAG=$(json_num flagId)
  check "fraud: organizer confirms fraud"    200 "$FRAUD_ORG" PATCH "/api/fraud/flags/$FLAG/review" '{"status":"CONFIRMED_FRAUD","note":"smoke test"}'
  for _ in $(seq 1 20); do
    BODY=$(curl -s -H "Authorization: Bearer $ORG" "$GATEWAY_URL/api/audit/correlations/fraud-flag-$FLAG")
    [[ "$BODY" == *FRAUD_FLAG_REVIEWED* ]] && break; sleep 0.5
  done
  expect "audit recorded FRAUD_FLAG_RAISED"   "$BODY" '"action":"FRAUD_FLAG_RAISED"'
  expect "audit recorded FRAUD_FLAG_REVIEWED" "$BODY" '"action":"FRAUD_FLAG_REVIEWED"'
  expect "reviewer is the actor"              "$BODY" '"actorId":3'
  check "other tenant cannot see fraud trail" 404 "$ORG_OTHER" GET "/api/audit/correlations/fraud-flag-$FLAG"
fi

echo
echo "RESULT: $PASS passed, $FAIL failed   (evidence: $LOG)"
[ "$FAIL" -eq 0 ]
