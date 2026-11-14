package com.yongdrel.fraudriskservice.adapter.out.messaging;

import com.yongdrel.fraudriskservice.application.port.out.FlagEventPublisherPort;
import com.yongdrel.fraudriskservice.domain.model.RiskFlag;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class RabbitFlagEventPublisher implements FlagEventPublisherPort {
    private final RabbitTemplate rabbitTemplate;

    public RabbitFlagEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void flagRaised(RiskFlag flag) {
        publish("RAISED", "fraud.flag.raised", flag);
    }

    @Override
    public void flagReviewed(RiskFlag flag) {
        publish("REVIEWED", "fraud.flag.reviewed", flag);
    }

    private void publish(String eventType, String routingKey, RiskFlag flag) {
        FraudFlagEventMessage message = new FraudFlagEventMessage(eventType, flag.getId(), flag.getCheckId(),
                flag.getTenantId(), flag.getEventId(), flag.getOrderId(), flag.getBuyerId(), flag.getScore(),
                flag.getRiskLevel().name(), flag.getReasons().stream().map(Enum::name).toList(),
                flag.getStatus().name(), flag.getReviewerId(), eventTime(flag));
        rabbitTemplate.convertAndSend(RabbitMessagingConfig.FRAUD_EXCHANGE, routingKey, message);
    }

    private static Instant eventTime(RiskFlag flag) {
        return flag.getReviewedAt() != null ? flag.getReviewedAt() : flag.getCreatedAt();
    }
}
