package com.yongdrel.transaction_ledger_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Full application start against real MySQL: proves the Flyway migration runs
 * and Hibernate's schema validation passes.
 */
@SpringBootTest
@AutoConfigureMockMvc // same setup as the integration tests, so both share one MySQL container
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
class TransactionLedgerServiceApplicationTests {

	// No key file in tests; JWT checking itself is covered by LedgerControllerSecurityTest.
	@MockitoBean
	private JwtDecoder jwtDecoder;

	@Test
	void contextLoads() {
	}

}
