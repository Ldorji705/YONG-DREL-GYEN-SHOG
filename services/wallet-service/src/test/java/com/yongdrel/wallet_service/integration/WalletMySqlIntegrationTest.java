package com.yongdrel.wallet_service.integration;

import com.yongdrel.wallet_service.TestcontainersConfiguration;
import com.yongdrel.wallet_service.application.exception.DependencyUnavailableException;
import com.yongdrel.wallet_service.application.exception.InsufficientFundsException;
import com.yongdrel.wallet_service.application.port.LedgerPort;
import com.yongdrel.wallet_service.application.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MySQL 8.4 (Testcontainers): row lock, UNIQUE and CHECK constraints are really enforced. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
class WalletMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private WalletService walletService;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private LedgerPort ledgerPort;

    private BigDecimal balanceOf(String owner) {
        return jdbc.queryForObject("SELECT balance FROM wallets WHERE owner_id = ?", BigDecimal.class, owner);
    }

    @Test
    void httpFlow_creditWithKey_debitWithJwt_thenMe() throws Exception {
        String user = "u-" + UUID.randomUUID();

        mockMvc.perform(post("/api/wallets/credit").header("X-Internal-Api-Key", "it-wallet-key")
                        .header("Idempotency-Key", "c1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ownerId\":\"" + user + "\",\"amount\":100.00}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/wallets/debit").with(jwt().jwt(t -> t.subject(user)))
                        .header("Idempotency-Key", "d1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":30.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(70.0));

        mockMvc.perform(get("/api/wallets/me").with(jwt().jwt(t -> t.subject(user))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(70.0));
    }

    @Test
    void concurrentDebits_neverMakeBalanceNegative() throws Exception {
        String user = "u-" + UUID.randomUUID();
        walletService.credit(user, "seed", new BigDecimal("100.00"), null);

        int threads = 10; // 10 x 20 = 200 requested from a balance of 100
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<WalletService.ApplyResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String key = "d-" + i;
            futures.add(pool.submit(() -> {
                go.await();
                return walletService.debit(user, key, new BigDecimal("20.00"), null);
            }));
        }
        go.countDown();

        int ok = 0, refused = 0;
        for (Future<WalletService.ApplyResult> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(InsufficientFundsException.class);
                refused++;
            }
        }
        pool.shutdown();

        assertThat(ok).as("exactly 100 / 20 debits succeed").isEqualTo(5);
        assertThat(refused).isEqualTo(5);
        assertThat(balanceOf(user)).isEqualByComparingTo("0");
    }

    @Test
    void concurrentRetriesWithSameKey_applyOnce() throws Exception {
        String user = "u-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<WalletService.ApplyResult>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return walletService.credit(user, "same-key", new BigDecimal("25.00"), null);
            }));
        }
        go.countDown();

        Set<UUID> ids = new HashSet<>();
        for (Future<WalletService.ApplyResult> f : futures) {
            ids.add(f.get(60, TimeUnit.SECONDS).operation().getId());
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(balanceOf(user)).as("credited only once").isEqualByComparingTo("25.00");
    }

    @Test
    void ledgerDown_thenSyncJob_recordsTheOperation() {
        String user = "u-" + UUID.randomUUID();
        doThrow(new DependencyUnavailableException("down")).when(ledgerPort).recordOperation(any(), anyString(), anyString());

        UUID opId = walletService.credit(user, "c1", new BigDecimal("10"), null).operation().getId();
        assertThat(jdbc.queryForObject("SELECT ledger_recorded FROM wallet_ops WHERE id = UUID_TO_BIN(?)",
                Boolean.class, opId.toString())).isFalse();

        reset(ledgerPort);
        doNothing().when(ledgerPort).recordOperation(any(), anyString(), anyString());
        walletService.syncPendingToLedger(50);

        assertThat(jdbc.queryForObject("SELECT ledger_recorded FROM wallet_ops WHERE id = UUID_TO_BIN(?)",
                Boolean.class, opId.toString())).isTrue();
    }

    @Test
    void database_rejectsNegativeBalance() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO wallets (id, owner_id, currency, balance, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(UUID()), ?, 'BTN', -1, 0, NOW(6), NOW(6))""", "neg-" + UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_wallet_balance_not_negative");
    }

    @Test
    void database_allowsOnlyOneWalletPerUser() {
        String sql = """
                INSERT INTO wallets (id, owner_id, currency, balance, version, created_at, updated_at)
                VALUES (UUID_TO_BIN(UUID()), 'dup-owner', 'BTN', 0, 0, NOW(6), NOW(6))""";
        jdbc.update(sql);
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataAccessException.class);
    }
}
