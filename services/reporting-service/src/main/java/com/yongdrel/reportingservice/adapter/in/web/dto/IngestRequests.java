package com.yongdrel.reportingservice.adapter.in.web.dto;

import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.EventStatus;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.OrderStatus;
import com.yongdrel.reportingservice.domain.model.PayoutProjection;
import com.yongdrel.reportingservice.domain.model.PayoutStatus;
import com.yongdrel.reportingservice.domain.model.PrizeProjection;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Bodies pushed by the owning services (REST PUT /api/reports/ingest/... or RabbitMQ yongdrel.reporting.feed).
 * "id" is the record's id in its owning service; over REST it may be omitted (taken from the path).
 */
public final class IngestRequests {

    private IngestRequests() {
    }

    public record EventIngest(
            @Positive Long id,
            @NotNull @Positive Long tenantId,
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal ticketPrice,
            @NotNull @Min(1) @Max(1_000_000) Integer totalTickets,
            @NotNull EventStatus status,
            Instant salesCloseAt,
            @NotNull Instant sourceUpdatedAt
    ) {
        public EventProjection toProjection(Long effectiveId) {
            return new EventProjection(effectiveId, tenantId, name, ticketPrice, totalTickets, status, salesCloseAt,
                    sourceUpdatedAt);
        }
    }

    public record OrderIngest(
            @Positive Long id,
            @NotNull @Positive Long eventId,
            @NotNull @Positive Long tenantId,
            @NotNull @Positive Long buyerId,
            @NotNull @Min(1) @Max(100) Integer quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @NotNull OrderStatus status,
            @NotNull Instant createdAt,
            Instant confirmedAt,
            @NotNull Instant sourceUpdatedAt
    ) {
        public OrderProjection toProjection(Long effectiveId) {
            return new OrderProjection(effectiveId, eventId, tenantId, buyerId, quantity, amount, status, createdAt,
                    confirmedAt, sourceUpdatedAt);
        }
    }

    public record PrizeIngest(
            @Positive Long id,
            @NotNull @Positive Long eventId,
            @NotNull @Positive Long tenantId,
            @NotNull @Min(1) @Max(50) Integer tier,
            @NotBlank @Size(max = 120) String name,
            @NotNull @Min(1) @Max(1000) Integer quantity,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal unitValue,
            @NotNull Instant sourceUpdatedAt
    ) {
        public PrizeProjection toProjection(Long effectiveId) {
            return new PrizeProjection(effectiveId, eventId, tenantId, tier, name, quantity, unitValue, sourceUpdatedAt);
        }
    }

    public record PayoutIngest(
            @Positive Long id,
            @NotNull @Positive Long eventId,
            @NotNull @Positive Long tenantId,
            @NotNull @Positive Long prizeId,
            @NotNull @Positive Long winnerId,
            @NotNull @DecimalMin("0.00") @DecimalMax("100000000.00") @Digits(integer = 9, fraction = 2) BigDecimal amount,
            @NotNull PayoutStatus status,
            Instant paidAt,
            @NotNull Instant sourceUpdatedAt
    ) {
        public PayoutProjection toProjection(Long effectiveId) {
            return new PayoutProjection(effectiveId, eventId, tenantId, prizeId, winnerId, amount, status, paidAt,
                    sourceUpdatedAt);
        }
    }

    public record IngestResponse(String kind, Long id, String outcome) {
    }
}
