package com.yongdrel.notification.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

@Configuration
public class NotificationRabbitMessageConfig {

    @Bean
    public JacksonJsonMessageConverter notificationJsonMessageConverter() {
        return new JacksonJsonMessageConverter(
                JsonMapper.builder()
                        .findAndAddModules()
                        .build()
        );
    }
}
