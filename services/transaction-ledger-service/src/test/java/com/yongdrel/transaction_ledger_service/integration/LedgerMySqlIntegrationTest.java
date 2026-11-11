package com.yongdrel.transaction_ledger_service.integration;

import com.yongdrel.transaction_ledger_service.TestcontainersConfiguration;
import com.yongdrel.transaction_ledger_service.adapter.in.web.dto.CreateLedgerEntryRequest;
import com.yongdrel.transaction_ledger_service.application.service.LedgerService;
import com.yongdrel.transaction_ledger_service.domain.model.EntryDirection;
import com.yongdrel.transaction_ledger_service.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs against a real MySQL 8.4 container (Testcontainers), so the UNIQUE and
 * CHECK constraints from the Flyway migration are really enforced.
 * Every test uses fresh random IDs, so tests never interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("it")
class LedgerMySqlIntegrationTest {

    private static final String KEY_HEADER = "X-Internal-Api-Key";
    private static final String KEY = "it-internal-key"; // from application-it.properties

    @Autowired private MockMvc mockMvc;
    @Autowired private LedgerService ledgerService;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private JwtDecoder jwtDecoder;

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private int rowsWithReference(String ref) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE reference_id = ?", Integer.class, ref);
    }

    private static CreateLedgerEntryRequest request(String account, String ref) {
        return new CreateLedgerEntryRequest(account, ref, SourceType.WALLET,
                EntryDirection.DEBIT, new BigDecimal("50.00"), "BTN", "it", null);
    }

    @Test
    void postThenRetry_storesExactlyOneRow() throws Exception {
        String ref = unique("ref");
        String body = """
                {"accountId":"acc-1","referenceId":"%s","sourceType":"WALLET",
                 "direction":"DEBIT","amount":50.00,"currency":"BTN"}
                """.formatted(ref);

        String firstId = com.jayway.jsonpath.JsonPath.read(
                mockMvc.perform(post("/api/ledger/entries").header(KEY_HEADER, KEY)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.amount").value(50.0))
                        .andReturn().getResponse().getContentAsString(),
                "$.id");

        mockMvc.perform(post("/api/ledger/entries").header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(firstId));

        assertThat(rowsWithReference(ref)).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithSameReference_createExactlyOneRow() throws Exception {
        String ref = unique("race");
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        List<Future<LedgerService.RecordResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startTogether.await();
                return ledgerService.record(request("acc-race", ref));
            }));
        }
        startTogether.countDown(); // release all threads at the same moment

        int created = 0;
        Set<UUID> ids = new HashSet<>();
        for (Future<LedgerService.RecordResult> f : futures) {
            LedgerService.RecordResult r = f.get(30, TimeUnit.SECONDS);
            if (r.created()) created++;
            ids.add(r.entry().getId());
        }
        pool.shutdown();

        assertThat(created).as("exactly one request creates the entry").isEqualTo(1);
        assertThat(ids).as("every request sees the same entry").hasSize(1);
        assertThat(rowsWithReference(ref)).isEqualTo(1);
    }

    @Test
    void database_rejectsDuplicateReference_evenBypassingTheService() {
        String ref = unique("dup");
        String sql = """
                INSERT INTO ledger_entries (id, account_id, reference_id, source_type, direction,
                    amount, currency, occurred_at, recorded_at)
                VALUES (UUID_TO_BIN(UUID()), 'acc-x', ?, 'WALLET', 'DEBIT', 10, 'BTN', NOW(6), NOW(6))
                """;
        jdbc.update(sql, ref);

        assertThatThrownBy(() -> jdbc.update(sql, ref))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(rowsWithReference(ref)).isEqualTo(1);
    }

    @Test
    void database_rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ledger_entries (id, account_id, reference_id, source_type, direction,
                    amount, currency, occurred_at, recorded_at)
                VALUES (UUID_TO_BIN(UUID()), 'acc-x', ?, 'WALLET', 'DEBIT', -5, 'BTN', NOW(6), NOW(6))
                """, unique("neg")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_ledger_amount_positive");
    }

    @Test
    void database_rejectsUnknownDirection() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO ledger_entries (id, account_id, reference_id, source_type, direction,
                    amount, currency, occurred_at, recorded_at)
                VALUES (UUID_TO_BIN(UUID()), 'acc-x', ?, 'WALLET', 'SIDEWAYS', 5, 'BTN', NOW(6), NOW(6))
                """, unique("dir")))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_ledger_direction");
    }

    @Test
    void getMe_returnsOnlyTheCallersEntries() throws Exception {
        String alice = unique("alice");
        String bob = unique("bob");
        ledgerService.record(request(alice, unique("a1")));
        ledgerService.record(request(alice, unique("a2")));
        ledgerService.record(request(bob, unique("b1")));

        mockMvc.perform(get("/api/ledger/me").with(jwt().jwt(t -> t.subject(alice))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].accountId").value(alice))
                .andExpect(jsonPath("$.content[1].accountId").value(alice));

        mockMvc.perform(get("/api/ledger/me").with(jwt().jwt(t -> t.subject(bob))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].accountId").value(bob));
    }
}
