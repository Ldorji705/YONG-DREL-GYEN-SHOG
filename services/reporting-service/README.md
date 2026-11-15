# Reporting Service

Sprint 3 · Owner: Nidup Dorji · Port **8092** · Database **`yongdrel_reporting`** · Gateway path **`/api/reports/**`**

This service gives **organizer-scoped** event, sales, ticket, prize and payout summaries, with strict **tenant isolation**. An organizer only ever sees their own tenant: another tenant's event returns 404, and naming another tenant returns 403.

## How it gets its data

Each service owns its own database, so Reporting keeps a **read model**: local, read-only copies of events, orders, prizes and payouts. The owning services push these copies whenever their data changes, either over REST or over RabbitMQ.

```
Event / Order / Prize / Winner services
   ├── PUT /api/reports/ingest/{events|orders|prizes|payouts}/{id}   (SERVICE JWT)
   └── RabbitMQ yongdrel.reporting.feed  (event|order|prize|payout).upserted
                       │
                       ▼
            Reporting Service ── MySQL yongdrel_reporting (report_* + saved_reports)
                       ▲
organizer / admin ── GET /api/reports/...      student ── GET /api/reports/me/purchases
```

**Update rules.** Every pushed record carries `sourceUpdatedAt`, the time it last changed in its owning service.

| Push | Result |
|---|---|
| New record | **201** `CREATED` |
| Newer version | **200** `UPDATED` |
| Identical repeat | **200** `UNCHANGED` |
| Older version, or same version with different content | **409** (over RabbitMQ: acknowledged and ignored) |
| Changes an immutable reference (event's tenant; order's event, tenant or buyer; payout's prize or winner) | **409** |

**Cross-service reference checks:**

* An order, prize or payout must reference a **known event of the same tenant**.
* A payout must reference a **prize of that event**.
* Anything else returns **422**. Over RabbitMQ, the message goes to the dead-letter queue `reporting.feed.dlq`.

> **For teammates (Event, Order, Prize Catalog, Winner services):** publish your current state to topic exchange `yongdrel.reporting.feed` with routing key `event.upserted`, `order.upserted`, `prize.upserted` or `payout.upserted`. Bodies are in [docs/API-CONTRACT.md](docs/API-CONTRACT.md#3-ingest-contract-what-other-services-send). **Publish the event before its orders, prizes and payouts.**

## Hexagonal structure

```
com.yongdrel.reportingservice
├── domain           projections (Event/Order/Prize/Payout) with validation, SavedReport, Summaries,
│                    UpsertPolicy (versioning), ReportCalculator (all figures), ReportPeriod
├── application
│   ├── port/in      IngestUseCase, ReportUseCase, SavedReportUseCase
│   ├── port/out     ProjectionRepositoryPort, ReportQueryPort, SavedReportRepositoryPort
│   └── service      IngestService, ReportService, SavedReportService, AccessPolicy
├── adapter
│   ├── in/web       ReportController, IngestController, SavedReportController, errors, JWT mapping
│   ├── in/messaging ReportingFeedListener (4 queues + DLQ)
│   └── out/persistence  JPA entities, aggregate queries (MySQL, Flyway)
└── config           Security, JWT decoder, clock
```

## Authorization

The service validates RS256 tokens: `iss=yongdrel-auth`, `aud=yongdrel-api`, expiry, and a **numeric `sub`**. Any failure is a 401.

| Endpoint | ORGANIZER | ADMIN | STUDENT (`USER`) | SERVICE |
|---|---|---|---|---|
| `GET /overview, /events, /events/{id}/summary, /sales, /tickets, /prizes, /payouts` | own tenant only (`tenantId` optional, must match; needs `tenantId` claim) | any tenant, or platform-wide | 403 | 403 |
| `GET /me/purchases` | 403 | 403 | own orders only | 403 |
| `/saved` CRUD + `/saved/{id}/run` | own tenant; only the creator may edit or delete | 403 | 403 | 403 |
| `PUT /ingest/**` | 403 | ✔ | 403 | ✔ |

## REST API (base `/api/reports`)

| Request | Returns |
|---|---|
| `GET /overview?tenantId&from&to` | events by status, sales, tickets, prizes, payouts, net after prizes |
| `GET /events?tenantId&status&page&size` | events with capacity, tickets sold, revenue, sell-through % |
| `GET /events/{eventId}/summary` | everything for one event, including prize tiers |
| `GET /sales?tenantId&eventId&from&to` | confirmed orders, tickets, revenue, average order, pending, failed, conversion %, **daily breakdown** |
| `GET /tickets?tenantId&eventId` | capacity, sold, reserved (pending), available, sell-through % |
| `GET /prizes?tenantId&eventId` | tiers, units, total value (tier list when `eventId` is given) |
| `GET /payouts?tenantId&eventId` | winners, total, paid, outstanding, cancelled, paid % |
| `GET /me/purchases` | student's tickets and spend per event |
| `POST /saved`, `GET /saved`, `GET/PUT/DELETE /saved/{id}`, `GET /saved/{id}/run` | saved report definitions |
| `PUT /ingest/events/{id}` (and `orders`, `prizes`, `payouts`) | push a record; returns `{kind, id, outcome}` |

Money is in **BTN** with 2 decimals. `from`/`to` are ISO-8601 instants that filter orders by creation time (≤ 366 days apart).

Example `GET /events/{id}/summary`:

```json
{"eventId":17946516021,"tenantId":53602,"name":"Losar Raffle","status":"OPEN","ticketPrice":50.00,
 "sales":{"currency":"BTN","confirmedOrders":2,"ticketsSold":3,"revenue":150.00,"averageOrderValue":75.00,
          "pendingOrders":1,"pendingAmount":50.00,"failedOrders":1,"conversionRatePercent":66.67,
          "daily":[{"date":"2026-11-02","orders":2,"tickets":3,"revenue":150.00}]},
 "tickets":{"capacity":100,"sold":3,"reserved":1,"available":96,"sellThroughPercent":3.00},
 "prizes":{"currency":"BTN","prizeTiers":1,"prizeUnits":1,"totalPrizeValue":60.00,"tiers":[{"prizeId":17946516027,"tier":1,"name":"Gold coin","quantity":1,"unitValue":60.00,"totalValue":60.00}]},
 "payouts":{"currency":"BTN","winners":1,"totalAmount":60.00,"paidCount":1,"paidAmount":60.00,"pendingCount":0,"outstandingAmount":0.00,"cancelledCount":0,"paidPercent":100.00},
 "netAfterPrizes":90.00}
```

### Status codes

All errors use one JSON body: `{timestamp, status, error, message, path, fieldErrors?}`.

| Code | When |
|---|---|
| 400 | validation (`fieldErrors`), malformed JSON, unknown enum, bad dates, `from ≥ to`, period > 366 days, paging, body id ≠ path id, domain rules (e.g. `CONFIRMED` without `confirmedAt`), `EVENT_SUMMARY` without `eventId` |
| 401 | missing, invalid, expired, wrong-issuer/audience token, or non-numeric `sub` |
| 403 | wrong role; organizer names another tenant or has no `tenantId` claim; editing another organizer's saved report |
| 404 | unknown event, unknown saved report, or one belonging to another tenant |
| 405 | wrong HTTP method |
| 409 | stale or conflicting ingest version, changed immutable reference, saved report name already used in the tenant (case-insensitive), concurrent edit |
| 422 | ingest references an unknown event or prize, or one of another tenant or event |

## Running

```bash
cd services/reporting-service && ./mvnw -DskipTests package && cd ../../infrastructure
docker compose up -d --build mysql rabbitmq config-server reporting-service api-gateway
curl http://localhost:8092/actuator/health
```

On a fresh MySQL volume, `infrastructure/mysql-init/30-reporting-service.sql` creates `yongdrel_reporting`. If your volume already exists, run it once:

```bash
docker exec -i yongdrel-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" < mysql-init/30-reporting-service.sql
```

## Testing

```bash
./mvnw verify      # 150 tests: unit, API, Testcontainers MySQL + RabbitMQ, JaCoCo gate
JWT_PRIVATE_KEY=../../infrastructure/keys/private.pem RABBITMQ_USER=... RABBITMQ_PASSWORD=... ./scripts/api-smoke-test.sh
```

* The curl script makes 90 checks through the gateway.
* The RabbitMQ part publishes through the management API on port 15672, and is skipped if `RABBITMQ_USER` is unset.
* Evidence: [docs/TEST-EVIDENCE.md](docs/TEST-EVIDENCE.md).
* Request examples: [api-examples.http](api-examples.http).
