package com.yongdrel.transaction_ledger_service.adapter.in.web;

import com.yongdrel.transaction_ledger_service.application.exception.LedgerReferenceConflictException;
import com.yongdrel.transaction_ledger_service.application.service.LedgerService;
import com.yongdrel.transaction_ledger_service.config.SecurityConfig;
import com.yongdrel.transaction_ledger_service.domain.model.EntryDirection;
import com.yongdrel.transaction_ledger_service.domain.model.LedgerEntry;
import com.yongdrel.transaction_ledger_service.domain.model.SourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer + real SecurityConfig. The service and JWT decoder are mocks,
 * so no database or key file is needed.
 */
@WebMvcTest(
        controllers = LedgerController.class,
        properties = {
                "app.ledger.internal-api-key=" + LedgerControllerSecurityTest.KEY,
                // Only needed so the property resolves; the JwtDecoder below is a mock,
                // so this file is never read.
                "JWT_PUBLIC_KEY_PATH=unused-in-tests.pem"
        }
)
@Import(SecurityConfig.class)
class LedgerControllerSecurityTest {

    static final String KEY = "test-internal-key";
    private static final String KEY_HEADER = "X-Internal-Api-Key";
    private static final String URL = "/api/ledger/entries";

    private static final String VALID_BODY = """
            {"accountId":"1","referenceId":"wallet-op-001","sourceType":"WALLET",
             "direction":"DEBIT","amount":50.00,"currency":"BTN","description":"Ticket"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LedgerService ledgerService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static LedgerEntry entry(String account, String ref) {
        return new LedgerEntry(account, ref, SourceType.WALLET, EntryDirection.DEBIT,
                new BigDecimal("50.00"), "BTN", "Ticket", Instant.now());
    }

    // ---------- POST /api/ledger/entries (internal) ----------

    @Test
    void post_withoutKey_is401() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verify(ledgerService, never()).record(any());
    }

    @Test
    void post_withWrongKey_is401() throws Exception {
        mockMvc.perform(post(URL).header(KEY_HEADER, "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        verify(ledgerService, never()).record(any());
    }

    @Test
    void post_withNormalUserJwt_is403() throws Exception {
        mockMvc.perform(post(URL)
                        .with(jwt().jwt(t -> t.subject("1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(ledgerService, never()).record(any());
    }

    @Test
    void post_withKey_newEntry_is201() throws Exception {
        when(ledgerService.record(any()))
                .thenReturn(new LedgerService.RecordResult(entry("1", "wallet-op-001"), true));

        mockMvc.perform(post(URL).header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceId").value("wallet-op-001"))
                .andExpect(jsonPath("$.amount").value(50.0));
    }

    @Test
    void post_withKey_retryOfSameReference_is200() throws Exception {
        when(ledgerService.record(any()))
                .thenReturn(new LedgerService.RecordResult(entry("1", "wallet-op-001"), false));

        mockMvc.perform(post(URL).header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void post_withKey_conflictingReference_is409() throws Exception {
        when(ledgerService.record(any()))
                .thenThrow(new LedgerReferenceConflictException("wallet-op-001"));

        mockMvc.perform(post(URL).header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("DUPLICATE_REFERENCE_CONFLICT"));
    }

    @Test
    void post_withKey_invalidFields_is400_andNothingRecorded() throws Exception {
        String bad = """
                {"accountId":"1","referenceId":"r","sourceType":"WALLET",
                 "direction":"DEBIT","amount":-5,"currency":"btn"}
                """;
        mockMvc.perform(post(URL).header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.amount").exists())
                .andExpect(jsonPath("$.fields.currency").exists());
        verify(ledgerService, never()).record(any());
    }

    @Test
    void post_withKey_unknownDirection_is400() throws Exception {
        String bad = VALID_BODY.replace("\"DEBIT\"", "\"SIDEWAYS\"");
        mockMvc.perform(post(URL).header(KEY_HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MALFORMED_REQUEST"));
    }

    // ---------- GET /api/ledger/me ----------

    @Test
    void getMe_withoutToken_is401() throws Exception {
        mockMvc.perform(get("/api/ledger/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_withInternalKeyOnly_is401() throws Exception {
        // The internal key only unlocks the write endpoint, not user history.
        mockMvc.perform(get("/api/ledger/me").header(KEY_HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getMe_usesJwtSubjectAsAccount() throws Exception {
        when(ledgerService.findForAccount("7", 0, 20))
                .thenReturn(new PageImpl<>(List.of(entry("7", "wallet-op-777"))));

        mockMvc.perform(get("/api/ledger/me").with(jwt().jwt(t -> t.subject("7"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].accountId").value("7"))
                .andExpect(jsonPath("$.totalElements").value(1));

        verify(ledgerService).findForAccount("7", 0, 20);
    }

    // ---------- Append-only: no update / delete ----------

    @Test
    void delete_isRefused_evenWithKey() throws Exception {
        mockMvc.perform(delete(URL + "/any-id").header(KEY_HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void put_isRefused_forAuthenticatedUser() throws Exception {
        mockMvc.perform(put(URL + "/any-id").with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verify(ledgerService, never()).findForAccount(anyString(), anyInt(), anyInt());
    }
}
