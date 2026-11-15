package com.yongdrel.reportingservice;

import com.yongdrel.reportingservice.adapter.in.messaging.RabbitMessagingConfig;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole service on real MySQL + RabbitMQ: health, REST + broker feed, figures, tenant isolation, saved reports. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class ReportingServiceApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbitTemplate;
    @MockitoBean JwtDecoder jwtDecoder;

    private static RequestPostProcessor as(String sub, String role, Long tenantId) {
        return jwt().jwt(b -> {
            b.subject(sub).claim("role", role);
            if (tenantId != null) {
                b.claim("tenantId", tenantId);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private void putJson(String path, String json, int expected) throws Exception {
        mvc.perform(put(path).with(as("2", "SERVICE", null)).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expected));
    }

    private void publish(String key, String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        rabbitTemplate.send(RabbitMessagingConfig.FEED_EXCHANGE, key,
                MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8)).andProperties(props).build());
    }

    private static void await(Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (condition.call()) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("condition not met within 10 s");
    }

    @Test
    void healthIsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void endToEndFiguresWithTenantIsolation() throws Exception {
        putJson("/api/reports/ingest/events/500", """
                {"tenantId":10,"name":"Losar Raffle","ticketPrice":50,"totalTickets":100,"status":"OPEN",
                 "sourceUpdatedAt":"2026-11-01T08:00:00Z"}""", 201);
        putJson("/api/reports/ingest/events/501", """
                {"tenantId":20,"name":"Other","ticketPrice":10,"totalTickets":10,"status":"OPEN",
                 "sourceUpdatedAt":"2026-11-01T08:00:00Z"}""", 201);
        putJson("/api/reports/ingest/orders/5001", """
                {"eventId":500,"tenantId":10,"buyerId":100,"quantity":2,"amount":100,"status":"AWAITING_VERIFICATION",
                 "createdAt":"2026-11-01T09:00:00Z","sourceUpdatedAt":"2026-11-01T09:05:00Z"}""", 201);
        putJson("/api/reports/ingest/orders/5001", """
                {"eventId":500,"tenantId":10,"buyerId":100,"quantity":2,"amount":100,"status":"CONFIRMED",
                 "createdAt":"2026-11-01T09:00:00Z","confirmedAt":"2026-11-01T09:30:00Z","sourceUpdatedAt":"2026-11-01T09:30:00Z"}""", 200);
        // older copy arrives late -> 409, state stays CONFIRMED
        putJson("/api/reports/ingest/orders/5001", """
                {"eventId":500,"tenantId":10,"buyerId":100,"quantity":2,"amount":100,"status":"AWAITING_VERIFICATION",
                 "createdAt":"2026-11-01T09:00:00Z","sourceUpdatedAt":"2026-11-01T09:05:00Z"}""", 409);
        // cross-service reference: order claims tenant 20 for event of tenant 10 -> 422
        putJson("/api/reports/ingest/orders/5002", """
                {"eventId":500,"tenantId":20,"buyerId":100,"quantity":1,"amount":50,"status":"AWAITING_PAYMENT",
                 "createdAt":"2026-11-01T09:00:00Z","sourceUpdatedAt":"2026-11-01T09:00:00Z"}""", 422);

        // via RabbitMQ: prize + payout + another confirmed order
        publish("prize.upserted", """
                {"id":700,"eventId":500,"tenantId":10,"tier":1,"name":"Gold coin","quantity":1,"unitValue":60,
                 "sourceUpdatedAt":"2026-11-01T08:00:00Z"}""");
        publish("order.upserted", """
                {"id":5003,"eventId":500,"tenantId":10,"buyerId":101,"quantity":1,"amount":50,"status":"CONFIRMED",
                 "createdAt":"2026-11-02T09:00:00Z","confirmedAt":"2026-11-02T09:10:00Z","sourceUpdatedAt":"2026-11-02T09:10:00Z"}""");
        RequestPostProcessor organizer = as("3", "ORGANIZER", 10L);
        await(() -> mvc.perform(get("/api/reports/prizes").param("eventId", "500").with(organizer))
                .andReturn().getResponse().getContentAsString().contains("Gold coin"));
        publish("payout.upserted", """
                {"id":900,"eventId":500,"tenantId":10,"prizeId":700,"winnerId":101,"amount":60,"status":"PENDING",
                 "sourceUpdatedAt":"2026-11-03T08:00:00Z"}""");
        publish("order.upserted", "{\"id\":5004,\"eventId\":999}"); // invalid -> dead-letter queue
        await(() -> mvc.perform(get("/api/reports/payouts").param("eventId", "500").with(organizer))
                .andReturn().getResponse().getContentAsString().contains("\"winners\":1"));
        await(() -> mvc.perform(get("/api/reports/sales").param("eventId", "500").with(organizer))
                .andReturn().getResponse().getContentAsString().contains("\"confirmedOrders\":2"));
        assertThat(rabbitTemplate.receive(RabbitMessagingConfig.DLQ, 10_000)).isNotNull();

        mvc.perform(get("/api/reports/events/500/summary").with(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.revenue").value(150.00))
                .andExpect(jsonPath("$.sales.daily.length()").value(2))
                .andExpect(jsonPath("$.tickets.sold").value(3))
                .andExpect(jsonPath("$.tickets.available").value(97))
                .andExpect(jsonPath("$.prizes.totalPrizeValue").value(60.00))
                .andExpect(jsonPath("$.payouts.outstandingAmount").value(60.00))
                .andExpect(jsonPath("$.netAfterPrizes").value(90.00));

        // tenant isolation
        mvc.perform(get("/api/reports/events/500/summary").with(as("4", "ORGANIZER", 20L))).andExpect(status().isNotFound());
        mvc.perform(get("/api/reports/overview").with(as("4", "ORGANIZER", 20L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sales.revenue").value(0.00))
                .andExpect(jsonPath("$.tickets.capacity").value(10));
        mvc.perform(get("/api/reports/overview").param("tenantId", "10").with(as("4", "ORGANIZER", 20L)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/me/purchases").with(as("100", "STUDENT", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedTickets").value(2));

        // saved report CRUD + run + duplicate name
        mvc.perform(post("/api/reports/saved").with(organizer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Losar tickets\",\"reportType\":\"TICKETS\",\"eventId\":500}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/reports/saved").with(organizer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"losar TICKETS\",\"reportType\":\"SALES\"}"))
                .andExpect(status().isConflict());
        String list = mvc.perform(get("/api/reports/saved").with(organizer)).andReturn().getResponse().getContentAsString();
        String id = list.replaceAll("^\\[\\{\"id\":(\\d+).*", "$1");
        mvc.perform(get("/api/reports/saved/" + id + "/run").with(organizer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sold").value(3));
        mvc.perform(get("/api/reports/saved/" + id).with(as("4", "ORGANIZER", 20L))).andExpect(status().isNotFound());
    }
}
