package com.yongdrel.refund_service;

import com.yongdrel.refund_service.application.port.LedgerPort;
import com.yongdrel.refund_service.application.port.PaymentLookupPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full start against real MySQL: Flyway migration runs and Hibernate validation passes.
 * Same setup and mocks as RefundMySqlIntegrationTest, so both share one container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
class RefundServiceApplicationTests {

	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private PaymentLookupPort paymentLookupPort;
	@MockitoBean private LedgerPort ledgerPort;

	@Test
	void contextLoads() {
	}

}
