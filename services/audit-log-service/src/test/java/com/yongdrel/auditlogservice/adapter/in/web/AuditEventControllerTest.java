package com.yongdrel.auditlogservice.adapter.in.web;

import com.yongdrel.auditlogservice.application.exception.DependencyUnavailableException;
import com.yongdrel.auditlogservice.application.exception.DuplicateAuditEventException;
import com.yongdrel.auditlogservice.application.exception.ForbiddenOperationException;
import com.yongdrel.auditlogservice.application.exception.ReferenceValidationException;
import com.yongdrel.auditlogservice.application.exception.ResourceNotFoundException;
import com.yongdrel.auditlogservice.application.model.AuditSearchCriteria;
import com.yongdrel.auditlogservice.application.model.PageResult;
import com.yongdrel.auditlogservice.application.port.in.AuditQueryUseCase;
import com.yongdrel.auditlogservice.application.port.in.RecordAuditEventUseCase;
import com.yongdrel.auditlogservice.config.SecurityConfig;
import com.yongdrel.auditlogservice.domain.exception.InvalidAuditEventException;
import com.yongdrel.auditlogservice.domain.model.ActorType;
import com.yongdrel.auditlogservice.domain.model.AuditEventDraft;
import com.yongdrel.auditlogservice.domain.model.Caller;
import com.yongdrel.auditlogservice.domain.model.Outcome;
import com.yongdrel.auditlogservice.domain.model.Role;
import com.yongdrel.auditlogservice.domain.model.TargetType;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static com.yongdrel.auditlogservice.TestData.CORRELATION;
import static com.yongdrel.auditlogservice.TestData.event;
import static com.yongdrel.auditlogservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.auditlogservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.auditlogservice.adapter.in.web.WebTestSupport.service;
import static com.yongdrel.auditlogservice.adapter.in.web.WebTestSupport.student;
import static com.yongdrel.auditlogservice.adapter.in.web.WebTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuditEventController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class AuditEventControllerTest {

    static final String VALID = """
            {"eventId":"3f2b8c1e-7a4d-4e1b-9c55-0a1b2c3d4e5f","tenantId":10,"actorType":"USER","actorId":100,
             "actorRole":"STUDENT","action":"PAYMENT_PROOF_UPLOADED","outcome":"SUCCESS","targetType":"ORDER",
             "targetId":"5001","sourceService":"payment-proof-service","correlationId":"order-5001-checkout",
             "occurredAt":"2026-11-10T09:59:55Z","details":{"amount":100}}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean RecordAuditEventUseCase recorder;
    @MockitoBean AuditQueryUseCase queries;
    @MockitoBean JwtDecoder jwtDecoder;

    @Nested
    class Recording {

        @Test
        void serviceRecordsEvent201() throws Exception {
            when(recorder.record(any(), any())).thenReturn(event(1L));

            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/audit/events/1"))
                    .andExpect(header().string("X-Correlation-Id", CORRELATION))
                    .andExpect(jsonPath("$.action").value("PAYMENT_PROOF_UPLOADED"))
                    .andExpect(jsonPath("$.details.amount").value(100))
                    .andExpect(jsonPath("$.integrityValid").value(true));

            ArgumentCaptor<AuditEventDraft> draft = ArgumentCaptor.forClass(AuditEventDraft.class);
            verify(recorder).record(draft.capture(), eq(new Caller(2L, Role.SERVICE, null)));
            assertThat(draft.getValue().detailsJson()).isEqualTo("{\"amount\":100}");
            assertThat(draft.getValue().targetType()).isEqualTo(TargetType.ORDER);
        }

        @Test
        void correlationIdFallsBackToHeaderThenUuid() throws Exception {
            when(recorder.record(any(), any())).thenReturn(event(1L));
            String noCorrelation = VALID.replace("\"correlationId\":\"order-5001-checkout\",", "");

            mvc.perform(post("/api/audit/events").with(admin()).header("X-Correlation-Id", "from-header-123")
                            .contentType(MediaType.APPLICATION_JSON).content(noCorrelation))
                    .andExpect(status().isCreated());
            mvc.perform(post("/api/audit/events").with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(noCorrelation))
                    .andExpect(status().isCreated());

            ArgumentCaptor<AuditEventDraft> draft = ArgumentCaptor.forClass(AuditEventDraft.class);
            verify(recorder, org.mockito.Mockito.times(2)).record(draft.capture(), any());
            assertThat(draft.getAllValues().get(0).correlationId()).isEqualTo("from-header-123");
            assertThat(draft.getAllValues().get(1).correlationId()).hasSize(36);
        }

        @Test
        void studentsAndOrganizersCannotWrite() throws Exception {
            mvc.perform(post("/api/audit/events").with(student())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/api/audit/events").with(organizer())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(recorder);
        }

        @Test
        void noTokenIs401() throws Exception {
            mvc.perform(post("/api/audit/events").contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.status").value(401));
        }

        @Test
        void nonNumericSubjectIs403() throws Exception {
            mvc.perform(post("/api/audit/events").with(token("svc-payments", "SERVICE", null))
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Token subject must be a numeric user id"));
        }

        @Test
        void validationErrorsAre400() throws Exception {
            String bad = """
                    {"eventId":"3f2b8c1e-7a4d-4e1b-9c55-0a1b2c3d4e5f","actorType":"USER","action":"paid",
                     "outcome":"SUCCESS","targetType":"ORDER","targetId":"a/b","sourceService":"Payment Service",
                     "occurredAt":"2026-11-10T09:59:55Z","tenantId":-1}
                    """;
            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("action")))
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("targetId")))
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("sourceService")))
                    .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("tenantId")))
                    .andExpect(jsonPath("$.fieldErrors[*].message", hasItem("actorId is required when actorType is USER")));
            verifyNoInteractions(recorder);
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "{\"eventId\":\"not-a-uuid\"}",
                "{\"actorType\":\"ROBOT\"}",
                "{\"occurredAt\":\"yesterday\"}",
                "{broken"})
        void unreadableBodiesAre400(String body) throws Exception {
            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void domainRuleViolationIs400() throws Exception {
            when(recorder.record(any(), any())).thenThrow(new InvalidAuditEventException("occurredAt cannot be in the future"));

            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("occurredAt cannot be in the future"));
        }

        @Test
        void duplicateIs409() throws Exception {
            when(recorder.record(any(), any())).thenThrow(new DuplicateAuditEventException("already recorded as id 7"));

            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("already recorded as id 7"));
        }

        @Test
        void unknownReferenceIs422() throws Exception {
            when(recorder.record(any(), any())).thenThrow(new ReferenceValidationException("Unknown sourceService 'x'"));

            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isUnprocessableEntity());
        }

        @Test
        void tenantServiceDownIs503() throws Exception {
            when(recorder.record(any(), any())).thenThrow(new DependencyUnavailableException("Tenant Service down"));

            mvc.perform(post("/api/audit/events").with(service())
                            .contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isServiceUnavailable());
        }
    }

    @Nested
    class AppendOnly {

        @Test
        void putPatchDeleteAre405() throws Exception {
            mvc.perform(put("/api/audit/events/1").with(admin()).contentType(MediaType.APPLICATION_JSON).content(VALID))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(jsonPath("$.message").value("Audit events are append-only: PUT is not supported on this path"));
            mvc.perform(patch("/api/audit/events/1").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isMethodNotAllowed());
            mvc.perform(delete("/api/audit/events/1").with(admin()))
                    .andExpect(status().isMethodNotAllowed());
            verifyNoInteractions(recorder, queries);
        }
    }

    @Nested
    class Searching {

        @Test
        void searchPassesAllFilters() throws Exception {
            when(queries.search(any(), eq(2), eq(10), any())).thenReturn(new PageResult<>(List.of(event(1L)), 2, 10, 21));

            mvc.perform(get("/api/audit/events").with(organizer())
                            .param("tenantId", "10").param("actorId", "100").param("actorType", "USER")
                            .param("action", "PAYMENT_PROOF_UPLOADED").param("outcome", "SUCCESS")
                            .param("targetType", "ORDER").param("targetId", "5001")
                            .param("correlationId", CORRELATION).param("sourceService", "payment-proof-service")
                            .param("from", "2026-11-01T00:00:00Z").param("to", "2026-11-30T00:00:00Z")
                            .param("page", "2").param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalPages").value(3));

            verify(queries).search(eq(new AuditSearchCriteria(10L, 100L, ActorType.USER, "PAYMENT_PROOF_UPLOADED",
                    Outcome.SUCCESS, TargetType.ORDER, "5001", CORRELATION, "payment-proof-service",
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-30T00:00:00Z"))), eq(2), eq(10), any());
        }

        @Test
        void studentCanSearchOwnActivity() throws Exception {
            when(queries.search(any(), anyInt(), anyInt(), any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

            mvc.perform(get("/api/audit/events").with(student())).andExpect(status().isOk());
            verify(queries).search(any(), eq(0), eq(20), eq(new Caller(100L, Role.STUDENT, null)));
        }

        @Test
        void serviceCannotRead() throws Exception {
            mvc.perform(get("/api/audit/events").with(service())).andExpect(status().isForbidden());
            verifyNoInteractions(queries);
        }

        @Test
        void forbiddenScopeFromUseCaseIs403() throws Exception {
            when(queries.search(any(), anyInt(), anyInt(), any()))
                    .thenThrow(new ForbiddenOperationException("Tenant 20 does not belong to the caller"));

            mvc.perform(get("/api/audit/events").param("tenantId", "20").with(organizer()))
                    .andExpect(status().isForbidden());
        }

        @ParameterizedTest
        @ValueSource(strings = {"size=0", "size=101", "page=-1", "outcome=MAYBE", "from=yesterday",
                "action=lower_case", "tenantId=0", "correlationId=bad id"})
        void badQueryParametersAre400(String param) throws Exception {
            String[] kv = param.split("=", 2);
            mvc.perform(get("/api/audit/events").param(kv[0], kv[1]).with(admin()))
                    .andExpect(status().isBadRequest());
            verifyNoInteractions(queries);
        }

        @Test
        void invalidRangeFromUseCaseIs400() throws Exception {
            when(queries.search(any(), anyInt(), anyInt(), any()))
                    .thenThrow(new InvalidAuditEventException("'from' must be before 'to'"));

            mvc.perform(get("/api/audit/events").param("from", "2026-11-30T00:00:00Z").param("to", "2026-11-01T00:00:00Z")
                            .with(admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("'from' must be before 'to'"));
        }

        @Test
        void getOne() throws Exception {
            when(queries.get(eq(1L), any())).thenReturn(event(1L));

            mvc.perform(get("/api/audit/events/1").with(admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId").value("3f2b8c1e-7a4d-4e1b-9c55-0a1b2c3d4e5f"));
        }

        @Test
        void getUnknownIs404() throws Exception {
            when(queries.get(eq(9L), any())).thenThrow(new ResourceNotFoundException("Audit event 9 not found"));

            mvc.perform(get("/api/audit/events/9").with(admin()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.path").value("/api/audit/events/9"));
        }

        @Test
        void nonNumericIdIs400() throws Exception {
            mvc.perform(get("/api/audit/events/abc").with(admin())).andExpect(status().isBadRequest());
        }

        @Test
        void timeline() throws Exception {
            when(queries.timeline(eq(CORRELATION), any())).thenReturn(List.of(event(1L)));

            mvc.perform(get("/api/audit/correlations/" + CORRELATION).with(organizer()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].correlationId").value(CORRELATION));
        }
    }
}
