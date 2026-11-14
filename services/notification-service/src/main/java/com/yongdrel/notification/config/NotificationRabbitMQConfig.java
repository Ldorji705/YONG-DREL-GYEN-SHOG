package com.yongdrel.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationRabbitMQConfig {

    public static final String TICKET_EXCHANGE =
            "yongdrel.ticket.exchange";

    public static final String TICKET_ISSUED_ROUTING_KEY =
            "ticket.issued";

    public static final String NOTIFICATION_QUEUE =
            "yongdrel.notification.ticket-issued.queue";

    public static final String DEAD_LETTER_EXCHANGE =
            "yongdrel.notification.dlx";

    public static final String DEAD_LETTER_QUEUE =
            "yongdrel.notification.ticket-issued.dlq";

    public static final String DEAD_LETTER_ROUTING_KEY =
            "notification.ticket-issued.dead";

    @Bean
    public DirectExchange notificationTicketExchange() {
        return new DirectExchange(TICKET_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationTicketIssuedQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument(
                        "x-dead-letter-exchange",
                        DEAD_LETTER_EXCHANGE
                )
                .withArgument(
                        "x-dead-letter-routing-key",
                        DEAD_LETTER_ROUTING_KEY
                )
                .build();
    }

    @Bean
    public Binding notificationTicketIssuedBinding() {
        return BindingBuilder
                .bind(notificationTicketIssuedQueue())
                .to(notificationTicketExchange())
                .with(TICKET_ISSUED_ROUTING_KEY);
    }

    @Bean
    public DirectExchange notificationDeadLetterExchange() {
        return new DirectExchange(
                DEAD_LETTER_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue notificationDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding notificationDeadLetterBinding() {
        return BindingBuilder
                .bind(notificationDeadLetterQueue())
                .to(notificationDeadLetterExchange())
                .with(DEAD_LETTER_ROUTING_KEY);
    }
}
