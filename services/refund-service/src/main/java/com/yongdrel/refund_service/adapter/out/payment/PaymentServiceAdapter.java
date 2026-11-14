package com.yongdrel.refund_service.adapter.out.payment;

import com.yongdrel.refund_service.application.exception.DependencyUnavailableException;
import com.yongdrel.refund_service.application.exception.PaymentNotFoundException;
import com.yongdrel.refund_service.application.port.PaymentLookupPort;
import com.yongdrel.refund_service.application.port.PaymentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.UUID;

/** Calls GET /api/payments/{id} on the Payment Service with the user's own JWT. */
@Component
public class PaymentServiceAdapter implements PaymentLookupPort {

    /** Only the fields we need from Payment's PaymentResponse. */
    record PaymentResponseBody(UUID id, UUID orderId, UUID userId,
                               BigDecimal amount, String currency, String status) {
    }

    private final RestClient restClient;

    public PaymentServiceAdapter(@Value("${app.payment.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public PaymentSnapshot getPayment(UUID paymentId, String bearerToken) {
        try {
            PaymentResponseBody body = restClient.get()
                    .uri("/api/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + bearerToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        // 403 / 404: not found or not the caller's payment. Treated the same.
                        throw new PaymentNotFoundException("Payment " + paymentId + " not found");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new DependencyUnavailableException("Payment Service error " + res.getStatusCode());
                    })
                    .body(PaymentResponseBody.class);

            if (body == null) {
                throw new DependencyUnavailableException("Payment Service returned no body");
            }
            return new PaymentSnapshot(body.id(), body.orderId(),
                    body.userId() == null ? null : body.userId().toString(),
                    body.amount(), body.currency(), body.status());

        } catch (ResourceAccessException e) {
            throw new DependencyUnavailableException("Payment Service unreachable");
        }
    }
}
