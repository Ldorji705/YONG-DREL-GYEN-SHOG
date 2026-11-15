package com.yongdrel.fraudriskservice.adapter.out.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.yongdrel.fraudriskservice.application.exception.DependencyUnavailableException;
import com.yongdrel.fraudriskservice.application.model.OrderSnapshot;
import com.yongdrel.fraudriskservice.application.port.out.OrderReferencePort;
import com.yongdrel.fraudriskservice.config.OrderServiceProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Optional;

@Component
public class OrderServiceReferenceAdapter implements OrderReferencePort {
    private final OrderServiceProperties properties;
    private final RestClient client;

    public OrderServiceReferenceAdapter(OrderServiceProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = builder.baseUrl(properties.baseUrl()).build();
    }

    @Override
    public boolean isEnabled() {
        return properties.enabled();
    }

    @Override
    public Optional<OrderSnapshot> findOrder(Long orderId) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        try {
            return client.get().uri("/api/orders/{id}", orderId).exchange((request, response) -> {
                HttpStatusCode status = response.getStatusCode();
                if (status.value() == 404) {
                    return Optional.empty();
                }
                if (!status.is2xxSuccessful()) {
                    throw new DependencyUnavailableException("Order Service returned HTTP " + status.value());
                }
                return Optional.ofNullable(response.bodyTo(OrderSnapshotResponse.class))
                        .map(OrderSnapshotResponse::toDomain);
            });
        } catch (DependencyUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DependencyUnavailableException("Order Service is unavailable");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OrderSnapshotResponse(Long id, Long tenantId, Long eventId, Long buyerId, String status) {
        OrderSnapshot toDomain() {
            return new OrderSnapshot(id, tenantId, eventId, buyerId, status);
        }
    }
}
