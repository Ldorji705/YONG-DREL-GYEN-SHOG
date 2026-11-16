package com.yongdrel.wallet_service;

import com.yongdrel.wallet_service.application.port.LedgerPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Full start on real MySQL (Flyway + Hibernate validation). Same setup as the integration test. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
class WalletServiceApplicationTests {

	@MockitoBean private JwtDecoder jwtDecoder;
	@MockitoBean private LedgerPort ledgerPort;

	@Test
	void contextLoads() {
	}

}
