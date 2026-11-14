package com.yongdrel.refund_service.integration;

import com.yongdrel.refund_service.TestcontainersConfiguration;
import com.yongdrel.refund_service.adapter.in.web.dto.CreateRefundRequest;
import com.yongdrel.refund_service.application.exception.DependencyUnavailableException;
import com.yongdrel.refund_service.application.exception.OverRefundException;
import com.yongdrel.refund_service.application.port.LedgerPort;
import com.yongdrel.refund_service.application.port.PaymentLookupPort;
import com.yongdrel.refund_service.application.port.PaymentSnapshot;
import com.yongdrel.refund_service.application.service.RefundService;
import com.yongdrel.refund_service.domain.model.Refund;
import com.yongdrel.refund_service.domain.model.RefundStatus;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real MySQL 8.4 (Testcontainers): row locks, UNIQUE and CHECK constraints are really enforced.
 * Payment Service and Ledger are mocked; the mock refund provider (SUCCESS) is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
class RefundMySqlIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private RefundService refundService;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private PaymentLookupPort paymentLookupPort;
    @MockitoBean private LedgerPort ledgerPort;

    private UUID confirmedPayment(String owner, String amount) {
        UUID id = UUID.randomUUID();
        when(paymentLookupPort.getPayment(eq(id), anyString())).thenReturn(new PaymentSnapshot(
                id, UUID.randomUUID(), owner, new BigDecimal(amount), "BTN", "CONFIRMED"));
        return id;
    }

    private static CreateRefundRequest req(UUID paymentId, String amount) {
        return new CreateRefundRequest(paymentId, new BigDecimal(amount), "it");
    }

    private BigDecimal reserved(UUID paymentId) {
        return jdbc.queryForObject(
                "SELECT reserved_amount FROM payment_refund_balances WHERE payment_id = UUID_TO_BIN(?)",
                BigDecimal.class, paymentId.toString());
    }

    @Test
    void httpPost_createsCompletedRefund_inMySql() throws Exception {
        String user = "u-" + UUID.randomUUID();
        UUID pay = confirmedPayment(user, "100.00");

        mockMvc.perform(post("/api/refunds").with(jwt().jwt(t -> t.subject(user)))
                        .header("Idempotency-Key", "k-http")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + pay + "\",\"amount\":25.50}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(reserved(pay)).isEqualByComparingTo("25.50");
    }

    @Test
    void concurrentRefunds_neverExceedThePaidAmount() throws Exception {
        String user = "u-" + UUID.randomUUID();
        UUID pay = confirmedPayment(user, "100.00");
        int threads = 10; // 10 x 20 = 200 requested against 100 paid
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<RefundService.CreateResult>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String key = "k-" + i;
            futures.add(pool.submit(() -> {
                go.await();
                return refundService.create(req(pay, "20.00"), user, "jwt", key);
            }));
        }
        go.countDown();

        int ok = 0, rejected = 0;
        for (Future<RefundService.CreateResult> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
                ok++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(OverRefundException.class);
                rejected++;
            }
        }
        pool.shutdown();

        assertThat(ok).as("exactly 100 / 20 refunds succeed").isEqualTo(5);
        assertThat(rejected).isEqualTo(5);
        assertThat(reserved(pay)).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject(
                "SELECT SUM(amount) FROM refunds WHERE payment_id = UUID_TO_BIN(?)",
                BigDecimal.class, pay.toString())).isEqualByComparingTo("100.00");
    }

    @Test
    void concurrentRetriesWithSameKey_createOneRefund() throws Exception {
        String user = "u-" + UUID.randomUUID();
        UUID pay = confirmedPayment(user, "100.00");
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<RefundService.CreateResult>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            futures.add(pool.submit(() -> {
                go.await();
                return refundService.create(req(pay, "30.00"), user, "jwt", "same-key");
            }));
        }
        go.countDown();

        Set<UUID> ids = new HashSet<>();
        for (Future<RefundService.CreateResult> f : futures) {
            ids.add(f.get(60, TimeUnit.SECONDS).refund().getId());
        }
        pool.shutdown();

        assertThat(ids).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM refunds WHERE payment_id = UUID_TO_BIN(?)",
                Integer.class, pay.toString())).isEqualTo(1);
        assertThat(reserved(pay)).as("amount reserved only once").isEqualByComparingTo("30.00");
    }

    @Test
    void ledgerDown_refundIsKeptFailed_thenRetrySucceeds() {
        String user = "u-" + UUID.randomUUID();
        UUID pay = confirmedPayment(user, "50.00");
        doThrow(new DependencyUnavailableException("ledger down")).when(ledgerPort).recordRefund(any());

        Refund failed = refundService.create(req(pay, "50.00"), user, "jwt", "k-fail").refund();
        assertThat(failed.getStatus()).isEqualTo(RefundStatus.FAILED);
        assertThat(reserved(pay)).as("still reserved while FAILED").isEqualByComparingTo("50.00");

        reset(ledgerPort);
        doNothing().when(ledgerPort).recordRefund(any());
        Refund retried = refundService.retry(failed.getId(), user, false);

        assertThat(retried.getStatus()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(retried.getAttempts()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM refunds WHERE id = UUID_TO_BIN(?)",
                String.class, failed.getId().toString())).isEqualTo("COMPLETED");
    }

    @Test
    void database_rejectsReservingMoreThanPaid() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payment_refund_balances (payment_id, paid_amount, reserved_amount, currency, updated_at)
                VALUES (UUID_TO_BIN(UUID()), 10, 11, 'BTN', NOW(6))"""))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_balance_not_over_refunded");
    }

    @Test
    void database_rejectsDuplicateIdempotencyKeyForSameUser() {
        String sql = """
                INSERT INTO refunds (id, payment_id, order_id, user_id, amount, currency, status,
                    idempotency_key, attempts, created_at, updated_at, version)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), 'dup-user', 5, 'BTN',
                    'PENDING', 'dup-key', 0, NOW(6), NOW(6), 0)""";
        jdbc.update(sql);
        assertThatThrownBy(() -> jdbc.update(sql)).isInstanceOf(DataAccessException.class);
    }
}
