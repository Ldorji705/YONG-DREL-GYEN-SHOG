package com.yongdrel.ticket_inventory_service.config;
import org.springframework.context.annotation.*;
import org.springframework.web.client.RestClient;
@Configuration public class RestClientConfig {
    @Bean public RestClient.Builder restClientBuilder() { return RestClient.builder(); }
}
