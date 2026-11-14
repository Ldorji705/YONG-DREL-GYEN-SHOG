package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.application.exception.DependencyUnavailableException;
import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ReferenceValidationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionCommand;
import com.yongdrel.fraudriskservice.application.port.in.AssessTransactionUseCase;
import com.yongdrel.fraudriskservice.application.port.in.AssessmentResult;
import com.yongdrel.fraudriskservice.config.SecurityConfig;
import com.yongdrel.fraudriskservice.domain.model.Actor;
import com.yongdrel.fraudriskservice.domain.model.Role;

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

import static com.yongdrel.fraudriskservice.TestData.flaggedCheck;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.service;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.student;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.token;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudCheckController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class FraudCheckControllerTest {

    private static final String VALID = """
            {"tenantId":10,"eventId":5,"orderId":1000,"buyerId":100,
             "paymentReference":"TXN-1234","proofImageHash":"%s",
             "amount":100.00,"expectedAmount":100.00}
            """.formatted("a".repeat(64));

    @Autowired MockMvc mvc;
    @MockitoBean AssessTransactionUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void createReturns201WithFullResultForOrganizer() throws Exception {
        when(useCase.assess(any(), any())).thenReturn(new AssessmentResult(flaggedCheck(42L), true));

        mvc.perform(post("/api/fraud/checks").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/fraud/checks/42"))
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.decision").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andExpect(jsonPath("$.findings[0].ruleType").value("DUPLICATE_PAYMENT_REFERENCE"));

        ArgumentCaptor<Actor> actor = ArgumentCaptor.forClass(Actor.class);
        ArgumentCaptor<AssessTransactionCommand> cmd = ArgumentCaptor.forClass(AssessTransactionCommand.class);
        verify(useCase).assess(cmd.capture(), actor.capture());
        assertThat(actor.getValue()).isEqualTo(new Actor(3L, Role.ORGANIZER, 10L));
        assertThat(cmd.getValue().orderId()).isEqualTo(1000L);
    }

    @Test
    void replayReturns200() throws Exception {
        when(useCase.assess(any(), any())).thenReturn(new AssessmentResult(flaggedCheck(42L), false));

        mvc.perform(post("/api/fraud/checks").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk());
    }

    @Test
    void studentSeesOnlyTheDecision() throws Exception {
        when(useCase.assess(any(), eq(new Actor(100L, Role.STUDENT, null))))
                .thenReturn(new AssessmentResult(flaggedCheck(42L), true));

        mvc.perform(post("/api/fraud/checks").with(student())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.findings").doesNotExist())
                .andExpect(jsonPath("$.score").doesNotExist());
    }

    @Test
    void missingTokenIs401WithJsonBody() throws Exception {
        mvc.perform(post("/api/fraud/checks").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(useCase);
    }

    @Test
    void unknownRoleIs403() throws Exception {
        mvc.perform(post("/api/fraud/checks").with(token("9", "HACKER", null))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonNumericSubjectIs403() throws Exception {
        mvc.perform(post("/api/fraud/checks").with(token("alice", "ADMIN", null))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Token subject must be a numeric user id"));
        verifyNoInteractions(useCase);
    }

    @Test
    void invalidBodyIs400WithFieldErrors() throws Exception {
        String body = """
                {"tenantId":-1,"eventId":5,"orderId":1000,"buyerId":100,
                 "proofImageHash":"not-a-hash","amount":0}
                """;
        mvc.perform(post("/api/fraud/checks").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("tenantId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("proofImageHash")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("amount")));
        verifyNoInteractions(useCase);
    }

    @Test
    void evidenceIsRequired() throws Exception {
        String body = """
                {"tenantId":10,"eventId":5,"orderId":1000,"buyerId":100,"amount":100}
                """;
        mvc.perform(post("/api/fraud/checks").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].message", hasItem("paymentReference or proofImageHash is required")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{not json", "{\"tenantId\":\"abc\"}", ""})
    void malformedBodyIs400(String body) throws Exception {
        mvc.perform(post("/api/fraud/checks").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void wrongContentTypeIs415() throws Exception {
        mvc.perform(post("/api/fraud/checks").with(admin())
                        .contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void forbiddenFromUseCaseIs403() throws Exception {
        when(useCase.assess(any(), any())).thenThrow(new ForbiddenOperationException("not your tenant"));

        mvc.perform(post("/api/fraud/checks").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("not your tenant"));
    }

    @Test
    void badCrossServiceReferenceIs422() throws Exception {
        when(useCase.assess(any(), any())).thenThrow(new ReferenceValidationException("Order 1000 does not exist"));

        mvc.perform(post("/api/fraud/checks").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Order 1000 does not exist"));
    }

    @Test
    void orderServiceDownIs503() throws Exception {
        when(useCase.assess(any(), any())).thenThrow(new DependencyUnavailableException("Order Service is unavailable"));

        mvc.perform(post("/api/fraud/checks").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getCheckReturnsIt() throws Exception {
        when(useCase.getCheck(eq(42L), any())).thenReturn(flaggedCheck(42L));

        mvc.perform(get("/api/fraud/checks/42").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1000));
    }

    @Test
    void studentCannotGetChecks() throws Exception {
        mvc.perform(get("/api/fraud/checks/42").with(student()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void unknownCheckIs404() throws Exception {
        when(useCase.getCheck(eq(7L), any())).thenThrow(new ResourceNotFoundException("Risk check 7 not found"));

        mvc.perform(get("/api/fraud/checks/7").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/api/fraud/checks/7"));
    }

    @Test
    void nonNumericIdIs400() throws Exception {
        mvc.perform(get("/api/fraud/checks/abc").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'id' has invalid value 'abc'"));
    }

    @Test
    void negativeIdIs400() throws Exception {
        mvc.perform(get("/api/fraud/checks/-5").with(admin()))
                .andExpect(status().isBadRequest());
    }
}
