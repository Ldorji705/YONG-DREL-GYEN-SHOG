
package com.yongdrel.servicetemplate.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE = "yongdrel.test.queue";
    public static final String EXCHANGE = "yongdrel.test.exchange";
    public static final String ROUTING_KEY = "test.message";

    @Bean
    public Queue testQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public DirectExchange testExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Binding testBinding(
            Queue testQueue,
            DirectExchange testExchange) {

        return BindingBuilder.bind(testQueue)
                .to(testExchange)
                .with(ROUTING_KEY);
    }
}

