package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.application.exception.ConflictException;
import com.yongdrel.reportingservice.application.exception.ForbiddenOperationException;
import com.yongdrel.reportingservice.application.exception.ResourceNotFoundException;
import com.yongdrel.reportingservice.application.model.SavedReportCommand;
import com.yongdrel.reportingservice.application.port.in.SavedReportUseCase;
import com.yongdrel.reportingservice.config.SecurityConfig;
import com.yongdrel.reportingservice.domain.model.ReportType;
import com.yongdrel.reportingservice.domain.model.SavedReport;
import com.yongdrel.reportingservice.domain.model.Summaries;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.student;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

/** CRUD + negative API tests for saved reports. */
@WebMvcTest(SavedReportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class SavedReportControllerTest {

    static final String BODY = "{\"name\":\"Weekly sales\",\"reportType\":\"SALES\",\"from\":\"2026-11-01T00:00:00Z\",\"to\":\"2026-11-08T00:00:00Z\"}";
    static final Instant T0 = Instant.parse("2026-11-01T00:00:00Z");

    @Autowired MockMvc mvc;
    @MockitoBean SavedReportUseCase saved;
    @MockitoBean JwtDecoder jwtDecoder;

    private static SavedReport report() {
        return new SavedReport(1L, 10L, 3L, "Weekly sales", ReportType.SALES, null, T0, T0.plusSeconds(604_800), T0, T0, 0L);
    }

    @Test
    void create() throws Exception {
        when(saved.create(any(), any())).thenReturn(report());

        mvc.perform(post("/api/reports/saved").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/reports/saved/1"))
                .andExpect(jsonPath("$.ownerId").value(3));
        verify(saved).create(eq(new SavedReportCommand("Weekly sales", ReportType.SALES, null, T0, T0.plusSeconds(604_800))), any());
    }

    @Test
    void readListUpdateDeleteRun() throws Exception {
        when(saved.list(any())).thenReturn(List.of(report()));
        when(saved.get(eq(1L), any())).thenReturn(report());
        when(saved.update(eq(1L), any(), any())).thenReturn(report());
        when(saved.run(eq(1L), any())).thenReturn(new Summaries.TicketSummary(100, 3, 1, 96, new BigDecimal("3.00")));

        mvc.perform(get("/api/reports/saved").with(organizer())).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(1));
        mvc.perform(get("/api/reports/saved/1").with(organizer())).andExpect(status().isOk());
        mvc.perform(put("/api/reports/saved/1").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        mvc.perform(get("/api/reports/saved/1/run").with(organizer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(96));
        mvc.perform(delete("/api/reports/saved/1").with(organizer())).andExpect(status().isNoContent());
        verify(saved).delete(eq(1L), any());
    }

    @Test
    void duplicateNameIs409() throws Exception {
        when(saved.create(any(), any())).thenThrow(new ConflictException("A saved report named 'Weekly sales' already exists"));
        mvc.perform(post("/api/reports/saved").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void concurrentEditIs409() throws Exception {
        when(saved.update(eq(1L), any(), any())).thenThrow(new OptimisticLockingFailureException("stale"));
        mvc.perform(put("/api/reports/saved/1").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    void notOwnerIs403AndOtherTenantIs404() throws Exception {
        doThrow(new ForbiddenOperationException("Only the organizer who created saved report 1 can change it"))
                .when(saved).delete(eq(1L), any());
        when(saved.get(eq(2L), any())).thenThrow(new ResourceNotFoundException("Saved report 2 not found"));

        mvc.perform(delete("/api/reports/saved/1").with(organizer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/saved/2").with(organizer())).andExpect(status().isNotFound());
    }

    @Test
    void validationIs400() throws Exception {
        mvc.perform(post("/api/reports/saved").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"eventId\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("name")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("reportType")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("eventId")));
        mvc.perform(post("/api/reports/saved").with(organizer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"reportType\":\"PIE_CHART\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(saved);
    }

    @Test
    void onlyOrganizers() throws Exception {
        mvc.perform(get("/api/reports/saved").with(admin())).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/saved").with(student())).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/saved")).andExpect(status().isUnauthorized());
        verifyNoInteractions(saved);
    }
}
