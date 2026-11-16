package com.yongdrel.payment_service.adapter.out.proof;

import com.yongdrel.payment_service.application.port.PaymentProofValidationPort;
import com.yongdrel.payment_service.application.port.PaymentProofValidationRequest;
import com.yongdrel.payment_service.application.port.PaymentProofValidationResult;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.util.UUID;

@Component
public class PaymentProofServiceAdapter
        implements PaymentProofValidationPort {

    private final RestClient client;
    private final String apiKey;

    public PaymentProofServiceAdapter(
            RestClient.Builder builder,
            @Value("${app.services.payment-proof-url}") String url,
            @Value("${app.internal.payment-proof-api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Payment proof trust key is required");
        }
        client = builder.baseUrl(url).build();
        this.apiKey = apiKey;
    }

    @Override
    public PaymentProofValidationResult validate(
            PaymentProofValidationRequest request
    ) {
        try {
            ProofMetadata proof = client.get()
                    .uri("/internal/payment-proofs/{id}", request.paymentProofId())
                    .header("X-Internal-API-Key", apiKey)
                    .retrieve()
                    .body(ProofMetadata.class);
            return new PaymentProofValidationResult(
                    proof != null,
                    proof != null && request.studentId().equals(proof.userId()),
                    proof != null && request.orderId().equals(proof.orderId()),
                    proof != null && "SUBMITTED".equals(proof.status()),
                    proof != null && request.paymentId() != null
            );
        } catch (RestClientException exception) {
            return new PaymentProofValidationResult(false, false, false, false, false);
        }
    }

    private record ProofMetadata(
            UUID id, UUID orderId, UUID userId, UUID tenantId, String status,
            String checksum
    ) {}
}
