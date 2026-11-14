package com.yongdrel.claim.config;

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
    public RestClient winnerRestClient(
            RestClient.Builder builder,
            @Value("${app.services.winner.base-url}") String winnerUrl
    ) {
        return builder.clone()
                .baseUrl(winnerUrl)
                .build();
    }

    @Bean
    public RestClient prizeRestClient(
            RestClient.Builder builder,
            @Value("${app.services.prize.base-url}") String prizeUrl
    ) {
        return builder.clone()
                .baseUrl(prizeUrl)
                .build();
    }
}