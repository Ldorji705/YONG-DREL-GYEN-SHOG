package com.yongdrel.reportingservice.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Feed topology: owning services publish their current state to topic exchange yongdrel.reporting.feed
 * with routing keys event.upserted / order.upserted / prize.upserted / payout.upserted.
 * Invalid or unresolvable messages are dead-lettered to reporting.feed.dlq.
 */
@Configuration
public class RabbitMessagingConfig {

    public static final String FEED_EXCHANGE = "yongdrel.reporting.feed";
    public static final String DLX = "yongdrel.reporting.dlx";
    public static final String DLQ = "reporting.feed.dlq";
    public static final String EVENTS_QUEUE = "reporting.events";
    public static final String ORDERS_QUEUE = "reporting.orders";
    public static final String PRIZES_QUEUE = "reporting.prizes";
    public static final String PAYOUTS_QUEUE = "reporting.payouts";

    @Bean
    public Declarables reportingFeedTopology() {
        TopicExchange feed = new TopicExchange(FEED_EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue dlq = QueueBuilder.durable(DLQ).build();
        List<org.springframework.amqp.core.Declarable> all = new ArrayList<>(List.of(feed, dlx, dlq,
                BindingBuilder.bind(dlq).to(dlx).with(DLQ)));
        String[][] queues = {{EVENTS_QUEUE, "event.#"}, {ORDERS_QUEUE, "order.#"},
                {PRIZES_QUEUE, "prize.#"}, {PAYOUTS_QUEUE, "payout.#"}};
        for (String[] q : queues) {
            Queue queue = QueueBuilder.durable(q[0]).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
            Binding binding = BindingBuilder.bind(queue).to(feed).with(q[1]);
            all.add(queue);
            all.add(binding);
        }
        return new Declarables(all);
    }

    /** Converts by the listener's parameter type, ignoring the producer's Java class name header. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
