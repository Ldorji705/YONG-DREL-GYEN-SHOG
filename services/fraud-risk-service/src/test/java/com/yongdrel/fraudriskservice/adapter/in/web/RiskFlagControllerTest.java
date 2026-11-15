package com.yongdrel.fraudriskservice.adapter.in.web;

import com.yongdrel.fraudriskservice.application.exception.ResourceNotFoundException;
import com.yongdrel.fraudriskservice.application.model.FlagSearchCriteria;
import com.yongdrel.fraudriskservice.application.model.PageResult;
import com.yongdrel.fraudriskservice.application.port.in.RiskFlagUseCase;
import com.yongdrel.fraudriskservice.config.SecurityConfig;
import com.yongdrel.fraudriskservice.domain.exception.InvalidFlagTransitionException;
import com.yongdrel.fraudriskservice.domain.model.FlagStatus;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import com.yongdrel.fraudriskservice.domain.model.RiskLevel;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static com.yongdrel.fraudriskservice.TestData.NOW;
import static com.yongdrel.fraudriskservice.TestData.TENANT;
import static com.yongdrel.fraudriskservice.TestData.openFlag;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.service;
import static com.yongdrel.fraudriskservice.adapter.in.web.WebTestSupport.student;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskFlagController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class RiskFlagControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RiskFlagUseCase useCase;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void listPassesFiltersAndPaging() throws Exception {
        when(useCase.search(any(), eq(1), eq(5), any()))
                .thenReturn(new PageResult<>(List.of(openFlag(1L, TENANT)), 1, 5, 6));

        mvc.perform(get("/api/fraud/flags")
                        .param("status", "OPEN").param("riskLevel", "HIGH").param("eventId", "5")
                        .param("page", "1").param("size", "5")
                        .with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].reasons[0]").value("DUPLICATE_PAYMENT_REFERENCE"))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));

        verify(useCase).search(eq(new FlagSearchCriteria(null, 5L, null, null, FlagStatus.OPEN, RiskLevel.HIGH)),
                eq(1), eq(5), any());
    }

    @Test
    void serviceCanListFlagsOfAnOrder() throws Exception {
        when(useCase.search(any(), anyInt(), anyInt(), any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mvc.perform(get("/api/fraud/flags").param("orderId", "1000").with(service()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void invalidPagingIs400() throws Exception {
        mvc.perform(get("/api/fraud/flags").param("size", "500").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("size")));
        verifyNoInteractions(useCase);
    }

    @Test
    void invalidStatusIs400() throws Exception {
        mvc.perform(get("/api/fraud/flags").param("status", "MAYBE").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Parameter 'status' has invalid value 'MAYBE'"));
    }

    @Test
    void studentCannotListFlags() throws Exception {
        mvc.perform(get("/api/fraud/flags").with(student())).andExpect(status().isForbidden());
    }

    @Test
    void noTokenIs401() throws Exception {
        mvc.perform(get("/api/fraud/flags")).andExpect(status().isUnauthorized());
    }

    @Test
    void getFlag() throws Exception {
        when(useCase.get(eq(1L), any())).thenReturn(openFlag(1L, TENANT));

        mvc.perform(get("/api/fraud/flags/1").with(organizer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getFlagOfOtherTenantIs404() throws Exception {
        when(useCase.get(eq(1L), any())).thenThrow(new ResourceNotFoundException("Flag 1 not found"));

        mvc.perform(get("/api/fraud/flags/1").with(organizer())).andExpect(status().isNotFound());
    }

    @Test
    void reviewFlag() throws Exception {
        RiskFlag reviewed = openFlag(1L, TENANT);
        reviewed.review(FlagStatus.CONFIRMED_FRAUD, 3L, "Same UPI ref used twice", NOW);
        when(useCase.review(eq(1L), eq(FlagStatus.CONFIRMED_FRAUD), eq("Same UPI ref used twice"), any()))
                .thenReturn(reviewed);

        mvc.perform(patch("/api/fraud/flags/1/review").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED_FRAUD\",\"note\":\"Same UPI ref used twice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_FRAUD"))
                .andExpect(jsonPath("$.reviewerId").value(3));
    }

    @Test
    void closingWithoutNoteIs400() throws Exception {
        mvc.perform(patch("/api/fraud/flags/1/review").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("note is required when confirming or dismissing a flag"));
        verifyNoInteractions(useCase);
    }

    @Test
    void reopeningIs400() throws Exception {
        mvc.perform(patch("/api/fraud/flags/1/review").with(organizer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTransitionIs409() throws Exception {
        when(useCase.review(anyLong(), any(), any(), any()))
                .thenThrow(new InvalidFlagTransitionException(FlagStatus.DISMISSED, FlagStatus.CONFIRMED_FRAUD));

        mvc.perform(patch("/api/fraud/flags/1/review").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED_FRAUD\",\"note\":\"late\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Flag cannot move from DISMISSED to CONFIRMED_FRAUD"));
    }

    @Test
    void concurrentReviewIs409() throws Exception {
        when(useCase.review(anyLong(), any(), any(), any()))
                .thenThrow(new OptimisticLockingFailureException("stale"));

        mvc.perform(patch("/api/fraud/flags/1/review").with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void serviceCannotReview() throws Exception {
        mvc.perform(patch("/api/fraud/flags/1/review").with(service())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"UNDER_REVIEW\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    void wrongMethodIs405() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/fraud/flags/1")
                        .with(admin()))
                .andExpect(status().isMethodNotAllowed());
    }
}
