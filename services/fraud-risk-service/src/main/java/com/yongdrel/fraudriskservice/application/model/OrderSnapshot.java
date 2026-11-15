package com.yongdrel.fraudriskservice.application.model;

/** What the Order Service tells us about an order; null fields are not compared. */
public record OrderSnapshot(Long id, Long tenantId, Long eventId, Long buyerId, String status) {
}
