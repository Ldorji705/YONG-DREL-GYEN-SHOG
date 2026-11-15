package com.yongdrel.reportingservice.domain.model;

/** Lifecycle of a lottery event as reported by the Event Service. */
public enum EventStatus {
    DRAFT, OPEN, CLOSED, DRAWN, CANCELLED
}
