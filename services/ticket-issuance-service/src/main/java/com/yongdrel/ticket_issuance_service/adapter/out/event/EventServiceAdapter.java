package com.yongdrel.ticket_issuance_service.adapter.out.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class EventServiceAdapter {

    private final RestClient client;

    public EventServiceAdapter(
            @Value("${app.services.event-url}") String url,
            @Value("${app.internal.event-api-key}") String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Event internal API key is required"
            );
        }

        this.client = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("X-Internal-API-Key", apiKey)
                .build();
    }

    public record EventInfo(
            UUID id,
            String status,
            LocalDateTime endDate
    ) {}

    public EventInfo getEvent(UUID eventId) {

        try {
            EventInfo event = client.get()
                    .uri("/internal/events/{id}", eventId)
                    .retrieve()
                    .body(EventInfo.class);

            if (event == null
                    || !eventId.equals(event.id())
                    || event.status() == null
                    || event.endDate() == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Invalid Event Service response"
                );
            }

            return event;

        } catch (ResponseStatusException exception) {
            throw exception;

        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Unable to retrieve event information",
                    exception
            );
        }
    }
}
