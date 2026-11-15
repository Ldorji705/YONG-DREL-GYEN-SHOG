package com.yongdrel.auditlogservice.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inbound topology.
 * - yongdrel.audit.events (topic) --audit.#--> audit.events : any service publishes audit events here
 * - yongdrel.fraud.events (topic) --fraud.flag.#--> audit.fraud-events : flag events of the Fraud &amp; Risk Service
 * Rejected (invalid) messages are dead-lettered to audit.events.dlq instead of being retried forever.
 */
@Configuration
public class RabbitMessagingConfig {

    public static final String AUDIT_EXCHANGE = "yongdrel.audit.events";
    public static final String AUDIT_QUEUE = "audit.events";
    public static final String FRAUD_EXCHANGE = "yongdrel.fraud.events";
    public static final String FRAUD_QUEUE = "audit.fraud-events";
    public static final String DLX = "yongdrel.audit.dlx";
    public static final String DLQ = "audit.events.dlq";

    @Bean
    public TopicExchange auditExchange() {
        return new TopicExchange(AUDIT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange fraudExchange() {
        return new TopicExchange(FRAUD_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange auditDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue auditQueue() {
        return QueueBuilder.durable(AUDIT_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    public Queue fraudAuditQueue() {
        return QueueBuilder.durable(FRAUD_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey(DLQ).build();
    }

    @Bean
    public Queue auditDeadLetterQueue() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding auditBinding() {
        return BindingBuilder.bind(auditQueue()).to(auditExchange()).with("audit.#");
    }

    @Bean
    public Binding fraudBinding() {
        return BindingBuilder.bind(fraudAuditQueue()).to(fraudExchange()).with("fraud.flag.#");
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(auditDeadLetterQueue()).to(auditDeadLetterExchange()).with(DLQ);
    }

    /** Converts by the listener's parameter type, ignoring the producer's Java class name header. */
    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(objectMapper);
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
