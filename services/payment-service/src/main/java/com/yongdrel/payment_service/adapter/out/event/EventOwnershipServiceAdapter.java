package com.yongdrel.payment_service.adapter.out.event;

import com.yongdrel.payment_service.application.port.EventOwnershipVerificationPort;
import com.yongdrel.payment_service.application.port.EventOwnershipVerificationRequest;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.UUID;

@Component
public class EventOwnershipServiceAdapter
        implements EventOwnershipVerificationPort {

    private final RestClient client;
    private final String apiKey;

    public EventOwnershipServiceAdapter(
            RestClient.Builder builder,
            @Value("${app.services.event-url}") String url,
            @Value("${app.internal.event-api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Event trust key is required");
        }
        client = builder.baseUrl(url).build();
        this.apiKey = apiKey;
    }

    @Override
    public boolean ownsEvent(EventOwnershipVerificationRequest request) {
        if (request.tenantId() == null) {
            return false;
        }
        try {
            OwnershipResponse response = client.get()
                    .uri(uri -> uri.path("/internal/events/{id}/organizer-ownership")
                            .queryParam("organizerId", request.organizerId())
                            .queryParam("tenantId", request.tenantId())
                            .build(request.eventId()))
                    .header("X-Internal-API-Key", apiKey)
                    .retrieve()
                    .body(OwnershipResponse.class);
            return response != null && response.owned();
        } catch (RestClientException exception) {
            return false;
        }
    }

    private record OwnershipResponse(boolean owned) {}
}
