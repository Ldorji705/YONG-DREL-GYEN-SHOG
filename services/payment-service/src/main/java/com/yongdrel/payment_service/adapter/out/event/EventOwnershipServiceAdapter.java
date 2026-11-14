package com.yongdrel.payment_service.adapter.out.event;

import com.yongdrel.payment_service.application.port.EventOwnershipVerificationPort;
import com.yongdrel.payment_service.application.port.EventOwnershipVerificationRequest;

import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EventOwnershipServiceAdapter
        implements EventOwnershipVerificationPort {

    @Override
    public boolean ownsEvent(EventOwnershipVerificationRequest request) {
        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Event ownership verification is not configured"
        );
    }
}
