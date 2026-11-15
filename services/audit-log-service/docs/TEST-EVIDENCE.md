# Test Evidence: Sprint 3, Audit Log Service

**Owner / tester:** Nidup Dorji (QA & DevOps) · **Date:** 14 Nov 2026 · **Build:** Spring Boot 3.5.0, Java 17 target (run on JDK 21), MySQL 8.4, RabbitMQ 3, Docker Engine 29.3

## Summary

| Item | Result |
|---|---|
| Maven tests (`./mvnw verify`) | **133 run, 133 passed, 0 failed, 0 skipped** |
| Coverage gate (domain + application services ≥ 80 % line / ≥ 70 % branch) | **passed** |
| Whole-service coverage (JaCoCo) | **94.3 % line, 88.5 % branch** |
| Authenticated curl script through the API Gateway | **60 / 60 passed**: [evidence/api-smoke-test-20261114-155153.txt](evidence/api-smoke-test-20261114-155153.txt) |
| Docker Compose health | `{"status":"UP","groups":["liveness","readiness"]}`, container `healthy` 5 s after start |
| Cross-service flow fraud-risk-service → RabbitMQ → audit-log-service | passed (curl script section 9; `AuditLogServiceApplicationTests`) |
| Regression: Fraud & Risk curl script on the same stack | 56 / 56 passed |

## Evidence per Jira subtask

### 01: Design API and database

* The contract is in [API-CONTRACT.md](API-CONTRACT.md). It covers the append-only event; the actor, action, target, timestamp and correlation ID; cross-service identifiers; constraints; status codes; schema and indexes; and the message contracts.
* The schema is in [V1__create_audit_schema.sql](../src/main/resources/db/migration/V1__create_audit_schema.sql).
* Verified on real MySQL 8.4 by `AuditEventPersistenceIntegrationTest` (6 tests).

### 02: Implement core service

| Capability | Tests |
|---|---|
| Validation of every field | `AuditEventTest$Validation` (24), `AuditEventControllerTest$Recording.validationErrorsAre400`, `unreadableBodiesAre400` |
| Append-only and tamper evidence | `AuditEventTest$Integrity` (4), `IntegrityHasherTest` (3), `AuditEventPersistenceIntegrationTest.directDatabaseTamperingIsDetected`, `existingRowsCannotBeSavedAgain`, `AuditEventControllerTest$AppendOnly` (405) |
| Consume business audit events | `AuditEventListenerTest` (7: valid, default correlation, invalid → DLQ, reference failure → DLQ, fraud flag mapping, idempotent ids); `AuditLogServiceApplicationTests.consumesBusinessAuditEventsAndFraudFlags` (real RabbitMQ: consume, ignore redelivery, dead-letter invalid, fraud flag recorded) |
| Restricted search | `AuditQueryServiceTest` (14), `AuditEventPersistenceIntegrationTest.searchFiltersScopeAndRange`, `timelineIsOldestFirst` |
| Idempotency / duplicate eventId | `AuditRecordingServiceTest.duplicateEventIdIsConflict`, `brokerRedeliveryIsIgnored`; `duplicateEventIdViolatesUniqueKey` |
| Health | `AuditLogServiceApplicationTests.healthIsUp` |

### 03: Security and authorization

| Requirement | Tests |
|---|---|
| RS256, `iss=yongdrel-auth`, `aud=yongdrel-api`, expiry, signature | `JwtDecoderConfigTest` (13, with real signed tokens) |
| Numeric subject | `JwtDecoderConfigTest.nonNumericSubjectIsRejected` (6 cases), `missingSubjectIsRejected`; controller `nonNumericSubjectIs403` |
| Role access (write = SERVICE/ADMIN, SERVICE cannot read) | `AuditRecordingServiceTest.usersCannotWriteAuditEvents`, `AuditQueryServiceTest.servicesCannotRead`, controller `studentsAndOrganizersCannotWrite`, `serviceCannotRead` |
| Tenant ownership | `organizerIsForcedToOwnTenant`, `organizerCannotAskForAnotherTenant`, `organizerWithoutTenantClaimIsRejected`, `getAppliesVisibility` |
| User ownership | `studentSeesOnlyOwnActivity`, `studentCannotAskForAnotherActor` |
| Reject invalid cross-service references | `AuditRecordingServiceTest.unknownSourceServiceIsRejected`, `unknownTenantIsRejectedWhenCheckEnabled`, `tenantServiceOutagePropagates`, `brokerPathStillChecksReferences`; `TenantReferenceAdapterTest` (5: exists + token forwarding, 404, 5xx/timeout, 403, disabled) |

### 04: Automated and negative tests

| Test class | Level | Tests | Failed |
|---|---|---|---|
| domain.AuditEventTest | Unit (BVA, validation, integrity) | 29 | 0 |
| domain.IntegrityHasherTest | Unit | 3 | 0 |
| domain.RoleTest | Unit | 9 | 0 |
| application.service.AuditRecordingServiceTest | Unit (Mockito) | 12 | 0 |
| application.service.AuditQueryServiceTest | Unit (Mockito) | 14 | 0 |
| config.JwtDecoderConfigTest | Security unit | 13 | 0 |
| adapter.in.web.AuditEventControllerTest | API (Recording 14, Searching 17, AppendOnly 1) | 32 | 0 |
| adapter.in.messaging.AuditEventListenerTest | Messaging unit | 7 | 0 |
| adapter.out.tenant.TenantReferenceAdapterTest | Cross-service contract | 5 | 0 |
| adapter.out.persistence.AuditEventPersistenceIntegrationTest | Integration (Testcontainers MySQL) | 6 | 0 |
| AuditLogServiceApplicationTests | System (MySQL + RabbitMQ containers) | 3 | 0 |
| **Total** | | **133** | **0** |

**Coverage by package (line / branch):**

* domain.model: 100 / 96.6
* domain.service: 83.3 / 100
* application.service: 98.5 / 88.0
* adapter.in.messaging: 100 / 81.2
* adapter.out.persistence: 98.6 / 100
* adapter.in.web: 87.0 / 70.0
* config: 93.3 / 85.7

**Authenticated curl script:** [scripts/api-smoke-test.sh](../scripts/api-smoke-test.sh) ran through the API Gateway with 60 checks, all passed. Full output: [evidence/api-smoke-test-20261114-155153.txt](evidence/api-smoke-test-20261114-155153.txt).

| Group | Checks |
|---|---|
| Health | 1 |
| 401 invalid JWT (none, garbage, tampered, expired, wrong issuer, wrong audience, non-numeric `sub`) | 7 |
| Record events (201, integrity, correlation header echo and fallback) | 6 |
| 409 duplicate eventId / 422 unknown source service | 2 |
| 400 bad input (validation, future time, bad UUID, enum, JSON, paging, range, date, correlation, id) | 11 |
| Restricted search (admin 2, organizer 2, other organizer 0, student 1, filters, timeline, get) | 13 |
| 403 (student write, organizer write, service read, other tenant, other actor, no tenantId) | 6 |
| 404 (unknown, other tenant, other student, SYSTEM event to student, unknown correlation) | 5 |
| 405 append-only (PUT, DELETE) | 2 |
| Cross-service: fraud check → flag → review → `FRAUD_FLAG_RAISED` + `FRAUD_FLAG_REVIEWED` in audit, hidden from other tenant | 7 |

### 05: Gateway, Compose and documentation

| Item | Evidence |
|---|---|
| Gateway route `/api/audit/**` | `api-gateway/src/main/resources/application-{dev,docker}.properties` `routes[3]`; all curl checks ran through the gateway |
| Compose service | `infrastructure/compose.yml` → `audit-log-service` (port 8091, `DB_*`, `RABBITMQ_*`, `JWT_PUBLIC_KEY_PATH`, healthcheck); `infrastructure/mysql-init/20-audit-log-service.sql` |
| RabbitMQ | after the run, queues `audit.events` and `audit.fraud-events` had 0 messages (all consumed) and `audit.events.dlq` had 0 |
| Documentation | [README](../README.md), [API-CONTRACT.md](API-CONTRACT.md), [api-examples.http](../api-examples.http) |

## Known limitations

* The Tenant Service isn't deployed, so the tenant existence check is off (`TENANT_SERVICE_CHECK_ENABLED=false`). It is covered by contract tests with a stubbed Tenant Service.
* The Auth Service doesn't issue `tenantId` or `role=SERVICE` yet. The curl script mints RS256 dev tokens with the same issuer and audience.
* The append-only guarantee is enforced by the application: no endpoints, an immutable entity, and no repository methods for updates or deletes. Direct SQL changes are still possible but are **detected** (`integrityValid=false`). Fully blocking them needs a separate DB user with only `INSERT`/`SELECT`.
