package com.yongdrel.ticket_issuance_service.messaging;

import com.yongdrel.ticket_issuance_service.messaging.event.TicketIssuedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TicketEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public TicketEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(TicketIssuedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.TICKET_EXCHANGE,
                RabbitMQConfig.TICKET_ISSUED_ROUTING_KEY,
                event
        );
    }
}
