package com.yongdrel.payment_service.application.port;

public interface EventOwnershipVerificationPort {

    boolean ownsEvent(EventOwnershipVerificationRequest request);
}
