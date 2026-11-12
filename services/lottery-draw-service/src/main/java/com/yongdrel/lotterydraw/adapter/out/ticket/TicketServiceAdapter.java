package com.yongdrel.lotterydraw.adapter.out.ticket;

import com.yongdrel.lotterydraw.application.port.EligibleTicketPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Component
public class TicketServiceAdapter implements EligibleTicketPort {

    private final RestClient client;
    private final String apiKey;

    public TicketServiceAdapter(
            RestClient.Builder builder,
            @Value("${app.services.ticket-url}") String ticketUrl,
            @Value("${app.internal.ticket-api-key}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Ticket API key must be configured"
            );
        }

        this.client = builder.baseUrl(ticketUrl).build();
        this.apiKey = apiKey;
    }

    @Override
    public List<UUID> getEligibleTicketIds(UUID eventId) {
        try {
            UUID[] tickets = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/internal/tickets/eligible")
                            .queryParam("eventId", eventId)
                            .build())
                    .header("X-Internal-API-Key", apiKey)
                    .retrieve()
                    .body(UUID[].class);

            if (tickets == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Ticket Service returned no response"
                );
            }

            List<UUID> result = Arrays.asList(tickets);

            if (result.stream().anyMatch(id -> id == null)
                    || result.stream().distinct().count() != result.size()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Ticket Service returned invalid ticket IDs"
                );
            }

            return result;

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Ticket Service request failed",
                    exception
            );
        }
    }
}
