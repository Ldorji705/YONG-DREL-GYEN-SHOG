package com.yongdrel.reportingservice.domain.model;

/** The four order states from the proposal. */
public enum OrderStatus {
    AWAITING_PAYMENT, AWAITING_VERIFICATION, CONFIRMED, FAILED;

    public boolean isPending() {
        return this == AWAITING_PAYMENT || this == AWAITING_VERIFICATION;
    }
}
