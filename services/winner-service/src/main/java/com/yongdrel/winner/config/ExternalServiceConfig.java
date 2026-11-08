package com.yongdrel.winner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class ExternalServiceConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient eventRestClient(
            RestClient.Builder builder,
            @Value("${app.services.event-url:http://localhost:8084}") String eventUrl) {

        return builder.clone()
                .baseUrl(eventUrl)
                .build();
    }

    @Bean
    public RestClient prizeRestClient(
            RestClient.Builder builder,
            @Value("${app.services.prize-url:http://localhost:8086}") String prizeUrl) {

        return builder.clone()
                .baseUrl(prizeUrl)
                .build();
    }
}