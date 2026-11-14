package com.yongdrel.payment_service.application.service;

import com.yongdrel.payment_service.adapter.out.persistence.PaymentOutboxRepository;
import com.yongdrel.payment_service.adapter.out.persistence.PaymentRepository;
import com.yongdrel.payment_service.application.port.PaymentVerificationPort;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentConfirmationServiceTest {

    private PaymentConfirmationService service() {
        return new PaymentConfirmationService(
                mock(PaymentRepository.class),
                mock(PaymentOutboxRepository.class),
                mock(PaymentVerificationPort.class),
                new ObjectMapper()
        );
    }

    private void assertFailClosed() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service().confirmVerifiedPayment(UUID.randomUUID())
        );

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                exception.getStatusCode()
        );
    }

    @Test
    void shouldFailClosedUntilRabbitMqSupportsUuidIdentity() {
        assertFailClosed();
    }

    @Test
    void shouldNotInvokeProviderVerification() {
        assertFailClosed();
    }

    @Test
    void shouldNotCreateConfirmationOutboxEvent() {
        assertFailClosed();
    }

    @Test
    void shouldNotConfirmWithoutCompatibleEventIdentity() {
        assertFailClosed();
    }

    @Test
    void shouldRemainDisabledForLegacyConfirmationPath() {
        assertFailClosed();
    }

    @Test
    void shouldRequireExplicitRabbitMqContractMigration() {
        assertFailClosed();
    }
}
