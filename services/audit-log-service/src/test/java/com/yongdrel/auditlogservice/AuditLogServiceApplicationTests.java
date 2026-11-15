package com.yongdrel.auditlogservice;

import com.yongdrel.auditlogservice.adapter.in.messaging.RabbitMessagingConfig;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Whole service on real MySQL + RabbitMQ: health, REST recording, consuming events, restricted search. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class AuditLogServiceApplicationTests {

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

    private static void publishRaw(RabbitTemplate template, String exchange, String key, String json) {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        template.send(exchange, key, MessageBuilder.withBody(json.getBytes(StandardCharsets.UTF_8))
                .andProperties(props).build());
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
    void recordViaRestThenSearchWithRestrictions() throws Exception {
        String body = """
                {"eventId":"%s","tenantId":10,"actorType":"USER","actorId":100,"actorRole":"STUDENT",
                 "action":"PAYMENT_PROOF_UPLOADED","outcome":"SUCCESS","targetType":"ORDER","targetId":"5001",
                 "sourceService":"payment-proof-service","correlationId":"it-checkout-5001",
                 "occurredAt":"%s","details":{"amount":100}}
                """.formatted(UUID.randomUUID(), Instant.now().minusSeconds(1));

        mvc.perform(post("/api/audit/events").with(as("2", "SERVICE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.integrityValid").value(true));
        mvc.perform(post("/api/audit/events").with(as("2", "SERVICE", null))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/audit/events").with(as("2", "SERVICE", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace(UUIDless(body), UUID.randomUUID().toString())
                                .replace("payment-proof-service", "unknown-service")))
                .andExpect(status().isUnprocessableEntity());

        mvc.perform(get("/api/audit/events").param("correlationId", "it-checkout-5001").with(as("3", "ORGANIZER", 10L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/audit/events").param("correlationId", "it-checkout-5001").with(as("4", "ORGANIZER", 20L)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/audit/events").param("correlationId", "it-checkout-5001").with(as("100", "STUDENT", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/audit/correlations/it-checkout-5001").with(as("101", "STUDENT", null)))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/audit/events/1").with(as("1", "ADMIN", null)))
                .andExpect(status().isMethodNotAllowed());
    }

    private static String UUIDless(String body) {
        return body.substring(body.indexOf("\"eventId\":\"") + 11, body.indexOf("\"eventId\":\"") + 47);
    }

    @Test
    void consumesBusinessAuditEventsAndFraudFlags() throws Exception {
        String eventId = UUID.randomUUID().toString();
        publishRaw(rabbitTemplate, RabbitMessagingConfig.AUDIT_EXCHANGE, "audit.order.cancelled", """
                {"eventId":"%s","tenantId":10,"actorType":"SYSTEM","action":"ORDER_CANCELLED_UNPAID",
                 "outcome":"SUCCESS","targetType":"ORDER","targetId":"6001","sourceService":"scheduler-service",
                 "correlationId":"it-broker-6001","occurredAt":"%s"}
                """.formatted(eventId, Instant.now().minusSeconds(1)));
        // redelivery of the same event is ignored
        publishRaw(rabbitTemplate, RabbitMessagingConfig.AUDIT_EXCHANGE, "audit.order.cancelled", """
                {"eventId":"%s","tenantId":10,"actorType":"SYSTEM","action":"ORDER_CANCELLED_UNPAID",
                 "outcome":"SUCCESS","targetType":"ORDER","targetId":"6001","sourceService":"scheduler-service",
                 "correlationId":"it-broker-6001","occurredAt":"%s"}
                """.formatted(eventId, Instant.now().minusSeconds(1)));
        // flag event in the Fraud & Risk Service's format, with its Java type header
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        props.setHeader("__TypeId__", "com.yongdrel.fraudriskservice.adapter.out.messaging.FlagEvent");
        rabbitTemplate.send(RabbitMessagingConfig.FRAUD_EXCHANGE, "fraud.flag.raised", MessageBuilder.withBody("""
                {"eventType":"FLAG_RAISED","flagId":77,"checkId":2,"tenantId":10,"eventId":5,"orderId":6002,
                 "buyerId":202,"score":100,"riskLevel":"HIGH","reasons":["DUPLICATE_PAYMENT_REFERENCE"],
                 "status":"OPEN","reviewerId":null,"occurredAt":"%s"}
                """.formatted(Instant.now().minusSeconds(1)).getBytes(StandardCharsets.UTF_8)).andProperties(props).build());
        // invalid event goes to the dead-letter queue
        publishRaw(rabbitTemplate, RabbitMessagingConfig.AUDIT_EXCHANGE, "audit.bad", "{\"eventId\":\"" + UUID.randomUUID()
                + "\",\"action\":\"lower case\"}");

        RequestPostProcessor admin = as("1", "ADMIN", null);
        await(() -> mvc.perform(get("/api/audit/events").param("correlationId", "it-broker-6001").with(admin))
                .andReturn().getResponse().getContentAsString().contains("\"totalElements\":1"));
        await(() -> mvc.perform(get("/api/audit/events").param("action", "FRAUD_FLAG_RAISED").with(admin))
                .andReturn().getResponse().getContentAsString().contains("\"targetId\":\"77\""));

        Message dead = rabbitTemplate.receive(RabbitMessagingConfig.DLQ, 10_000);
        assertThat(dead).as("invalid message dead-lettered").isNotNull();

        Thread.sleep(500); // give the redelivered duplicate time to be (not) stored
        mvc.perform(get("/api/audit/events").param("correlationId", "it-broker-6001").with(admin))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/audit/correlations/fraud-flag-77").with(as("3", "ORGANIZER", 10L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceService").value("fraud-risk-service"))
                .andExpect(jsonPath("$[0].details.orderId").value(6002));
    }
}
