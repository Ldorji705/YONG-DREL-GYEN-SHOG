package com.yongdrel.payment_service.adapter.out.order;

import com.yongdrel.payment_service.application.port.OrderServicePort;
import com.yongdrel.payment_service.application.port.TrustedOrder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class OrderServiceAdapter implements OrderServicePort {
    private final RestClient client; private final String key;
    public OrderServiceAdapter(RestClient.Builder b,@Value("${app.services.order-url}")String url,@Value("${app.internal.order-api-key}")String key){if(key==null||key.isBlank())throw new IllegalStateException("Order trust key required");client=b.baseUrl(url).build();this.key=key;}

    @Override
    public TrustedOrder getOrder(UUID orderId) {

        try {
            OrderDetails d=client.get().uri("/internal/orders/{id}/payment-details",orderId)
                    .header("X-Internal-API-Key",key).retrieve().body(OrderDetails.class);
            return d==null?null:new TrustedOrder(d.orderId(),d.eventId(),d.userId(),d.quantity(),d.amount(),d.currency(),d.status(),d.tenantId());
        } catch(RestClientException ex){return null;}
    }
    private record OrderDetails(UUID orderId,UUID tenantId,UUID userId,UUID eventId,int quantity,String status,java.math.BigDecimal amount,String currency){}
}
