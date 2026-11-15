package com.yongdrel.fraudriskservice.adapter.out.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessagingConfig {
    public static final String FRAUD_EXCHANGE = "yongdrel.fraud.events";
    public static final String REVIEW_QUEUE = "fraud-risk.flag-review";

    @Bean
    TopicExchange fraudExchange() {
        return new TopicExchange(FRAUD_EXCHANGE, true, false);
    }

    @Bean
    Queue reviewQueue() {
        return QueueBuilder.durable(REVIEW_QUEUE).build();
    }

    @Bean
    Binding reviewBinding(Queue reviewQueue, TopicExchange fraudExchange) {
        return BindingBuilder.bind(reviewQueue).to(fraudExchange).with("fraud.flag.#");
    }
}
