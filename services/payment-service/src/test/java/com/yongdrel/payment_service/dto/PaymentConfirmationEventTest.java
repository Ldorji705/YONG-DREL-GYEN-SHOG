package com.yongdrel.payment_service.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentConfirmationEventTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesUuidUserIdentityWithoutChangingEventFields() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID lotteryEventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PaymentConfirmationEvent event = new PaymentConfirmationEvent(
                eventId,
                paymentId,
                orderId,
                lotteryEventId,
                userId,
                2,
                LocalDateTime.of(2026, 11, 14, 1, 25)
        );

        String payload = objectMapper.writeValueAsString(event);

        assertTrue(payload.contains("\"eventId\":\"" + eventId + "\""));
        assertTrue(payload.contains("\"paymentId\":\"" + paymentId + "\""));
        assertTrue(payload.contains("\"orderId\":\"" + orderId + "\""));
        assertTrue(payload.contains(
                "\"lotteryEventId\":\"" + lotteryEventId + "\""
        ));
        assertTrue(payload.contains("\"userId\":\"" + userId + "\""));
        assertTrue(payload.contains("\"quantity\":2"));
        assertTrue(payload.contains("\"confirmedAt\""));
    }

    @Test
    void deserializesUuidUserIdentity() {
        UUID userId = UUID.randomUUID();
        String payload = """
                {
                  "eventId": "%s",
                  "paymentId": "%s",
                  "orderId": "%s",
                  "lotteryEventId": "%s",
                  "userId": "%s",
                  "quantity": 1,
                  "confirmedAt": "2026-11-14T01:25:00"
                }
                """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                userId
        );

        PaymentConfirmationEvent event =
                objectMapper.readValue(
                        payload,
                        PaymentConfirmationEvent.class
                );

        assertEquals(userId, event.userId());
    }
}
