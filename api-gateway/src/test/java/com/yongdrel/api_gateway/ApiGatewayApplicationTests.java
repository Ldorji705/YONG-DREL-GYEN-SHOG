package com.yongdrel.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "JWT_PUBLIC_KEY_PATH=../infrastructure/keys/public.pem",
        "spring.cloud.config.enabled=false",
        "spring.config.import="
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
    }
}
