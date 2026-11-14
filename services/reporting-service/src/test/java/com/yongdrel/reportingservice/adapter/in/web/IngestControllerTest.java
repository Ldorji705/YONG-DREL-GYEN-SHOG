package com.yongdrel.reportingservice.adapter.in.web;

import com.yongdrel.reportingservice.application.exception.ReferenceValidationException;
import com.yongdrel.reportingservice.application.port.in.IngestUseCase;
import com.yongdrel.reportingservice.config.SecurityConfig;
import com.yongdrel.reportingservice.domain.exception.StaleUpdateException;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;

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

import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.admin;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.organizer;
import static com.yongdrel.reportingservice.adapter.in.web.WebTestSupport.service;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class IngestControllerTest {

    static final String EVENT = """
            {"tenantId":10,"name":"Losar Raffle","ticketPrice":50,"totalTickets":100,"status":"OPEN",
             "sourceUpdatedAt":"2026-11-01T08:00:00Z"}
            """;
    static final String ORDER = """
            {"eventId":5,"tenantId":10,"buyerId":100,"quantity":2,"amount":100,"status":"CONFIRMED",
             "createdAt":"2026-11-01T09:00:00Z","confirmedAt":"2026-11-01T09:10:00Z","sourceUpdatedAt":"2026-11-01T09:10:00Z"}
            """;
    static final String PRIZE = """
            {"eventId":5,"tenantId":10,"tier":1,"name":"Gold coin","quantity":2,"unitValue":500,
             "sourceUpdatedAt":"2026-11-01T08:00:00Z"}
            """;
    static final String PAYOUT = """
            {"eventId":5,"tenantId":10,"prizeId":70,"winnerId":100,"amount":500,"status":"PENDING",
             "sourceUpdatedAt":"2026-11-02T08:00:00Z"}
            """;

    @Autowired MockMvc mvc;
    @MockitoBean IngestUseCase ingest;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void createdIs201UpdatedIs200() throws Exception {
        when(ingest.upsertEvent(any(), any())).thenReturn(UpsertOutcome.CREATED, UpsertOutcome.UNCHANGED);

        mvc.perform(put("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("CREATED"))
                .andExpect(jsonPath("$.kind").value("EVENT"));
        mvc.perform(put("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("UNCHANGED"));

        ArgumentCaptor<EventProjection> captor = ArgumentCaptor.forClass(EventProjection.class);
        verify(ingest, org.mockito.Mockito.times(2)).upsertEvent(captor.capture(), any());
        assertThat(captor.getValue().id()).isEqualTo(5L);
    }

    @Test
    void allFourFeeds() throws Exception {
        when(ingest.upsertOrder(any(), any())).thenReturn(UpsertOutcome.UPDATED);
        when(ingest.upsertPrize(any(), any())).thenReturn(UpsertOutcome.CREATED);
        when(ingest.upsertPayout(any(), any())).thenReturn(UpsertOutcome.CREATED);

        mvc.perform(put("/api/reports/ingest/orders/1").with(admin()).contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isOk());
        mvc.perform(put("/api/reports/ingest/prizes/70").with(service()).contentType(MediaType.APPLICATION_JSON).content(PRIZE))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/reports/ingest/payouts/90").with(service()).contentType(MediaType.APPLICATION_JSON).content(PAYOUT))
                .andExpect(status().isCreated());
    }

    @Test
    void organizersCannotIngest() throws Exception {
        mvc.perform(put("/api/reports/ingest/events/5").with(organizer()).contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ingest);
    }

    @Test
    void staleIs409() throws Exception {
        when(ingest.upsertEvent(any(), any())).thenThrow(new StaleUpdateException("Event 5 update is older than the stored version"));
        mvc.perform(put("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Event 5 update is older than the stored version"));
    }

    @Test
    void unknownEventReferenceIs422() throws Exception {
        when(ingest.upsertOrder(any(), any())).thenThrow(new ReferenceValidationException("Order 1 references unknown event 5"));
        mvc.perform(put("/api/reports/ingest/orders/1").with(service()).contentType(MediaType.APPLICATION_JSON).content(ORDER))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void bodyIdMustMatchPath() throws Exception {
        mvc.perform(put("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content(EVENT.replace("{\"tenantId\"", "{\"id\":6,\"tenantId\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Body id 6 does not match path id 5"));
    }

    @Test
    void domainRuleIs400() throws Exception {
        mvc.perform(put("/api/reports/ingest/orders/1").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content(ORDER.replace("\"confirmedAt\":\"2026-11-01T09:10:00Z\",", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("confirmedAt is required for CONFIRMED orders and only for them"));
    }

    @Test
    void validationErrorsAre400() throws Exception {
        mvc.perform(put("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenantId\":0,\"name\":\"\",\"ticketPrice\":-1,\"totalTickets\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("tenantId")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("ticketPrice")))
                .andExpect(jsonPath("$.fieldErrors[*].field", hasItem("sourceUpdatedAt")));
        verifyNoInteractions(ingest);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"FINISHED\"}", "{\"ticketPrice\":\"ten\"}", "{oops"})
    void unreadableBodiesAre400(String body) throws Exception {
        mvc.perform(put("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void postIs405() throws Exception {
        mvc.perform(post("/api/reports/ingest/events/5").with(service()).contentType(MediaType.APPLICATION_JSON).content(EVENT))
                .andExpect(status().isMethodNotAllowed());
    }
}
