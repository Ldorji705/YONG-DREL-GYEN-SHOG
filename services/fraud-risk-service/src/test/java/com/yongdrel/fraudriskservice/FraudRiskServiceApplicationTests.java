package com.yongdrel.fraudriskservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yongdrel.fraudriskservice.adapter.out.messaging.RabbitMessagingConfig;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Whole service against real MySQL + RabbitMQ: health endpoint, duplicate detection,
 * manual review and the published flag events. Skipped when Docker is not running.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class FraudRiskServiceApplicationTests {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3-management");

    @Autowired MockMvc mvc;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean JwtDecoder jwtDecoder;

    private static RequestPostProcessor as(String sub, String role, Long tenantId) {
        return jwt().jwt(b -> {
            b.subject(sub).claim("role", role);
            if (tenantId != null) {
                b.claim("tenantId", tenantId);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private static String body(long orderId, long buyerId, String ref) {
        return """
                {"tenantId":10,"eventId":5,"orderId":%d,"buyerId":%d,"paymentReference":"%s",
                 "amount":100.00,"expectedAmount":100.00}
                """.formatted(orderId, buyerId, ref);
    }

    @Test
    void healthEndpointIsUpWithoutToken() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void duplicateReferenceFlowFromCheckToReview() throws Exception {
        RequestPostProcessor service = as("2", "SERVICE", null);
        RequestPostProcessor organizer = as("3", "ORGANIZER", 10L);

        // 1st use of the reference: clean
        mvc.perform(post("/api/fraud/checks").with(service)
                        .contentType(MediaType.APPLICATION_JSON).content(body(5001, 201, "upi-777-888")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("CLEAR"));

        // identical replay is idempotent
        mvc.perform(post("/api/fraud/checks").with(service)
                        .contentType(MediaType.APPLICATION_JSON).content(body(5001, 201, "upi-777-888")))
                .andExpect(status().isOk());

        // same reference (different formatting) on another order by another buyer: flagged
        String response = mvc.perform(post("/api/fraud/checks").with(service)
                        .contentType(MediaType.APPLICATION_JSON).content(body(5002, 202, "UPI777888")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.riskLevel").value("HIGH"))
                .andReturn().getResponse().getContentAsString();
        long flagId = objectMapper.readTree(response).get("flagId").asLong();

        JsonNode raised = receive();
        assertThat(raised.get("eventType").asText()).isEqualTo("FLAG_RAISED");
        assertThat(raised.get("flagId").asLong()).isEqualTo(flagId);

        // organizer of another tenant cannot see it
        mvc.perform(get("/api/fraud/flags/" + flagId).with(as("4", "ORGANIZER", 20L)))
                .andExpect(status().isNotFound());

        // owning organizer sees it in the review queue and confirms fraud
        mvc.perform(get("/api/fraud/flags").param("status", "OPEN").with(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(flagId));

        mvc.perform(patch("/api/fraud/flags/" + flagId + "/review").with(organizer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED_FRAUD\",\"note\":\"Reference reused from order 5001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED_FRAUD"));

        assertThat(receive().get("eventType").asText()).isEqualTo("FLAG_REVIEWED");

        // a closed flag cannot be reviewed again
        mvc.perform(patch("/api/fraud/flags/" + flagId + "/review").with(organizer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISMISSED\",\"note\":\"changed mind\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void organizerOverridesDefaultRule() throws Exception {
        RequestPostProcessor organizer = as("3", "ORGANIZER", 10L);

        mvc.perform(post("/api/fraud/rules").with(organizer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":10,"ruleType":"AMOUNT_MISMATCH","enabled":true,
                                 "threshold":5,"windowMinutes":0,"weight":40}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/fraud/rules/effective").with(organizer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[?(@.ruleType=='AMOUNT_MISMATCH')].scope").value("TENANT"));

        mvc.perform(post("/api/fraud/rules").with(organizer).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":10,"ruleType":"AMOUNT_MISMATCH","enabled":true,
                                 "threshold":5,"windowMinutes":0,"weight":40}
                                """))
                .andExpect(status().isConflict());
    }

    private JsonNode receive() throws Exception {
        Message message = rabbitTemplate.receive(RabbitMessagingConfig.REVIEW_QUEUE, 5000);
        assertThat(message).as("event on " + RabbitMessagingConfig.REVIEW_QUEUE).isNotNull();
        return objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    }
}
