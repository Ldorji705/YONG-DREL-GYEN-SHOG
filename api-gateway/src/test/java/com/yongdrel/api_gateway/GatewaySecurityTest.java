package com.yongdrel.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "JWT_PUBLIC_KEY_PATH=../infrastructure/keys/public.pem",
                "spring.cloud.config.enabled=false",
                "spring.config.import="
        }
)
@AutoConfigureWebTestClient
class GatewaySecurityTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void protectedEndpoint_shouldRejectRequestWithoutJwt() {
        webTestClient.get()
                .uri("/api/users")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}
