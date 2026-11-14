package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.application.exception.ConflictException;
import com.yongdrel.fraudriskservice.application.exception.ForbiddenOperationException;
import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.port.in.RiskRuleUseCase;
import com.yongdrel.fraudriskservice.application.port.in.RuleSettings;
import com.yongdrel.fraudriskservice.config.SecurityConfig;
import com.yongdrel.fraudriskservice.domain.exception.InvalidRuleException;
import com.yongdrel.fraudriskservice.domain.model.RuleType;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.defaultRules;
import static com.yongdrel.fraudriskservice.TestData.tenantRule;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.service;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.student;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD + negative API tests for /api/fraud/rules. */
@WebMvcTest(RiskRuleController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class RiskRuleControllerTest {

    private static final String CREATE = """
            {"tenantId":10,"ruleType":"BUYER_VELOCITY","enabled":true,"threshold":3,
             "windowMinutes":15,"weight":30,"description":"stricter"}
            """;
    private static final String UPDATE = """
            {"enabled":false,"threshold":3,"windowMinutes":15,"weight":30}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean RiskRuleUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void create() throws Exception {
        when(useCase.create(eq(TENANT), eq(RuleType.BUYER_VELOCITY),
                eq(new RuleSettings(true, 3, 15, 30, "stricter")), any()))
                .thenReturn(tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY));

        mvc.perform(post("/api/fraud/rules").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/fraud/rules/9"))
                .andExpect(jsonPath("$.scope").value("TENANT"))
                .andExpect(jsonPath("$.ruleType").value("BUYER_VELOCITY"));
    }

    @Test
    void readList() throws Exception {
        when(useCase.list(isNull(), any())).thenReturn(defaultRules());

        mvc.perform(get("/api/fraud/rules").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].scope").value("GLOBAL"));
    }

    @Test
    void readEffective() throws Exception {
        when(useCase.effective(eq(TENANT), any())).thenReturn(List.of(tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY)));

        mvc.perform(get("/api/fraud/rules/effective").param("tenantId", "10").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9));
    }

    @Test
    void readOne() throws Exception {
        when(useCase.get(eq(9L), any())).thenReturn(tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY));

        mvc.perform(get("/api/fraud/rules/9").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value(10));
    }

    @Test
    void update() throws Exception {
        when(useCase.update(eq(9L), eq(new RuleSettings(false, 3, 15, 30, null)), any()))
                .thenReturn(tenantRule(9L, TENANT, RuleType.BUYER_VELOCITY));

        mvc.perform(put("/api/fraud/rules/9").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        mvc.perform(delete("/api/fraud/rules/9").with(organizer()))
                .andExpect(status().isNoContent());
        verify(useCase).delete(eq(9L), any());
    }

    @Test
    void duplicateIs409() throws Exception {
        when(useCase.create(any(), any(), any(), any())).thenThrow(new ConflictException("already exists"));

        mvc.perform(post("/api/fraud/rules").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isConflict());
    }

    @Test
    void domainRuleViolationIs400() throws Exception {
        when(useCase.create(any(), any(), any(), any()))
                .thenThrow(new InvalidRuleException("BUYER_VELOCITY threshold must be at least 1"));

        mvc.perform(post("/api/fraud/rules").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("BUYER_VELOCITY threshold must be at least 1"));
    }

    @Test
    void missingFieldsAre400() throws Exception {
        mvc.perform(post("/api/fraud/rules").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"weight\":500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("ruleType")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("weight")));
        verifyNoInteractions(useCase);
    }

    @Test
    void unknownRuleTypeIs400() throws Exception {
        mvc.perform(post("/api/fraud/rules").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE.replace("BUYER_VELOCITY", "MOON_PHASE")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void organizerOtherTenantIs403() throws Exception {
        when(useCase.create(any(), any(), any(), any()))
                .thenThrow(new ForbiddenOperationException("Tenant 20 does not belong to the caller"));

        mvc.perform(post("/api/fraud/rules").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE.replace("10", "20")))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentsAndServicesCannotTouchRules() throws Exception {
        mvc.perform(get("/api/fraud/rules").with(student())).andExpect(status().isForbidden());
        mvc.perform(post("/api/fraud/rules").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content(CREATE))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void deleteUnknownIs404() throws Exception {
        doThrow(new ResourceNotFoundException("Rule 77 not found")).when(useCase).delete(eq(77L), any());

        mvc.perform(delete("/api/fraud/rules/77").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Rule 77 not found"));
    }
}
