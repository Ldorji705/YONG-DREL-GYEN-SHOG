# Test Evidence: Sprint 3, Reporting Service

**Owner / tester:** Nidup Dorji (QA & DevOps) · **Date:** 14 Nov 2026 · **Build:** Spring Boot 3.5.0, Java 17 target (run on JDK 21), MySQL 8.4, RabbitMQ 3, Docker Engine 29.3

## Summary

| Item | Result |
|---|---|
| Maven tests (`./mvnw verify`) | **150 run, 150 passed, 0 failed, 0 skipped** |
| Coverage gate (domain + application services ≥ 80 % line / ≥ 70 % branch) | **passed** |
| Whole-service coverage (JaCoCo) | **98.4 % line, 92.9 % branch** |
| Authenticated curl script through the API Gateway | **90 / 90 passed**: [evidence/api-smoke-test-20261114-162002.txt](evidence/api-smoke-test-20261114-162002.txt) |
| Docker Compose health | `{"status":"UP","groups":["liveness","readiness"]}`, container `healthy` 5 s after start |
| RabbitMQ feed in Compose | payout published to `yongdrel.reporting.feed` was routed and reflected in the payout report; all `reporting.*` queues drained, DLQ empty |
| Regression on the same stack | Fraud & Risk 56 / 56, Audit Log 60 / 60 |

## Evidence per Jira subtask

### 01: Design API and database

* The contract is in [API-CONTRACT.md](API-CONTRACT.md). It covers:
  * cross-service identifiers;
  * tenant-scoped report filters and their constraints;
  * aggregate definitions for sales, tickets, prizes, payouts and net;
  * the ingest contract for the services that will feed this one;
  * saved-report rules, status codes per endpoint, and the ER diagram with indexes.
* The schema is in [V1__create_reporting_schema.sql](../src/main/resources/db/migration/V1__create_reporting_schema.sql).
* Verified on real MySQL 8.4 by `ReadModelPersistenceIntegrationTest` (7 tests).

### 02: Implement core service

| Capability | Tests |
|---|---|
| Sales summary (revenue, average, pending, failed, conversion, daily by UTC date) | `ReportCalculatorTest.salesFigures`, `emptySalesHaveZeroesNotErrors`, `percentRounding` (BVA); `ReadModelPersistenceIntegrationTest.eventAndPeriodFilters` |
| Ticket summary (capacity, sold, reserved, available ≥ 0, sell-through) | `ReportCalculatorTest.ticketFigures`, `availableNeverNegative` |
| Prize summary (tiers, units, value) | `ReportCalculatorTest.prizeFigures`, `ReportServiceTest.prizeTiersOnlyForSingleEvent` |
| Payout summary (paid, outstanding, cancelled, paid %) | `ReportCalculatorTest.payoutFigures`, `IngestServiceTest.payoutStatusProgressesWithNewerVersion` |
| Event summary / overview / events list / student purchases | `ReportServiceTest` (15) |
| Read model kept in sync (create / update / unchanged / stale) | `UpsertPolicyTest` (6), `IngestServiceTest` (8), `ReportingFeedListenerTest` (4) |
| Validation of all fields | `ProjectionTest` (19), `SavedReportTest` (4), controller validation tests |
| Saved report CRUD | `SavedReportServiceTest` (17), `SavedReportControllerTest` (7), `ReadModelPersistenceIntegrationTest.savedReportCrudAndUniqueName` |
| Health | `ReportingServiceApplicationTests.healthIsUp` |

### 03: Security and authorization

| Requirement | Tests |
|---|---|
| RS256, `iss`, `aud`, expiry, signature, numeric `sub` | `JwtDecoderConfigTest` (13, real signed tokens); `ReportControllerTest.nonNumericSubjectIs403` |
| Role access | `ReportControllerTest.studentGetsOwnPurchasesOnly`, `servicesCannotReadReports`; `IngestControllerTest.organizersCannotIngest`; `SavedReportControllerTest.onlyOrganizers`; `IngestServiceTest.onlyServicesAndAdminsCanIngest` |
| Tenant isolation | `ReportServiceTest.organizerIsForcedToOwnTenant`, `organizerCannotRequestAnotherTenant`, `organizerCannotSeeAnotherTenantsEvent`, `adminAskingForEventOfOtherTenantIs404`; `ReadModelPersistenceIntegrationTest.tenantScopedAggregates`; `ReportingServiceApplicationTests.endToEndFiguresWithTenantIsolation` |
| Ownership | `ReportServiceTest.studentSeesOwnPurchases`; `SavedReportServiceTest.colleaguesCanReadButNotChange` |
| Reject invalid cross-service references | `IngestServiceTest.orderNeedsKnownEventOfSameTenant`, `prizeNeedsKnownEvent`, `payoutNeedsPrizeOfSameEvent`; `SavedReportServiceTest.eventMustBelongToTenant`; DB foreign keys in `ordersCannotReferenceMissingEvents` |

### 04: Automated and negative tests

| Test class | Level | Tests | Failed |
|---|---|---|---|
| domain.ProjectionTest | Unit (validation, BVA, normalization) | 19 | 0 |
| domain.UpsertPolicyTest | Unit (versioning decision table) | 6 | 0 |
| domain.ReportCalculatorTest | Unit (figures, BVA rounding) | 11 | 0 |
| domain.SavedReportTest | Unit | 4 | 0 |
| domain.RoleTest | Unit | 9 | 0 |
| application.service.IngestServiceTest | Unit (Mockito) | 8 | 0 |
| application.service.ReportServiceTest | Unit (Mockito) | 15 | 0 |
| application.service.SavedReportServiceTest | Unit (Mockito) | 17 | 0 |
| config.JwtDecoderConfigTest | Security unit | 13 | 0 |
| adapter.in.web.ReportControllerTest | API | 16 | 0 |
| adapter.in.web.IngestControllerTest | API | 12 | 0 |
| adapter.in.web.SavedReportControllerTest | API / CRUD | 7 | 0 |
| adapter.in.messaging.ReportingFeedListenerTest | Messaging unit | 4 | 0 |
| adapter.out.persistence.ReadModelPersistenceIntegrationTest | Integration (Testcontainers MySQL) | 7 | 0 |
| ReportingServiceApplicationTests | System (MySQL + RabbitMQ containers) | 2 | 0 |
| **Total** | | **150** | **0** |

**Coverage by package (line / branch):**

* domain.model: 100 / 93.8
* domain.service: 100 / 97.1
* application.service: 100 / 96.6
* adapter.in.messaging: 100 / 100
* adapter.out.persistence: 100 / 85.7
* adapter.in.web: 89.5 / 80.0
* config: 94.7 / 90.0

**Authenticated curl script:** [scripts/api-smoke-test.sh](../scripts/api-smoke-test.sh) ran through the API Gateway with 90 checks, all passed. Full output: [evidence/api-smoke-test-20261114-162002.txt](evidence/api-smoke-test-20261114-162002.txt).

| Group | Checks |
|---|---|
| Health | 1 |
| 401 invalid JWT (none, garbage, tampered, expired, wrong issuer, wrong audience, non-numeric `sub`) | 7 |
| Feed via REST (created, unchanged, updated, 4 order states, prize, payout) | 12 |
| 409 (older version, same version with other content, buyer changed, tenant changed) | 4 |
| 422 cross-service references (unknown event, tenant mismatch, unknown prize) | 3 |
| RabbitMQ feed (payout PAID routed, report reflects it) | 2 |
| Sales, ticket, prize and payout summaries, with figures checked (revenue 150.00, sold 3, reserved 1, available 96, net 90.00, conversion 66.67 %, daily breakdown, student 2 tickets) | 22 |
| Tenant isolation (other organizer: no revenue, foreign event 404, naming tenant 403) | 5 |
| Saved reports (create, duplicate 409, list, read, non-owner 403, update, run, other tenant 404, foreign event 404, delete, gone) | 12 |
| 400 bad input | 12 |
| 403 unauthorized | 7 |
| 404 absent record | 3 |

### 05: Gateway, Compose and documentation

| Item | Evidence |
|---|---|
| Gateway route `/api/reports/**` | `api-gateway/src/main/resources/application-{dev,docker}.properties` `routes[4]`; all curl checks ran through the gateway |
| Compose service | `infrastructure/compose.yml` → `reporting-service` (port 8092, `DB_*`, `RABBITMQ_*`, `JWT_PUBLIC_KEY_PATH`, healthcheck); `infrastructure/mysql-init/30-reporting-service.sql` |
| RabbitMQ | exchange `yongdrel.reporting.feed`, 4 queues + DLQ declared at start; publish → consume → report verified in Compose and in `ReportingServiceApplicationTests` (including a dead-lettered invalid message) |
| Documentation | [README](../README.md), [API-CONTRACT.md](API-CONTRACT.md), [api-examples.http](../api-examples.http) |

## Known limitations

* The Event, Order, Prize Catalog and Winner services aren't built yet, so their feeds were simulated with the documented ingest contract (REST and RabbitMQ). Those teams need to publish to `yongdrel.reporting.feed`, and send each event before its orders, prizes and payouts.
* The Auth Service doesn't yet issue `tenantId` or `role=SERVICE`. The curl script mints RS256 dev tokens with the same issuer and audience.
