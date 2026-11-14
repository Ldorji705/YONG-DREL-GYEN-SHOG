package com.yongdrel.lotterydraw.adapter.out.ticket;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TicketServiceAdapterTest {

    @Test
    void retrievesEligibleTicketsUsingInternalCredentials()
            throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();

        AtomicReference<String> receivedKey =
                new AtomicReference<>();

        AtomicReference<String> receivedQuery =
                new AtomicReference<>();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext(
                "/internal/tickets/eligible",
                exchange -> {
                    receivedKey.set(
                            exchange.getRequestHeaders()
                                    .getFirst("X-Internal-API-Key")
                    );

                    receivedQuery.set(
                            exchange.getRequestURI().getRawQuery()
                    );

                    byte[] response = (
                            "[\"" + ticketId + "\"]"
                    ).getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json"
                    );

                    exchange.sendResponseHeaders(
                            200, response.length
                    );

                    try (var output =
                                 exchange.getResponseBody()) {
                        output.write(response);
                    }
                }
        );

        server.start();

        try {
            String url = "http://127.0.0.1:"
                    + server.getAddress().getPort();

            TicketServiceAdapter adapter =
                    new TicketServiceAdapter(
                            RestClient.builder(),
                            url,
                            "test-internal-key"
                    );

            List<UUID> tickets =
                    adapter.getEligibleTicketIds(eventId);

            assertEquals(List.of(ticketId), tickets);
            assertEquals(
                    "test-internal-key",
                    receivedKey.get()
            );
            assertEquals(
                    "eventId=" + eventId,
                    receivedQuery.get()
            );

        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsDuplicateTicketIds() throws Exception {

        UUID ticketId = UUID.randomUUID();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext(
                "/internal/tickets/eligible",
                exchange -> {
                    byte[] response = (
                            "[\"" + ticketId + "\",\""
                                    + ticketId + "\"]"
                    ).getBytes(StandardCharsets.UTF_8);

                    exchange.getResponseHeaders().set(
                            "Content-Type", "application/json"
                    );

                    exchange.sendResponseHeaders(
                            200, response.length
                    );

                    try (var output =
                                 exchange.getResponseBody()) {
                        output.write(response);
                    }
                }
        );

        server.start();

        try {
            String url = "http://127.0.0.1:"
                    + server.getAddress().getPort();

            TicketServiceAdapter adapter =
                    new TicketServiceAdapter(
                            RestClient.builder(),
                            url,
                            "test-internal-key"
                    );

            assertThrows(
                    ResponseStatusException.class,
                    () -> adapter.getEligibleTicketIds(
                            UUID.randomUUID()
                    )
            );

        } finally {
            server.stop(0);
        }
    }
}
