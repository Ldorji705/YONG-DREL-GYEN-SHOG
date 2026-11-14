package com.yongdrel.payment_service.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE =
            "yongdrel.payment.exchange";

    public static final String PAYMENT_CONFIRMED_KEY =
            "payment.confirmed";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }
}
