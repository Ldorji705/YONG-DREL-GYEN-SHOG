# Fraud & Risk Service

Sprint 3 · Owner: Nidup Dorji · Port **8090** · Database **`yongdrel_fraud`** · Gateway path **`/api/fraud/**`**

This service checks every payment-proof submission for duplicate transactions and suspicious activity, using rules you can configure. When a submission looks risky, it raises a **flag**, which the organizer then reviews manually. Each flag is also published on RabbitMQ so other services can react, for example Notification and Payment Verification.

```
Payment Proof / Order flow ──POST /api/fraud/checks──► Fraud & Risk ──► MySQL (yongdrel_fraud)
                                                          │
                                         (optional) GET /api/orders/{id} ──► Order Service
                                                          │
                                   fraud.flag.raised / fraud.flag.reviewed ──► RabbitMQ
Organizer dashboard ──GET /api/fraud/flags, PATCH …/review──┘
```

## Hexagonal structure

```
com.yongdrel.fraudriskservice
├── domain                     pure Java, no Spring
│   ├── model                  RiskRule, RiskCheck, RiskFlag, TransactionDetails, enums, Actor
│   ├── service                RiskEvaluator, ScoringPolicy, EffectiveRules, RiskHistory (port)
│   └── exception              InvalidRuleException, InvalidFlagTransitionException
├── application
│   ├── port/in                AssessTransactionUseCase, RiskFlagUseCase, RiskRuleUseCase
│   ├── port/out               RiskCheck/RiskFlag/RiskRule repository ports, FlagEventPublisherPort, OrderReferencePort
│   └── service                RiskAssessmentService, FlagReviewService, RiskRuleService, AccessPolicy
├── adapter
│   ├── in/web                 REST controllers, DTOs, GlobalExceptionHandler, JwtActorMapper
│   └── out
│       ├── persistence        JPA entities + Spring Data repositories (MySQL)
│       ├── messaging          RabbitMQ publisher (after-commit)
│       └── order              Order Service REST client (cross-service reference check)
└── config                     Security (JWT), JWT decoder, domain wiring, properties
```

The schema is owned by Flyway: see [V1__create_fraud_schema.sql](src/main/resources/db/migration/V1__create_fraud_schema.sql) and [V2__seed_default_rules.sql](src/main/resources/db/migration/V2__seed_default_rules.sql). Hibernate never changes the schema (`ddl-auto=none`).

## Risk rules

Each enabled rule that fires adds its **weight** to the score, which is capped at 100.

| Rule | Fires when | Default threshold / window / weight |
|---|---|---|
| `DUPLICATE_PAYMENT_REFERENCE` | the same bank/UPI reference was used on ≥ *threshold* **other** orders of the tenant. References are compared case-insensitively, ignoring spaces and hyphens. | 1 / all time / 70 |
| `DUPLICATE_PROOF_IMAGE` | the same screenshot SHA-256 was used on ≥ *threshold* other orders | 1 / all time / 70 |
| `BUYER_VELOCITY` | the buyer made ≥ *threshold* submissions within *window* minutes | 5 / 10 min / 25 |
| `AMOUNT_MISMATCH` | the declared amount differs from `expectedAmount` by more than *threshold* % | 0 % / – / 40 |
| `PRIOR_CONFIRMED_FRAUD` | the buyer already has ≥ *threshold* flags confirmed as fraud | 1 / all time / 50 |

A window of `0` means all history. **Score → level:** 0 = `NONE`, 1–29 = `LOW`, 30–69 = `MEDIUM`, 70+ = `HIGH`. A flag is raised (`decision = REVIEW_REQUIRED`) when the score is ≥ `app.fraud.flag-threshold` (30). You can change all three numbers centrally in `config-server/config-repo/fraud-risk-service.properties`.

The rules stored with no tenant (`tenantId = null`) are the platform defaults. An organizer can **override** any rule type for their own tenant. A tenant rule replaces the default of the same type, and deleting it reverts to the default.

**Flag lifecycle:** `OPEN → UNDER_REVIEW → CONFIRMED_FRAUD | DISMISSED`. An `OPEN` flag can also go straight to either closed state. Closed flags are final (409), and closing a flag requires a note.

## Authentication & authorization

Every `/api/fraud/**` call needs an RS256 JWT from the Auth Service. The service checks the signature, expiry, `iss=yongdrel-auth`, `aud=yongdrel-api` and a **numeric `sub`** (the user id); any failure is a 401.

| Claim | Meaning |
|---|---|
| `sub` | numeric user id |
| `role` | `STUDENT` (Auth's current `USER` is treated as `STUDENT`), `ORGANIZER`, `ADMIN`, `SERVICE` |
| `tenantId` | **required for organizers**: the tenant they manage |

| Endpoint | STUDENT | ORGANIZER | SERVICE | ADMIN |
|---|---|---|---|---|
| `POST /checks` | own orders only (`buyerId == sub`); outcome only | own tenant | ✔ | ✔ |
| `GET /checks/{id}` | 403 | own tenant (else 404) | ✔ | ✔ |
| `GET /flags`, `GET /flags/{id}` | 403 | own tenant (forced filter; other tenant's id → 404) | ✔ read-only | ✔ |
| `PATCH /flags/{id}/review` | 403 | own tenant | 403 | ✔ |
| `GET /rules`, `/rules/effective`, `/rules/{id}` | 403 | defaults + own tenant | 403 | ✔ |
| `POST/PUT/DELETE /rules` | 403 | own tenant only, never defaults | 403 | ✔ |

> **For the Auth/Tenant owners:** organizer tokens must carry a `tenantId` claim, and service-to-service callers need a token with `role=SERVICE`. Until then, organizer calls return 403 with `"Organizer token has no tenantId claim"`.

## REST API

Base URL through the gateway: `http://localhost:8080/api/fraud`. Directly: `http://localhost:8090/api/fraud`. Ready-to-run requests are in [api-examples.http](api-examples.http).

### `POST /checks`: assess a payment submission

```json
{
  "tenantId": 10, "eventId": 5, "orderId": 5002, "buyerId": 202,
  "paymentReference": "UPI-777-888",
  "proofImageHash": "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
  "amount": 80.00, "expectedAmount": 100.00
}
```

`paymentReference` (4–64 chars) **or** `proofImageHash` (SHA-256 hex) is required. `expectedAmount` enables the amount check.

`201 Created` for a new check. `200 OK` if the identical submission was already checked (idempotent replay):

```json
{
  "id": 2, "tenantId": 10, "eventId": 5, "orderId": 5002, "buyerId": 202,
  "paymentReference": "UPI777888", "amount": 80, "expectedAmount": 100,
  "score": 100, "riskLevel": "HIGH", "decision": "REVIEW_REQUIRED",
  "findings": [
    {"ruleType": "DUPLICATE_PAYMENT_REFERENCE", "weight": 70, "detail": "Payment reference already used on 1 other order(s)"},
    {"ruleType": "AMOUNT_MISMATCH", "weight": 40, "detail": "Declared amount 80 differs from expected 100 by 20.00%"}
  ],
  "flagId": 1, "createdAt": "2026-11-14T09:07:38.207801Z"
}
```

Students receive only `{"id": 1, "orderId": 5001, "decision": "CLEAR"}`, so the rules can't be probed.

### Other endpoints

| Method & path | Purpose | Success |
|---|---|---|
| `GET /checks/{id}` | one stored check | 200 |
| `GET /flags?tenantId&eventId&orderId&buyerId&status&riskLevel&page=0&size=20` | review queue, newest first (size 1–100) | 200 page `{content,page,size,totalElements,totalPages}` |
| `GET /flags/{id}` | one flag | 200 |
| `PATCH /flags/{id}/review` `{"status":"CONFIRMED_FRAUD","note":"…"}` | manual review | 200 |
| `GET /rules[?tenantId=]` | defaults, or one tenant's overrides | 200 |
| `GET /rules/effective[?tenantId=]` | rules actually applied (defaults + overrides) | 200 |
| `GET /rules/{id}` | one rule | 200 |
| `POST /rules` `{"tenantId":10,"ruleType":"BUYER_VELOCITY","enabled":true,"threshold":2,"windowMinutes":30,"weight":30}` | create an override (omit `tenantId` for a default, admin only) | 201 + `Location` |
| `PUT /rules/{id}` `{"enabled":false,"threshold":2,"windowMinutes":30,"weight":30}` | update settings (type and tenant are fixed) | 200 |
| `DELETE /rules/{id}` | delete (tenant override → falls back to default) | 204 |
| `GET /actuator/health` | health, no token needed | 200 `{"status":"UP"}` |

### Errors

Every error has the same body:

```json
{"timestamp":"…","status":400,"error":"Bad Request","message":"Request validation failed","path":"/api/fraud/checks",
 "fieldErrors":[{"field":"tenantId","message":"must be greater than 0"}]}
```

| Status | When |
|---|---|
| 400 | validation failure, malformed JSON, unknown enum, bad path/query parameter, invalid rule settings |
| 401 | missing, expired, tampered or wrong-issuer/audience token (`WWW-Authenticate: Bearer`) |
| 403 | role not allowed, wrong tenant, student acting for another buyer, organizer token without `tenantId` |
| 404 | unknown id, or a check/flag that belongs to another tenant |
| 405 / 415 | wrong HTTP method / non-JSON body |
| 409 | duplicate rule, invalid flag transition, concurrent update (optimistic lock) |
| 422 | cross-service reference failed: order doesn't exist, or its tenant/event/buyer doesn't match |
| 503 | Order Service unreachable or timed out (connect 2 s / read 3 s) |

## Cross-service reference check

When `ORDER_SERVICE_CHECK_ENABLED=true`, every new check calls `GET {ORDER_SERVICE_URL}/api/orders/{orderId}`, forwarding the caller's bearer token. The service expects JSON with `id`, `tenantId`, `eventId` and `buyerId`; unknown fields are ignored. A 404 from the Order Service becomes 422 here, and a 5xx or timeout becomes 503. The check is **off by default** until the Order Service is deployed.

## Events (RabbitMQ)

Events go to topic exchange `yongdrel.fraud.events`, with routing keys `fraud.flag.raised` and `fraud.flag.reviewed`. They are published only **after** the database commit. The durable queue `fraud.flags.review` is bound to `fraud.flag.#`, so events wait there until a consumer reads them.

```json
{"eventType":"FLAG_RAISED","flagId":1,"checkId":2,"tenantId":10,"eventId":5,"orderId":5002,"buyerId":202,
 "score":100,"riskLevel":"HIGH","reasons":["DUPLICATE_PAYMENT_REFERENCE","AMOUNT_MISMATCH"],
 "status":"OPEN","reviewerId":null,"occurredAt":"…"}
```

## Running

**Docker Compose** (from `infrastructure/`; needs `.env` and `keys/public.pem` as for the other services):

```bash
cd services/fraud-risk-service && ./mvnw -DskipTests package && cd ../../infrastructure
docker compose up -d --build mysql rabbitmq config-server fraud-risk-service api-gateway
curl http://localhost:8090/actuator/health        # {"status":"UP",...}
```

On a **fresh** MySQL volume, `infrastructure/mysql-init/10-fraud-risk-service.sql` creates `yongdrel_fraud` and grants it to `yongdrel`. If your volume already exists, run the script once by hand:

```bash
docker exec -i yongdrel-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < mysql-init/10-fraud-risk-service.sql
```

**Locally** (MySQL on 3307, RabbitMQ on 5672 from compose):

```bash
DB_PASSWORD=... RABBITMQ_USER=... RABBITMQ_PASSWORD=... JWT_PUBLIC_KEY_PATH=../../infrastructure/keys/public.pem ./mvnw spring-boot:run
```

## Testing

```bash
./mvnw verify      # unit + API + Testcontainers integration tests, JaCoCo report and coverage gate
open target/site/jacoco/index.html
```

* The Testcontainers tests (MySQL 8.4 + RabbitMQ) need Docker and are skipped automatically without it.
* `src/test/resources/docker-java.properties` pins Docker API 1.44, because Docker Engine 29+ rejects the old Testcontainers default.
* The build fails if `domain.*` + `application.service` drop below 80 % line or 70 % branch coverage.

**Authenticated curl test through the gateway.** It mints dev tokens with the Auth Service's dev private key, runs 56 positive and negative checks, and saves a log to `docs/evidence/`:

```bash
JWT_PRIVATE_KEY=../../infrastructure/keys/private.pem ./scripts/api-smoke-test.sh
```

* Full API and database contract: [docs/API-CONTRACT.md](docs/API-CONTRACT.md).
* Results per Jira subtask: [docs/TEST-EVIDENCE.md](docs/TEST-EVIDENCE.md).
