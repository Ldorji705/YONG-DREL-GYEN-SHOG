package com.yongdrel.reportingservice.application.service;

import com.yongdrel.reportingservice.application.exception.ReferenceValidationException;
import com.yongdrel.reportingservice.application.port.in.IngestUseCase;
import com.yongdrel.reportingservice.application.port.out.ProjectionRepositoryPort;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.EventProjection;
import com.yongdrel.reportingservice.domain.model.OrderProjection;
import com.yongdrel.reportingservice.domain.model.PayoutProjection;
import com.yongdrel.reportingservice.domain.model.PrizeProjection;
import com.yongdrel.reportingservice.domain.model.UpsertOutcome;
import com.yongdrel.reportingservice.domain.service.UpsertPolicy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Applies changes pushed by the owning services. Cross-service references are checked against the read model:
 * an order/prize/payout must point at a known event of the same tenant; a payout at a prize of that event.
 */
@Service
@Transactional
public class IngestService implements IngestUseCase {

    private final ProjectionRepositoryPort projections;

    public IngestService(ProjectionRepositoryPort projections) {
        this.projections = projections;
    }

    @Override
    public UpsertOutcome upsertEvent(EventProjection event, Caller caller) {
        AccessPolicy.requireIngestor(caller);
        UpsertOutcome outcome = UpsertPolicy.decide(projections.findEvent(event.id()).orElse(null), event,
                "Event", EventProjection::tenantId);
        return persist(outcome, () -> projections.save(event));
    }

    @Override
    public UpsertOutcome upsertOrder(OrderProjection order, Caller caller) {
        AccessPolicy.requireIngestor(caller);
        requireEventOfTenant(order.eventId(), order.tenantId(), "Order " + order.id());
        UpsertOutcome outcome = UpsertPolicy.decide(projections.findOrder(order.id()).orElse(null), order,
                "Order", OrderProjection::eventId, OrderProjection::tenantId, OrderProjection::buyerId);
        return persist(outcome, () -> projections.save(order));
    }

    @Override
    public UpsertOutcome upsertPrize(PrizeProjection prize, Caller caller) {
        AccessPolicy.requireIngestor(caller);
        requireEventOfTenant(prize.eventId(), prize.tenantId(), "Prize " + prize.id());
        UpsertOutcome outcome = UpsertPolicy.decide(projections.findPrize(prize.id()).orElse(null), prize,
                "Prize", PrizeProjection::eventId, PrizeProjection::tenantId);
        return persist(outcome, () -> projections.save(prize));
    }

    @Override
    public UpsertOutcome upsertPayout(PayoutProjection payout, Caller caller) {
        AccessPolicy.requireIngestor(caller);
        requireEventOfTenant(payout.eventId(), payout.tenantId(), "Payout " + payout.id());
        PrizeProjection prize = projections.findPrize(payout.prizeId())
                .orElseThrow(() -> new ReferenceValidationException(
                        "Payout " + payout.id() + " references unknown prize " + payout.prizeId()));
        if (!prize.eventId().equals(payout.eventId())) {
            throw new ReferenceValidationException("Prize " + prize.id() + " belongs to event " + prize.eventId()
                    + ", not " + payout.eventId());
        }
        UpsertOutcome outcome = UpsertPolicy.decide(projections.findPayout(payout.id()).orElse(null), payout,
                "Payout", PayoutProjection::eventId, PayoutProjection::tenantId, PayoutProjection::prizeId,
                PayoutProjection::winnerId);
        return persist(outcome, () -> projections.save(payout));
    }

    private void requireEventOfTenant(Long eventId, Long tenantId, String what) {
        EventProjection event = projections.findEvent(eventId)
                .orElseThrow(() -> new ReferenceValidationException(what + " references unknown event " + eventId));
        if (!Objects.equals(event.tenantId(), tenantId)) {
            throw new ReferenceValidationException(what + " says tenant " + tenantId + " but event " + eventId
                    + " belongs to tenant " + event.tenantId());
        }
    }

    private static UpsertOutcome persist(UpsertOutcome outcome, Runnable save) {
        if (outcome != UpsertOutcome.UNCHANGED) {
            save.run();
        }
        return outcome;
    }
}
