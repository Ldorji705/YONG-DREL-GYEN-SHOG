#!/usr/bin/env bash
# Authenticated end-to-end API test of the Reporting Service through the API Gateway.
#
# Usage:
#   JWT_PRIVATE_KEY=../../infrastructure/keys/private.pem ./scripts/api-smoke-test.sh
#
# Env:
#   JWT_PRIVATE_KEY   RSA private key of the Auth Service (dev keys only), used to mint test tokens
#   GATEWAY_URL       default http://localhost:8080
#   SERVICE_URL       default http://localhost:8092    (health check)
#   RABBIT_MGMT_URL   default http://localhost:15672   (RabbitMQ management API, to publish feed messages)
#   RABBITMQ_USER / RABBITMQ_PASSWORD   broker credentials (from infrastructure/.env); feed test skipped if unset
#   EVIDENCE_DIR      default docs/evidence
set -uo pipefail

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
SERVICE_URL=${SERVICE_URL:-http://localhost:8092}
RABBIT_MGMT_URL=${RABBIT_MGMT_URL:-http://localhost:15672}
EVIDENCE_DIR=${EVIDENCE_DIR:-docs/evidence}
: "${JWT_PRIVATE_KEY:?set JWT_PRIVATE_KEY to the Auth Service dev private key (PEM)}"

mkdir -p "$EVIDENCE_DIR"
LOG="$EVIDENCE_DIR/api-smoke-test-$(date +%Y%m%d-%H%M%S).txt"
exec > >(tee "$LOG") 2>&1

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
mint() {  # mint <sub> <role> [tenantId] [iss] [aud] [expOffsetSeconds]
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

PASS=0; FAIL=0; BODY=""
check() {  # check <name> <expectedStatus> <token|-> <method> <path> [json]
  local name=$1 expected=$2 token=$3 method=$4 path=$5 data=${6:-}
  local args=(-s -o /tmp/report-smoke.$$ -w '%{http_code}' -X "$method" "$GATEWAY_URL$path")
  [ "$token" != "-" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  local status; status=$(curl "${args[@]}")
  BODY=$(cat /tmp/report-smoke.$$ 2>/dev/null); rm -f /tmp/report-smoke.$$
  if [ "$status" = "$expected" ]; then
    PASS=$((PASS + 1)); printf 'PASS  %-56s %s %s -> %s\n' "$name" "$method" "$path" "$status"
  else
    FAIL=$((FAIL + 1)); printf 'FAIL  %-56s %s %s -> %s (expected %s)\n      body: %s\n' \
      "$name" "$method" "$path" "$status" "$expected" "$BODY"
  fi
}
expect() {  # expect <name> <needle>   (in last BODY)
  if [[ "$BODY" == *"$2"* ]]; then PASS=$((PASS + 1)); printf 'PASS  %-56s contains %s\n' "$1" "$2"
  else FAIL=$((FAIL + 1)); printf 'FAIL  %-56s missing %s\n      got: %s\n' "$1" "$2" "$BODY"; fi
}
json_num() { printf '%s' "$BODY" | grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }

RUN=$(date +%s)
T=$((RUN % 100000 + 2000)); T2=$((T + 1))
EV=$((RUN * 10 + 1)); EV2=$((RUN * 10 + 2)); EV3=$((RUN * 10 + 3))
O=$((RUN * 100)); PZ=$((RUN * 10 + 7)); PO=$((RUN * 10 + 9))
B=$((RUN % 100000 + 7000))
SERVICE=$(mint 2 SERVICE); ADMIN=$(mint 1 ADMIN)
ORG=$(mint 3 ORGANIZER "$T"); COLLEAGUE=$(mint 6 ORGANIZER "$T"); ORG2=$(mint 4 ORGANIZER "$T2")
ORG_NO_TENANT=$(mint 5 ORGANIZER); STUDENT=$(mint "$B" USER)

event() { printf '{"tenantId":%s,"name":"%s","ticketPrice":50,"totalTickets":100,"status":"%s","sourceUpdatedAt":"%s"}' "$1" "$2" "$3" "$4"; }
order() {  # order <buyer> <qty> <status> <updatedAt> [tenant] [event]
  local conf=""; [ "$3" = CONFIRMED ] && conf="\"confirmedAt\":\"2026-11-02T10:00:00Z\","
  printf '{"eventId":%s,"tenantId":%s,"buyerId":%s,"quantity":%s,"amount":%s,"status":"%s","createdAt":"2026-11-02T09:00:00Z",%s"sourceUpdatedAt":"%s"}' \
    "${6:-$EV}" "${5:-$T}" "$1" "$2" "$(( $2 * 50 ))" "$3" "$conf" "$4"
}

echo "Reporting Service API smoke test  gateway=$GATEWAY_URL  run=$RUN  tenant=$T  event=$EV  $(date -u +%FT%TZ)"
echo

echo "== Health"
code=$(curl -s -o /dev/null -w '%{http_code}' "$SERVICE_URL/actuator/health")
if [ "$code" = 200 ]; then PASS=$((PASS + 1)); echo "PASS  health endpoint -> 200 $(curl -s "$SERVICE_URL/actuator/health")"
else FAIL=$((FAIL + 1)); echo "FAIL  health endpoint -> $code"; fi

echo; echo "== 401 invalid JWT"
check "no token"              401 -  GET /api/reports/overview
check "garbage token"         401 "x.y.z" GET /api/reports/overview
check "tampered signature"    401 "${ORG%?}x" GET /api/reports/overview
check "expired token"         401 "$(mint 3 ORGANIZER "$T" yongdrel-auth yongdrel-api -60)" GET /api/reports/overview
check "wrong issuer"          401 "$(mint 3 ORGANIZER "$T" evil-issuer)" GET /api/reports/overview
check "wrong audience"        401 "$(mint 3 ORGANIZER "$T" yongdrel-auth other-api)" GET /api/reports/overview
check "non-numeric subject"   401 "$(mint alice ORGANIZER "$T")" GET /api/reports/overview

echo; echo "== Feed the read model (REST ingest by a service account)"
check "event created"                 201 "$SERVICE" PUT "/api/reports/ingest/events/$EV"  "$(event "$T" "Losar Raffle $RUN" OPEN 2026-11-01T08:00:00Z)"
check "identical repeat"              200 "$SERVICE" PUT "/api/reports/ingest/events/$EV"  "$(event "$T" "Losar Raffle $RUN" OPEN 2026-11-01T08:00:00Z)"
expect "outcome UNCHANGED"            '"outcome":"UNCHANGED"'
check "other tenant's event"          201 "$SERVICE" PUT "/api/reports/ingest/events/$EV2" "$(event "$T2" "Other $RUN" OPEN 2026-11-01T08:00:00Z)"
check "order 1 awaiting verification" 201 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 1))" "$(order "$B" 2 AWAITING_VERIFICATION 2026-11-02T09:05:00Z)"
check "order 1 confirmed (newer)"     200 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 1))" "$(order "$B" 2 CONFIRMED 2026-11-02T10:00:00Z)"
expect "outcome UPDATED"              '"outcome":"UPDATED"'
check "order 2 confirmed"             201 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 2))" "$(order $((B + 1)) 1 CONFIRMED 2026-11-02T10:00:00Z)"
check "order 3 awaiting payment"      201 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 3))" "$(order $((B + 2)) 1 AWAITING_PAYMENT 2026-11-02T09:00:00Z)"
check "order 4 failed"                201 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 4))" "$(order $((B + 3)) 3 FAILED 2026-11-02T09:30:00Z)"
check "prize tier"                    201 "$SERVICE" PUT "/api/reports/ingest/prizes/$PZ" \
  "{\"eventId\":$EV,\"tenantId\":$T,\"tier\":1,\"name\":\"Gold coin\",\"quantity\":1,\"unitValue\":60,\"sourceUpdatedAt\":\"2026-11-01T08:00:00Z\"}"
check "payout pending"                201 "$SERVICE" PUT "/api/reports/ingest/payouts/$PO" \
  "{\"eventId\":$EV,\"tenantId\":$T,\"prizeId\":$PZ,\"winnerId\":$B,\"amount\":60,\"status\":\"PENDING\",\"sourceUpdatedAt\":\"2026-11-03T08:00:00Z\"}"

echo; echo "== 409 duplicate / conflicting updates"
check "older order version"           409 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 1))" "$(order "$B" 2 AWAITING_VERIFICATION 2026-11-02T09:05:00Z)"
check "same version, other content"   409 "$SERVICE" PUT "/api/reports/ingest/events/$EV" "$(event "$T" "Renamed" OPEN 2026-11-01T08:00:00Z)"
check "order moved to another buyer"  409 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 1))" "$(order 999 2 CONFIRMED 2026-11-02T11:00:00Z)"
check "event moved to another tenant" 409 "$SERVICE" PUT "/api/reports/ingest/events/$EV" "$(event "$T2" "Losar Raffle $RUN" OPEN 2026-11-01T09:00:00Z)"

echo; echo "== 422 invalid cross-service references"
check "order for unknown event"       422 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 9))" "$(order "$B" 1 AWAITING_PAYMENT 2026-11-02T09:00:00Z "$T" "$EV3")"
check "order tenant != event tenant"  422 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 9))" "$(order "$B" 1 AWAITING_PAYMENT 2026-11-02T09:00:00Z "$T2" "$EV")"
check "payout for unknown prize"      422 "$SERVICE" PUT "/api/reports/ingest/payouts/$((PO + 1))" \
  "{\"eventId\":$EV,\"tenantId\":$T,\"prizeId\":$((PZ + 5)),\"winnerId\":$B,\"amount\":60,\"status\":\"PENDING\",\"sourceUpdatedAt\":\"2026-11-03T08:00:00Z\"}"

if [ -n "${RABBITMQ_USER:-}" ]; then
  echo; echo "== RabbitMQ feed (yongdrel.reporting.feed via management API)"
  payload="{\"id\":$PO,\"eventId\":$EV,\"tenantId\":$T,\"prizeId\":$PZ,\"winnerId\":$B,\"amount\":60,\"status\":\"PAID\",\"paidAt\":\"2026-11-04T08:00:00Z\",\"sourceUpdatedAt\":\"2026-11-04T08:00:00Z\"}"
  esc=$(printf '%s' "$payload" | sed 's/"/\\"/g')
  BODY=$(curl -s -u "$RABBITMQ_USER:${RABBITMQ_PASSWORD:-}" -H 'Content-Type: application/json' \
    -X POST "$RABBIT_MGMT_URL/api/exchanges/%2F/yongdrel.reporting.feed/publish" \
    --data "{\"properties\":{\"content_type\":\"application/json\"},\"routing_key\":\"payout.upserted\",\"payload\":\"$esc\",\"payload_encoding\":\"string\"}")
  expect "payout PAID published and routed" '"routed":true'
  for _ in $(seq 1 20); do
    BODY=$(curl -s -H "Authorization: Bearer $ORG" "$GATEWAY_URL/api/reports/payouts?eventId=$EV")
    [[ "$BODY" == *'"paidCount":1'* ]] && break; sleep 0.5
  done
  expect "payout report shows it paid"   '"paidCount":1'
fi

echo; echo "== Sales, ticket, prize and payout summaries (organizer, own tenant)"
check "event summary"                 200 "$ORG" GET "/api/reports/events/$EV/summary"
expect "revenue 150.00 (3 tickets)"   '"revenue":150.00'
expect "tickets sold 3"               '"sold":3'
expect "1 reserved (pending)"         '"reserved":1'
expect "available 96"                 '"available":96'
expect "net after prizes 90.00"       '"netAfterPrizes":90.00'
check "sales report"                  200 "$ORG" GET "/api/reports/sales?eventId=$EV"
expect "2 confirmed, 1 pending, 1 failed" '"confirmedOrders":2'
expect "conversion 66.67%"            '"conversionRatePercent":66.67'
check "sales with period"             200 "$ORG" GET "/api/reports/sales?eventId=$EV&from=2026-11-02T00:00:00Z&to=2026-11-03T00:00:00Z"
expect "daily breakdown"              '"date":"2026-11-02"'
check "ticket report"                 200 "$ORG" GET "/api/reports/tickets?eventId=$EV"
check "prize report"                  200 "$ORG" GET "/api/reports/prizes?eventId=$EV"
expect "prize tier listed"            '"Gold coin"'
check "payout report"                 200 "$ORG" GET "/api/reports/payouts?eventId=$EV"
check "tenant overview"               200 "$ORG" GET /api/reports/overview
expect "overview scoped to tenant"    "\"tenantId\":$T"
check "events list"                   200 "$ORG" GET "/api/reports/events?status=OPEN"
expect "only own event listed"        "\"totalElements\":1"
check "admin views tenant"            200 "$ADMIN" GET "/api/reports/overview?tenantId=$T"
check "student's own purchases"       200 "$STUDENT" GET /api/reports/me/purchases
expect "student sees 2 tickets"       '"confirmedTickets":2'

echo; echo "== Tenant isolation"
check "other organizer overview"      200 "$ORG2" GET /api/reports/overview
expect "other tenant sees no revenue" '"revenue":0.00'
check "other organizer, foreign event" 404 "$ORG2" GET "/api/reports/events/$EV/summary"
check "other organizer, sales of it"  404 "$ORG2" GET "/api/reports/sales?eventId=$EV"
check "other organizer asks tenant T" 403 "$ORG2" GET "/api/reports/overview?tenantId=$T"

echo; echo "== Saved reports (CRUD, 409, ownership)"
check "create saved report"           201 "$ORG" POST /api/reports/saved "{\"name\":\"Losar sales $RUN\",\"reportType\":\"SALES\",\"eventId\":$EV}"
SR=$(json_num id)
check "duplicate name (any case)"     409 "$COLLEAGUE" POST /api/reports/saved "{\"name\":\"LOSAR SALES $RUN\",\"reportType\":\"TICKETS\"}"
check "list"                          200 "$COLLEAGUE" GET /api/reports/saved
check "colleague can read"            200 "$COLLEAGUE" GET "/api/reports/saved/$SR"
check "colleague cannot edit"         403 "$COLLEAGUE" PUT "/api/reports/saved/$SR" "{\"name\":\"Mine now\",\"reportType\":\"SALES\"}"
check "owner updates"                 200 "$ORG" PUT "/api/reports/saved/$SR" "{\"name\":\"Losar tickets $RUN\",\"reportType\":\"TICKETS\",\"eventId\":$EV}"
check "run it"                        200 "$ORG" GET "/api/reports/saved/$SR/run"
expect "runs the ticket report"       '"sold":3'
check "other tenant cannot see it"    404 "$ORG2" GET "/api/reports/saved/$SR"
check "saved report on foreign event" 404 "$ORG2" POST /api/reports/saved "{\"name\":\"Spy $RUN\",\"reportType\":\"SALES\",\"eventId\":$EV}"
check "owner deletes"                 204 "$ORG" DELETE "/api/reports/saved/$SR"
check "deleted is gone"               404 "$ORG" GET "/api/reports/saved/$SR"

echo; echo "== 400 bad input"
check "ingest validation errors"      400 "$SERVICE" PUT "/api/reports/ingest/events/$EV" '{"tenantId":0,"name":"","ticketPrice":-1,"totalTickets":0}'
expect "fieldErrors returned"         '"fieldErrors"'
check "confirmed order w/o confirmedAt" 400 "$SERVICE" PUT "/api/reports/ingest/orders/$((O + 8))" \
  "{\"eventId\":$EV,\"tenantId\":$T,\"buyerId\":$B,\"quantity\":1,\"amount\":50,\"status\":\"CONFIRMED\",\"createdAt\":\"2026-11-02T09:00:00Z\",\"sourceUpdatedAt\":\"2026-11-02T09:00:00Z\"}"
check "body id != path id"            400 "$SERVICE" PUT "/api/reports/ingest/events/$EV" "{\"id\":1,$(event "$T" x OPEN 2026-11-05T00:00:00Z | cut -c2-)"
check "unknown enum"                  400 "$SERVICE" PUT "/api/reports/ingest/events/$EV" '{"status":"FINISHED"}'
check "malformed JSON"                400 "$SERVICE" PUT "/api/reports/ingest/events/$EV" '{"tenantId":'
check "reversed period"               400 "$ORG" GET "/api/reports/sales?from=2026-12-01T00:00:00Z&to=2026-11-01T00:00:00Z"
check "period over 366 days"          400 "$ORG" GET "/api/reports/sales?from=2025-01-01T00:00:00Z&to=2026-11-01T00:00:00Z"
check "bad date"                      400 "$ORG" GET "/api/reports/sales?from=yesterday"
check "page size 500"                 400 "$ORG" GET "/api/reports/events?size=500"
check "non-numeric event id"          400 "$ORG" GET /api/reports/events/abc/summary
check "EVENT_SUMMARY without event"   400 "$ORG" POST /api/reports/saved '{"name":"x","reportType":"EVENT_SUMMARY"}'

echo; echo "== 403 unauthorized"
check "organizer cannot ingest"       403 "$ORG" PUT "/api/reports/ingest/events/$EV" "$(event "$T" x OPEN 2026-11-06T00:00:00Z)"
check "student cannot ingest"         403 "$STUDENT" PUT "/api/reports/ingest/events/$EV" "$(event "$T" x OPEN 2026-11-06T00:00:00Z)"
check "student cannot read reports"   403 "$STUDENT" GET /api/reports/overview
check "service cannot read reports"   403 "$SERVICE" GET /api/reports/overview
check "organizer without tenantId"    403 "$ORG_NO_TENANT" GET /api/reports/overview
check "admin has no saved reports"    403 "$ADMIN" GET /api/reports/saved
check "organizer has no purchases"    403 "$ORG" GET /api/reports/me/purchases

echo; echo "== 404 absent record"
check "unknown event summary"         404 "$ADMIN" GET /api/reports/events/999999999999/summary
check "unknown event in sales"        404 "$ORG" GET "/api/reports/sales?eventId=999999999999"
check "unknown saved report"          404 "$ORG" GET /api/reports/saved/999999999

echo
echo "RESULT: $PASS passed, $FAIL failed   (evidence: $LOG)"
[ "$FAIL" -eq 0 ]
