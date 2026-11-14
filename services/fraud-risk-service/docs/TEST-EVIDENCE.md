# Test Evidence: Sprint 3, Fraud & Risk Service

**Owner / tester:** Nidup Dorji (QA & DevOps) · **Date:** 14 Nov 2026 · **Build:** Spring Boot 3.5.0, Java 17 target (run on JDK 21), MySQL 8.4, RabbitMQ 3, Docker Engine 29.3

## Summary

| Item | Result |
|---|---|
| Maven tests (`./mvnw verify`) | **196 run, 196 passed, 0 failed, 0 skipped** |
| Coverage gate (domain + application services ≥ 80 % line / ≥ 70 % branch) | **passed** |
| Whole-service coverage (JaCoCo) | **96.7 % line, 92.1 % branch** |
| Authenticated curl script through the API Gateway | **56 / 56 passed**: [evidence/api-smoke-test-20261114-151743.txt](evidence/api-smoke-test-20261114-151743.txt) |
| Docker Compose health | `{"status":"UP","groups":["liveness","readiness"]}`, container `healthy` |

## Evidence per Jira subtask

### 01: Design API and database

* The contract is in [API-CONTRACT.md](API-CONTRACT.md). It covers cross-service identifiers (tenant, event, order as the target entity, buyer, reviewer), the rule, flag, score and review-state enums, request and response schemas with constraints, status codes per endpoint, the ER diagram and indexes, and the event contract.
* The schema is in [V1__create_fraud_schema.sql](../src/main/resources/db/migration/V1__create_fraud_schema.sql), with seed rules in [V2__seed_default_rules.sql](../src/main/resources/db/migration/V2__seed_default_rules.sql).
* Verified by `PersistenceAdaptersIntegrationTest` (Flyway on real MySQL 8.4, 9 tests).

### 02: Implement core service

| Capability | Tests |
|---|---|
| Duplicate payment checks (reference, screenshot) | `RiskEvaluatorTest.duplicateReference`, `duplicateProof`, `configurableThreshold`; `PersistenceAdaptersIntegrationTest.duplicateQueriesCountOtherOrdersInsideWindowOnly` |
| Suspicious activity rules (velocity, amount, prior fraud, score cap, levels) | `RiskEvaluatorTest` (18, boundary values), `ScoringPolicyAndEffectiveRulesTest` (14), `RiskAssessmentServiceTest$Assessing` |
| Manual review flow | `DomainModelTest$FlagLifecycle` (16, full state-transition table), `FlagReviewServiceTest` (12) |
| Configurable rules and tenant overrides | `RiskRuleServiceTest` (13), `DomainModelTest$RuleValidation` (9) |
| Idempotent replay, optimistic locking | `RiskAssessmentServiceTest.identicalResubmissionReturnsExistingCheck`, `PersistenceAdaptersIntegrationTest.staleFlagUpdateIsRejected`, `idempotencyKeyIsUnique` |
| Health endpoint | `FraudRiskServiceApplicationTests.healthEndpointIsUpWithoutToken` |

### 03: Security and authorization

| Requirement | Tests |
|---|---|
| RS256, issuer `yongdrel-auth`, audience `yongdrel-api`, expiry, signature | `JwtDecoderConfigTest` (13, with real signed tokens): valid, wrong issuer, wrong audience, expired, other key, tampered payload |
| Numeric subject | `JwtDecoderConfigTest.nonNumericSubjectIsRejected` (`abc`, email, `0`, `-5`, `12a`, overflow), `missingSubjectIsRejected`; `FraudCheckControllerTest.nonNumericSubjectIs403` (defence in depth) |
| Role access | 401/403 tests in `FraudCheckControllerTest`, `RiskFlagControllerTest`, `RiskRuleControllerTest` |
| User ownership (student = own buyerId) | `RiskAssessmentServiceTest$Authorization.studentCannotSubmitForSomeoneElse` |
| Tenant isolation | `FlagReviewServiceTest.organizerSearchIsForcedToOwnTenant`, `reviewOfAnotherTenantsFlagIs404`, `RiskRuleServiceTest.organizerCannotCreateGlobalOrOtherTenantRules` |
| Reject invalid cross-service references | `RiskAssessmentServiceTest$CrossServiceReference` (missing order, tenant mismatch, buyer mismatch, outage); `OrderReferenceAdapterTest` (404, 403, 5xx, timeout, token forwarding) |

### 04: Automated and negative tests

**Maven results:**

| Test class | Level | Tests | Failed |
|---|---|---|---|
| domain.service.RiskEvaluatorTest | Unit (BVA, equivalence classes) | 18 | 0 |
| domain.service.ScoringPolicyAndEffectiveRulesTest | Unit (BVA) | 14 | 0 |
| domain.model.DomainModelTest | Unit (state transitions, validation) | 42 | 0 |
| application.service.RiskAssessmentServiceTest | Unit (Mockito) | 18 | 0 |
| application.service.FlagReviewServiceTest | Unit (Mockito) | 12 | 0 |
| application.service.RiskRuleServiceTest | Unit (Mockito) | 13 | 0 |
| config.JwtDecoderConfigTest | Security unit | 13 | 0 |
| adapter.in.web.FraudCheckControllerTest | API | 20 | 0 |
| adapter.in.web.RiskFlagControllerTest | API | 15 | 0 |
| adapter.in.web.RiskRuleControllerTest | API / CRUD | 13 | 0 |
| adapter.out.order.OrderReferenceAdapterTest | Cross-service contract | 6 | 0 |
| adapter.out.persistence.PersistenceAdaptersIntegrationTest | Integration (Testcontainers MySQL) | 9 | 0 |
| FraudRiskServiceApplicationTests | System (MySQL + RabbitMQ containers) | 3 | 0 |
| **Total** | | **196** | **0** |

**Authenticated curl script:** [scripts/api-smoke-test.sh](../scripts/api-smoke-test.sh) was run through the API Gateway. It made 56 checks, all passed. Full output: [evidence/api-smoke-test-20261114-151743.txt](evidence/api-smoke-test-20261114-151743.txt).

| Group | Checks | Examples |
|---|---|---|
| Health | 1 | 200 UP |
| 401 invalid JWT | 7 | no token, garbage, tampered, expired, wrong issuer, wrong audience, non-numeric `sub` |
| Duplicate payment checks | 8 | clean 201, replay 200, duplicate reference → REVIEW_REQUIRED, duplicate screenshot |
| Suspicious activity | 2 + 2 | amount mismatch; buyer with confirmed fraud flagged again (`PRIOR_CONFIRMED_FRAUD`) |
| Manual review flow | 8 | queue, start review, close without note 400, confirm 200, re-review 409 |
| 400 bad input | 7 | validation `fieldErrors`, malformed JSON, bad enum, page size, non-numeric id, invalid rule |
| 403 unauthorized | 7 | student for other buyer, student reads flags, other tenant, no tenantId claim, service review, organizer edits default |
| 404 absent record | 5 | unknown check, flag or rule; other tenant's flag and check hidden |
| CRUD + 409 conflict | 9 | create 201, duplicate 409, read, effective, update, other organizer 403, delete 204, then 404 |

### 05: Gateway, Compose and documentation

| Item | Where / evidence |
|---|---|
| Gateway route `/api/fraud/**` | `api-gateway/src/main/resources/application-{dev,docker}.properties` (`routes[2]`); every curl call went through the gateway, which validated the JWT before the service did |
| Compose service, DB env, RabbitMQ | `infrastructure/compose.yml` → `fraud-risk-service` (port 8090, `DB_*`, `RABBITMQ_*`, `JWT_PUBLIC_KEY_PATH`, healthcheck); `infrastructure/mysql-init/10-fraud-risk-service.sql` creates `yongdrel_fraud` |
| Health | container `healthy` 15–20 s after start; `GET /actuator/health` → UP |
| RabbitMQ flow | `FraudRiskServiceApplicationTests` receives `FLAG_RAISED` and `FLAG_REVIEWED` from `fraud.flags.review`; in the Compose run the queue held 2 messages after raise + review |
| Cross-service flow | the Order Service lookup is tested against a stubbed Order Service (`OrderReferenceAdapterTest`); the live check is off until the Order Service exists |
| Documentation | [README](../README.md), [API-CONTRACT.md](API-CONTRACT.md), [api-examples.http](../api-examples.http) |

## Known limitations

* The Order Service is not deployed yet, so `ORDER_SERVICE_CHECK_ENABLED=false`. Turn it on and rerun the curl script when it is.
* The Auth Service does not issue `tenantId` or `role=SERVICE` yet. The curl script mints dev tokens with the same RS256 key, issuer and audience.
* If RabbitMQ is down, events are logged and dropped, but the flags stay in the database and the review queue. A transactional outbox would guarantee delivery.
