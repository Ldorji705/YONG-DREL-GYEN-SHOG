package com.yongdrel.ticket_issuance_service.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

@Configuration
public class RabbitMQConfig {

    public static final String PAYMENT_EXCHANGE =
            "yongdrel.payment.exchange";

    public static final String PAYMENT_CONFIRMED_QUEUE =
            "yongdrel.ticket.payment-confirmed.queue";

    public static final String PAYMENT_CONFIRMED_ROUTING_KEY =
            "payment.confirmed";

    public static final String TICKET_EXCHANGE =
            "yongdrel.ticket.exchange";

    public static final String TICKET_ISSUED_ROUTING_KEY =
            "ticket.issued";

    public static final String TICKET_ISSUED_QUEUE =
            "yongdrel.ticket.issued.queue";

    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentConfirmedQueue() {
        return new Queue(PAYMENT_CONFIRMED_QUEUE, true);
    }

    @Bean
    public Binding paymentConfirmedBinding(
            Queue paymentConfirmedQueue,
            DirectExchange paymentExchange) {

        return BindingBuilder
                .bind(paymentConfirmedQueue)
                .to(paymentExchange)
                .with(PAYMENT_CONFIRMED_ROUTING_KEY);
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }

    @Bean
public org.springframework.boot.ApplicationRunner declareTicketExchange(
        org.springframework.amqp.core.AmqpAdmin amqpAdmin,
        DirectExchange ticketExchange) {

    return args -> amqpAdmin.declareExchange(ticketExchange);
}

    @Bean
    public DirectExchange ticketExchange() {
        return new DirectExchange(TICKET_EXCHANGE, true, false);
    }

    @Bean
public Queue ticketIssuedQueue() {
    return new Queue(TICKET_ISSUED_QUEUE, true);
}

@Bean
public Binding ticketIssuedBinding(
        Queue ticketIssuedQueue,
        DirectExchange ticketExchange) {

    return BindingBuilder
            .bind(ticketIssuedQueue)
            .to(ticketExchange)
            .with(TICKET_ISSUED_ROUTING_KEY);
}
}