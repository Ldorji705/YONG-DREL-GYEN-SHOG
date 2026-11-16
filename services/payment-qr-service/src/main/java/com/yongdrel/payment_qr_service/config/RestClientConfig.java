package com.yongdrel.payment_qr_service.config;
import org.springframework.context.annotation.*; import org.springframework.web.client.RestClient;
@Configuration public class RestClientConfig{@Bean RestClient.Builder restClientBuilder(){return RestClient.builder();}}
