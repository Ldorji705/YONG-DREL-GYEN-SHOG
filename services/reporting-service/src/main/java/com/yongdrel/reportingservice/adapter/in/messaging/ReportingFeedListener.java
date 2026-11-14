package com.yongdrel.reportingservice.adapter.in.messaging;

import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.EventIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.OrderIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.PayoutIngest;
import com.yongdrel.reportingservice.adapter.in.web.dto.IngestRequests.PrizeIngest;
import com.yongdrel.reportingservice.application.port.in.IngestUseCase;
import com.yongdrel.reportingservice.domain.exception.StaleUpdateException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Applies feed messages to the read model. Out-of-order (stale) messages are acknowledged and ignored, because a
 * newer state is already stored; invalid messages and unknown references are dead-lettered for investigation.
 */
@Component
public class ReportingFeedListener {

    private static final Logger log = LoggerFactory.getLogger(ReportingFeedListener.class);

    private final IngestUseCase ingest;
    private final Validator validator;

    public ReportingFeedListener(IngestUseCase ingest, Validator validator) {
        this.ingest = ingest;
        this.validator = validator;
    }

    @RabbitListener(queues = RabbitMessagingConfig.EVENTS_QUEUE)
    public void onEvent(EventIngest m) {
        apply("event", m, m.id(), () -> ingest.upsertEvent(m.toProjection(m.id()), null).name());
    }

    @RabbitListener(queues = RabbitMessagingConfig.ORDERS_QUEUE)
    public void onOrder(OrderIngest m) {
        apply("order", m, m.id(), () -> ingest.upsertOrder(m.toProjection(m.id()), null).name());
    }

    @RabbitListener(queues = RabbitMessagingConfig.PRIZES_QUEUE)
    public void onPrize(PrizeIngest m) {
        apply("prize", m, m.id(), () -> ingest.upsertPrize(m.toProjection(m.id()), null).name());
    }

    @RabbitListener(queues = RabbitMessagingConfig.PAYOUTS_QUEUE)
    public void onPayout(PayoutIngest m) {
        apply("payout", m, m.id(), () -> ingest.upsertPayout(m.toProjection(m.id()), null).name());
    }

    private <T> void apply(String kind, T message, Long id, Supplier<String> action) {
        Set<ConstraintViolation<T>> violations = validator.validate(message);
        if (id == null) {
            throw new AmqpRejectAndDontRequeueException("Invalid " + kind + " message: id is required");
        }
        if (!violations.isEmpty()) {
            throw new AmqpRejectAndDontRequeueException("Invalid " + kind + " message: " + violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining(", ")));
        }
        try {
            log.debug("{} {} -> {}", kind, id, action.get());
        } catch (StaleUpdateException e) {
            log.info("Ignoring stale {} {}: {}", kind, id, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Rejecting {} {}: {}", kind, id, e.getMessage());
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
