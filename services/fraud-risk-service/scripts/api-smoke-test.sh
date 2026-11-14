#!/usr/bin/env bash
# Authenticated end-to-end API test of the Fraud & Risk Service through the API Gateway.
#
# Usage:
#   JWT_PRIVATE_KEY=../../infrastructure/keys/private.pem ./scripts/api-smoke-test.sh
#
# Env:
#   JWT_PRIVATE_KEY  RSA private key of the Auth Service (dev keys only). Used to mint test tokens
#                    with iss=yongdrel-auth, aud=yongdrel-api, numeric sub, role and tenantId claims.
#   GATEWAY_URL      default http://localhost:8080
#   SERVICE_URL      default http://localhost:8090   (only for the health check)
#   EVIDENCE_DIR     default docs/evidence
#
# Exit code is non-zero if any check fails. Output is also saved as evidence.
set -uo pipefail

GATEWAY_URL=${GATEWAY_URL:-http://localhost:8080}
SERVICE_URL=${SERVICE_URL:-http://localhost:8090}
EVIDENCE_DIR=${EVIDENCE_DIR:-docs/evidence}
: "${JWT_PRIVATE_KEY:?set JWT_PRIVATE_KEY to the Auth Service dev private key (PEM)}"

mkdir -p "$EVIDENCE_DIR"
LOG="$EVIDENCE_DIR/api-smoke-test-$(date +%Y%m%d-%H%M%S).txt"
exec > >(tee "$LOG") 2>&1

# ---------- JWT minting (RS256) ----------
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
# mint <sub> <role> [tenantId] [iss] [aud] [expOffsetSeconds] [key]
mint() {
  local sub=$1 role=$2 tenant=${3:-} iss=${4:-yongdrel-auth} aud=${5:-yongdrel-api} off=${6:-900} key=${7:-$JWT_PRIVATE_KEY}
  local now; now=$(date +%s)
  local t=""; [ -n "$tenant" ] && t=",\"tenantId\":$tenant"
  local h p s
  h=$(printf '{"alg":"RS256","typ":"JWT"}' | b64url)
  p=$(printf '{"iss":"%s","sub":"%s","aud":["%s"],"iat":%d,"exp":%d,"role":"%s"%s}' \
        "$iss" "$sub" "$aud" "$now" "$((now + off))" "$role" "$t" | b64url)
  s=$(printf '%s.%s' "$h" "$p" | openssl dgst -sha256 -sign "$key" | b64url)
  printf '%s.%s.%s' "$h" "$p" "$s"
}

SERVICE=$(mint 2 SERVICE)
ADMIN=$(mint 1 ADMIN)
ORG=$(mint 3 ORGANIZER 10)
ORG_OTHER=$(mint 4 ORGANIZER 20)
ORG_NO_TENANT=$(mint 5 ORGANIZER)
STUDENT=$(mint 201 USER)

# ---------- helpers ----------
PASS=0; FAIL=0; BODY=""
# check <name> <expectedStatus> <token|-> <method> <path> [json]
check() {
  local name=$1 expected=$2 token=$3 method=$4 path=$5 data=${6:-}
  local args=(-s -o /tmp/fraud-smoke-body.$$ -w '%{http_code}' -X "$method" "$GATEWAY_URL$path")
  [ "$token" != "-" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H 'Content-Type: application/json' --data "$data")
  local status; status=$(curl "${args[@]}")
  BODY=$(cat /tmp/fraud-smoke-body.$$ 2>/dev/null); rm -f /tmp/fraud-smoke-body.$$
  if [ "$status" = "$expected" ]; then
    PASS=$((PASS + 1)); printf 'PASS  %-62s %s %s -> %s\n' "$name" "$method" "$path" "$status"
  else
    FAIL=$((FAIL + 1)); printf 'FAIL  %-62s %s %s -> %s (expected %s)\n      body: %s\n' \
      "$name" "$method" "$path" "$status" "$expected" "$BODY"
  fi
}
expect_body() {  # expect_body <name> <substring>
  if [[ "$BODY" == *"$2"* ]]; then PASS=$((PASS + 1)); printf 'PASS  %-62s body contains %s\n' "$1" "$2"
  else FAIL=$((FAIL + 1)); printf 'FAIL  %-62s body missing %s\n      body: %s\n' "$1" "$2" "$BODY"; fi
}
json_num() { printf '%s' "$BODY" | grep -o "\"$1\":[0-9]*" | head -1 | cut -d: -f2; }

RUN=$(date +%s)
O1=$((RUN * 10 + 1)); O2=$((RUN * 10 + 2)); O3=$((RUN * 10 + 3)); O4=$((RUN * 10 + 4)); O5=$((RUN * 10 + 5))
B1=$((RUN % 100000 + 1000)); B2=$((B1 + 1))
REF="UPI-$RUN"
HASH=$(printf 'screenshot-%s' "$RUN" | openssl dgst -sha256 | awk '{print $NF}')
STUDENT_B1=$(mint "$B1" USER)

echo "Fraud & Risk Service API smoke test  gateway=$GATEWAY_URL  run=$RUN  $(date -u +%FT%TZ)"
echo

echo "== Health"
code=$(curl -s -o /dev/null -w '%{http_code}' "$SERVICE_URL/actuator/health")
if [ "$code" = 200 ]; then PASS=$((PASS + 1)); echo "PASS  health endpoint -> 200 $(curl -s "$SERVICE_URL/actuator/health")"
else FAIL=$((FAIL + 1)); echo "FAIL  health endpoint -> $code"; fi

echo; echo "== 401 invalid JWT"
check "no token"                         401 -  GET /api/fraud/flags
check "garbage token"                    401 "not.a.jwt" GET /api/fraud/flags
check "tampered signature"               401 "${SERVICE%?}x" GET /api/fraud/flags
check "expired token"                    401 "$(mint 2 SERVICE '' yongdrel-auth yongdrel-api -60)" GET /api/fraud/flags
check "wrong issuer"                     401 "$(mint 2 SERVICE '' evil-issuer)" GET /api/fraud/flags
check "wrong audience"                   401 "$(mint 2 SERVICE '' yongdrel-auth other-api)" GET /api/fraud/flags
check "non-numeric subject"              401 "$(mint alice ADMIN)" GET /api/fraud/flags

echo; echo "== Duplicate payment checks"
check "student submits own proof (clean)" 201 "$STUDENT_B1" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O1,\"buyerId\":$B1,\"paymentReference\":\"$REF\",\"proofImageHash\":\"$HASH\",\"amount\":100,\"expectedAmount\":100}"
expect_body "student sees only the decision"     '"decision":"CLEAR"'
check "identical replay is idempotent"    200 "$STUDENT_B1" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O1,\"buyerId\":$B1,\"paymentReference\":\"$REF\",\"proofImageHash\":\"$HASH\",\"amount\":100,\"expectedAmount\":100}"
check "same reference, other order/buyer" 201 "$SERVICE" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O2,\"buyerId\":$B2,\"paymentReference\":\"$(printf %s "$REF" | tr A-Z a-z)\",\"amount\":100,\"expectedAmount\":100}"
expect_body "duplicate reference raises a flag"  '"DUPLICATE_PAYMENT_REFERENCE"'
expect_body "decision REVIEW_REQUIRED"           '"decision":"REVIEW_REQUIRED"'
FLAG=$(json_num flagId); CHECK=$(json_num id)
check "same screenshot, other order"      201 "$SERVICE" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O3,\"buyerId\":$((B2 + 1)),\"proofImageHash\":\"$HASH\",\"amount\":100}"
expect_body "duplicate screenshot detected"      '"DUPLICATE_PROOF_IMAGE"'

echo; echo "== Suspicious activity rules"
check "amount mismatch (60 vs 100)"       201 "$SERVICE" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O4,\"buyerId\":$((B2 + 2)),\"paymentReference\":\"AMT-$RUN\",\"amount\":60,\"expectedAmount\":100}"
expect_body "amount mismatch flagged"            '"AMOUNT_MISMATCH"'

echo; echo "== Manual review flow"
check "organizer review queue"            200 "$ORG" GET "/api/fraud/flags?status=OPEN&size=100"
expect_body "queue contains the new flag"        "\"id\":$FLAG,"
check "service can read flags of an order" 200 "$SERVICE" GET "/api/fraud/flags?orderId=$O2"
check "start review"                      200 "$ORG" PATCH "/api/fraud/flags/$FLAG/review" '{"status":"UNDER_REVIEW"}'
check "close without note"                400 "$ORG" PATCH "/api/fraud/flags/$FLAG/review" '{"status":"CONFIRMED_FRAUD"}'
check "confirm fraud"                     200 "$ORG" PATCH "/api/fraud/flags/$FLAG/review" '{"status":"CONFIRMED_FRAUD","note":"Reference reused"}'
expect_body "status CONFIRMED_FRAUD, reviewer 3" '"reviewerId":3'
check "review closed flag again"          409 "$ORG" PATCH "/api/fraud/flags/$FLAG/review" '{"status":"DISMISSED","note":"changed mind"}'
check "buyer with confirmed fraud pays again" 201 "$SERVICE" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O5,\"buyerId\":$B2,\"paymentReference\":\"NEW-$RUN\",\"amount\":100}"
expect_body "prior confirmed fraud flagged"      '"PRIOR_CONFIRMED_FRAUD"'

echo; echo "== 400 bad input"
check "validation errors"                 400 "$SERVICE" POST /api/fraud/checks '{"tenantId":0,"eventId":5,"orderId":1,"buyerId":1,"amount":-1}'
expect_body "fieldErrors returned"               '"fieldErrors"'
check "malformed JSON"                    400 "$SERVICE" POST /api/fraud/checks '{"tenantId":'
check "unknown enum in query"             400 "$ORG" GET "/api/fraud/flags?status=MAYBE"
check "page size too large"               400 "$ORG" GET "/api/fraud/flags?size=500"
check "non-numeric id"                    400 "$ADMIN" GET /api/fraud/checks/abc
check "invalid rule settings"             400 "$ORG" POST /api/fraud/rules \
  '{"tenantId":10,"ruleType":"BUYER_VELOCITY","enabled":true,"threshold":0,"windowMinutes":10,"weight":25}'

echo; echo "== 403 unauthorized"
check "student for another buyer"         403 "$STUDENT" POST /api/fraud/checks \
  "{\"tenantId\":10,\"eventId\":5,\"orderId\":$O1,\"buyerId\":999,\"paymentReference\":\"X-$RUN\",\"amount\":100}"
check "student reads flags"               403 "$STUDENT" GET /api/fraud/flags
check "organizer submits for other tenant" 403 "$ORG" POST /api/fraud/checks \
  "{\"tenantId\":20,\"eventId\":5,\"orderId\":$O1,\"buyerId\":1,\"paymentReference\":\"Y-$RUN\",\"amount\":100}"
check "organizer filters other tenant"    403 "$ORG" GET "/api/fraud/flags?tenantId=20"
check "organizer token without tenantId"  403 "$ORG_NO_TENANT" GET /api/fraud/flags
check "service reviews a flag"            403 "$SERVICE" PATCH "/api/fraud/flags/$FLAG/review" '{"status":"UNDER_REVIEW"}'
check "organizer edits platform default"  403 "$ORG" PUT /api/fraud/rules/1 '{"enabled":false,"threshold":1,"windowMinutes":0,"weight":70}'

echo; echo "== 404 absent record"
check "unknown check"                     404 "$ADMIN" GET /api/fraud/checks/999999999
check "unknown flag"                      404 "$ADMIN" GET /api/fraud/flags/999999999
check "flag of another tenant (hidden)"   404 "$ORG_OTHER" GET "/api/fraud/flags/$FLAG"
check "check of another tenant (hidden)"  404 "$ORG_OTHER" GET "/api/fraud/checks/$CHECK"
check "unknown rule"                      404 "$ADMIN" DELETE /api/fraud/rules/999999999

echo; echo "== Rule CRUD and 409 conflict"
TENANT_RULES=$((RUN % 100000 + 500))
ORG_R=$(mint 6 ORGANIZER "$TENANT_RULES")
check "create tenant override"            201 "$ORG_R" POST /api/fraud/rules \
  "{\"tenantId\":$TENANT_RULES,\"ruleType\":\"BUYER_VELOCITY\",\"enabled\":true,\"threshold\":2,\"windowMinutes\":30,\"weight\":30}"
RULE=$(json_num id)
check "duplicate override"                409 "$ORG_R" POST /api/fraud/rules \
  "{\"tenantId\":$TENANT_RULES,\"ruleType\":\"BUYER_VELOCITY\",\"enabled\":true,\"threshold\":2,\"windowMinutes\":30,\"weight\":30}"
check "read rule"                         200 "$ORG_R" GET "/api/fraud/rules/$RULE"
check "effective rules use override"      200 "$ORG_R" GET /api/fraud/rules/effective
expect_body "override is effective"              '"scope":"TENANT"'
check "update rule"                       200 "$ORG_R" PUT "/api/fraud/rules/$RULE" '{"enabled":true,"threshold":3,"windowMinutes":30,"weight":30}'
check "other organizer reads it"          403 "$ORG" GET "/api/fraud/rules/$RULE"
check "delete rule"                       204 "$ORG_R" DELETE "/api/fraud/rules/$RULE"
check "deleted rule is gone"              404 "$ORG_R" GET "/api/fraud/rules/$RULE"

echo
echo "RESULT: $PASS passed, $FAIL failed   (evidence: $LOG)"
[ "$FAIL" -eq 0 ]
