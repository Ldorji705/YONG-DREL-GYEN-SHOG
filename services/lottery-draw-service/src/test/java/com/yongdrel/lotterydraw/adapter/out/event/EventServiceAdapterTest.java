package com.yongdrel.lotterydraw.adapter.out.event;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventServiceAdapterTest {

    private HttpServer server;
    private EventServiceAdapter adapter;
    private String response;
    private int status;
    private String receivedAuthorization;

    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setup() throws Exception {
        response = event("COMPLETED", "owner-123", "2020-01-01T10:00:00");
        status = 200;

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/api/events/", exchange -> {
            receivedAuthorization =
                    exchange.getRequestHeaders().getFirst("Authorization");

            byte[] body = response.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json"
            );

            exchange.sendResponseHeaders(status, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });

        server.start();

        adapter = new EventServiceAdapter(
                RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "Asia/Thimphu"
        );
    }

    @AfterEach
    void cleanup() {
        if (server != null) server.stop(0);
    }

    private String event(String state, String owner, String endDate) {
        return """
                {
                  "id": "%s",
                  "status": "%s",
                  "createdBy": "%s",
                  "endDate": "%s"
                }
                """.formatted(eventId, state, owner, endDate);
    }

    @Test
    void validCompletedEventPasses() {
        assertDoesNotThrow(() ->
                adapter.verifyEligibleForDraw(
                        eventId, "owner-123", "test-token"
                )
        );

        assertEquals("Bearer test-token", receivedAuthorization);
    }

    @Test
    void rejectsDifferentOwner() {
        response = event("COMPLETED", "another-owner",
                "2020-01-01T10:00:00");

        assertStatus(403);
    }

    @Test
    void rejectsActiveEvent() {
        response = event("ACTIVE", "owner-123",
                "2020-01-01T10:00:00");

        assertStatus(409);
    }

    @Test
    void rejectsFutureEndDate() {
        response = event("COMPLETED", "owner-123",
                "2099-01-01T10:00:00");

        assertStatus(409);
    }

    @Test
    void rejectsMissingEvent() {
        status = 404;
        response = "{}";

        assertStatus(404);
    }

    @Test
    void rejectsMissingToken() {
        assertThrows(ResponseStatusException.class, () ->
                adapter.verifyEligibleForDraw(
                        eventId, "owner-123", ""
                )
        );
    }

    private void assertStatus(int expected) {
        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () ->
                        adapter.verifyEligibleForDraw(
                                eventId, "owner-123", "test-token"
                        )
                );

        assertEquals(expected, exception.getStatusCode().value());
    }
}
