# Audit Log Service

Sprint 3 · Owner: Nidup Dorji · Port **8091** · Database **`yongdrel_audit`** · Gateway path **`/api/audit/**`**

This service keeps an **append-only** record of security-sensitive business actions: who did what, to which entity, when, and as part of which transaction (correlation ID). Searches are **restricted** by role:

- **Admins** see everything.
- **Organizers** see their own tenant only.
- **Students** see only their own actions.
- **Service accounts** can write but cannot read.

```
any service ──POST /api/audit/events (SERVICE/ADMIN JWT)──────────────┐
any service ──RabbitMQ yongdrel.audit.events (audit.#)────────────────┤
fraud-risk-service ──RabbitMQ yongdrel.fraud.events (fraud.flag.#)────┼──► Audit Log ──► MySQL (yongdrel_audit)
                                       (optional) GET /api/tenants/{id} ◄──┤        │
admin / organizer / student ──GET /api/audit/events, /correlations/{id}────────────┘
```

* **Append-only:** there are no update or delete endpoints, so `PUT`, `PATCH` and `DELETE` return 405. The JPA entity is `@Immutable`, and the repository has no save-existing or delete methods.
* **Tamper-evident:** each row stores a SHA-256 `integrityHash` of all its fields. Every response includes `integrityValid`, so a row changed directly in the database shows `false`.
* **Idempotent:** the producer supplies a unique `eventId` (UUID). Re-posting it over REST returns 409. A RabbitMQ redelivery of an already-recorded `eventId` is acknowledged and ignored.

## Hexagonal structure

```
com.yongdrel.auditlogservice
├── domain            AuditEvent (validation + sealing), AuditEventDraft, enums, Caller, IntegrityHasher
├── application
│   ├── port/in       RecordAuditEventUseCase, AuditQueryUseCase
│   ├── port/out      AuditEventRepositoryPort (append/find only), TenantReferencePort, SourceServiceRegistryPort
│   └── service       AuditRecordingService, AuditQueryService, AccessPolicy
├── adapter
│   ├── in/web        AuditEventController, DTOs, GlobalExceptionHandler, JwtCallerMapper
│   ├── in/messaging  AuditEventListener (audit.events + audit.fraud-events, dead-letter queue)
│   ├── out/persistence  JPA entity + repository (MySQL, Flyway)
│   └── out/tenant    Tenant Service REST client (cross-service reference check)
└── config            Security, JWT decoder, known source services, clock
```

The full contract (fields, constraints, status codes, schema, messages) is in [docs/API-CONTRACT.md](docs/API-CONTRACT.md).

## Authorization

The service validates RS256 tokens from the Auth Service: `iss=yongdrel-auth`, `aud=yongdrel-api`, expiry, and a **numeric `sub`**. Any failure is a 401.

| Endpoint | STUDENT (`USER`) | ORGANIZER | SERVICE | ADMIN |
|---|---|---|---|---|
| `POST /api/audit/events` | 403 | 403 | ✔ | ✔ |
| `GET /api/audit/events` | own actions only (`actorId` forced to `sub`) | own tenant only (forced; needs `tenantId` claim) | 403 | all |
| `GET /api/audit/events/{id}` | if they are the actor, else 404 | if same tenant, else 404 | 403 | ✔ |
| `GET /api/audit/correlations/{correlationId}` | own actions in it | own tenant's events in it | 403 | ✔ |
| `PUT` / `PATCH` / `DELETE` anything | 405 | 405 | 405 | 405 |

## REST API

### `POST /api/audit/events`: record an event (`SERVICE`, `ADMIN`)

```json
{
  "eventId": "3f2b8c1e-7a4d-4e1b-9c55-0a1b2c3d4e5f",
  "tenantId": 10,
  "actorType": "USER", "actorId": 100, "actorRole": "STUDENT",
  "action": "PAYMENT_PROOF_UPLOADED",
  "outcome": "SUCCESS",
  "targetType": "ORDER", "targetId": "5001",
  "sourceService": "payment-proof-service",
  "correlationId": "order-5001-checkout",
  "occurredAt": "2026-11-10T09:59:55Z",
  "details": {"amount": 100}
}
```

The `correlationId` comes from the body if present, otherwise from the `X-Correlation-Id` header, otherwise a new UUID is generated. It is echoed in the `X-Correlation-Id` response header.

A new event returns **201** with `Location: /api/audit/events/{id}`:

```json
{"id":1,"eventId":"3f2b8c1e-…","tenantId":10,"actorType":"USER","actorId":100,"actorRole":"STUDENT",
 "action":"PAYMENT_PROOF_UPLOADED","outcome":"SUCCESS","targetType":"ORDER","targetId":"5001",
 "sourceService":"payment-proof-service","correlationId":"order-5001-checkout",
 "occurredAt":"2026-11-10T09:59:55Z","recordedAt":"2026-11-10T10:00:00.123456Z",
 "details":{"amount":100},"integrityHash":"9c1f…","integrityValid":true}
```

### Restricted search and lookup

| Request | Purpose |
|---|---|
| `GET /api/audit/events?tenantId&actorId&actorType&action&outcome&targetType&targetId&correlationId&sourceService&from&to&page=0&size=20` | search, newest first. `from`/`to` are ISO-8601 instants (≤ 366 days apart) and apply to `occurredAt`; `size` is 1–100 |
| `GET /api/audit/events/{id}` | one event |
| `GET /api/audit/correlations/{correlationId}` | timeline of one business transaction, oldest first (≤ 500) |
| `GET /actuator/health` | health, no token needed |

### Status codes

All errors use one JSON body: `{timestamp, status, error, message, path, fieldErrors?}`.

| Code | When |
|---|---|
| 400 | validation (`fieldErrors`), malformed JSON, non-UUID `eventId`, unknown enum, `occurredAt` > 5 min in the future, details > 4000 chars / 50 keys, bad query params, `from ≥ to`, range > 366 days |
| 401 | missing, invalid, expired, wrong-issuer/audience token, or non-numeric `sub` |
| 403 | wrong role for the endpoint; organizer asks for another tenant or has no `tenantId` claim; student asks for another `actorId` |
| 404 | unknown id or correlation, or one the caller is not allowed to see |
| 405 | `PUT`/`PATCH`/`DELETE` (append-only) |
| 409 | `eventId` already recorded |
| 422 | unknown `sourceService`; `tenantId` unknown to the Tenant Service (when that check is on) |
| 503 | Tenant Service unreachable (when that check is on) |

## Consuming business audit events (RabbitMQ)

| Exchange (topic) | Binding → queue | Message |
|---|---|---|
| `yongdrel.audit.events` | `audit.#` → `audit.events` | same JSON as `POST /api/audit/events` |
| `yongdrel.fraud.events` | `fraud.flag.#` → `audit.fraud-events` | Fraud & Risk flag events, recorded as `FRAUD_FLAG_RAISED` / `FRAUD_FLAG_REVIEWED`, target `RISK_FLAG`, correlation `fraud-flag-{flagId}` |

* **Invalid messages** are rejected to the dead-letter queue `audit.events.dlq`, through exchange `yongdrel.audit.dlx`. They are never retried in a loop.
* **To publish from another service:** send JSON to `yongdrel.audit.events` with a routing key such as `audit.order.cancelled`. The `__TypeId__` header is ignored, so any producer class works.

## Running

```bash
cd services/audit-log-service && ./mvnw -DskipTests package && cd ../../infrastructure
docker compose up -d --build mysql rabbitmq config-server audit-log-service api-gateway
curl http://localhost:8091/actuator/health     # {"status":"UP",...}
```

On a fresh MySQL volume, `infrastructure/mysql-init/20-audit-log-service.sql` creates `yongdrel_audit`. If your volume already exists, run it once:

```bash
docker exec -i yongdrel-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < mysql-init/20-audit-log-service.sql
```

Once the Tenant Service exists, set `TENANT_SERVICE_CHECK_ENABLED=true` in Compose.

## Testing

```bash
./mvnw verify                      # unit + API + Testcontainers (MySQL, RabbitMQ) + JaCoCo gate
JWT_PRIVATE_KEY=../../infrastructure/keys/private.pem ./scripts/api-smoke-test.sh   # 60 checks through the gateway
```

* The curl script also runs the cross-service flow, raising and reviewing a flag in fraud-risk-service. Set `SKIP_FRAUD_FLOW=1` if that service isn't running.
* Evidence per Jira subtask: [docs/TEST-EVIDENCE.md](docs/TEST-EVIDENCE.md).
* Request examples: [api-examples.http](api-examples.http).
