package com.yongdrel.wallet_service.adapter.in.web;

import com.yongdrel.wallet_service.application.exception.InsufficientFundsException;
import com.yongdrel.wallet_service.application.service.WalletService;
import com.yongdrel.wallet_service.config.SecurityConfig;
import com.yongdrel.wallet_service.domain.model.OperationType;
import com.yongdrel.wallet_service.domain.model.Wallet;
import com.yongdrel.wallet_service.domain.model.WalletOperation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer + real SecurityConfig (incl. internal key filter); WalletService and JwtDecoder are mocks. */
@WebMvcTest(
        controllers = WalletController.class,
        properties = {"JWT_PUBLIC_KEY_PATH=unused-in-tests.pem", "app.wallet.internal-api-key=test-key"}
)
@Import(SecurityConfig.class)
class WalletControllerSecurityTest {

    private static final String DEBIT = "{\"amount\":30.00}";
    private static final String CREDIT = "{\"ownerId\":\"u1\",\"amount\":100.00}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private WalletService walletService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private static WalletService.ApplyResult result(OperationType type, boolean created) {
        return new WalletService.ApplyResult(new WalletOperation(UUID.randomUUID(), "k", type,
                new BigDecimal("30"), new BigDecimal("70"), null), created);
    }

    @Test
    void me_withoutToken_is401() throws Exception {
        mockMvc.perform(get("/api/wallets/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void me_usesJwtSubject() throws Exception {
        when(walletService.getOrCreate("u1")).thenReturn(new Wallet("u1", "BTN"));

        mockMvc.perform(get("/api/wallets/me").with(jwt().jwt(t -> t.subject("u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").value("u1"));
    }

    @Test
    void debit_newOperation_is201_onCallersOwnWallet() throws Exception {
        when(walletService.debit(eq("u1"), eq("k"), any(), any())).thenReturn(result(OperationType.DEBIT, true));

        mockMvc.perform(post("/api/wallets/debit").with(jwt().jwt(t -> t.subject("u1")))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(DEBIT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(70.0));
    }

    @Test
    void debit_replay_is200() throws Exception {
        when(walletService.debit(anyString(), anyString(), any(), any())).thenReturn(result(OperationType.DEBIT, false));

        mockMvc.perform(post("/api/wallets/debit").with(jwt().jwt(t -> t.subject("u1")))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(DEBIT))
                .andExpect(status().isOk());
    }

    @Test
    void debit_insufficientFunds_is422() throws Exception {
        when(walletService.debit(anyString(), anyString(), any(), any()))
                .thenThrow(new InsufficientFundsException("low"));

        mockMvc.perform(post("/api/wallets/debit").with(jwt().jwt(t -> t.subject("u1")))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(DEBIT))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void debit_withoutIdempotencyKey_is400() throws Exception {
        mockMvc.perform(post("/api/wallets/debit").with(jwt().jwt(t -> t.subject("u1")))
                        .contentType(MediaType.APPLICATION_JSON).content(DEBIT))
                .andExpect(status().isBadRequest());
    }

    @Test
    void debit_negativeAmount_is400_andServiceNotCalled() throws Exception {
        mockMvc.perform(post("/api/wallets/debit").with(jwt().jwt(t -> t.subject("u1")))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
        verify(walletService, never()).debit(anyString(), anyString(), any(), any());
    }

    @Test
    void credit_withNormalUserJwt_is403() throws Exception {
        mockMvc.perform(post("/api/wallets/credit")
                        .with(jwt().jwt(t -> t.subject("u1").claim("role", "USER")))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(CREDIT))
                .andExpect(status().isForbidden());
        verify(walletService, never()).credit(anyString(), anyString(), any(), any());
    }

    @Test
    void credit_withoutAnyAuth_is401() throws Exception {
        mockMvc.perform(post("/api/wallets/credit")
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(CREDIT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void credit_withWrongKey_is401() throws Exception {
        mockMvc.perform(post("/api/wallets/credit").header("X-Internal-Api-Key", "wrong")
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(CREDIT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void credit_withInternalKey_is201() throws Exception {
        when(walletService.credit(eq("u1"), eq("k"), any(), any())).thenReturn(result(OperationType.CREDIT, true));

        mockMvc.perform(post("/api/wallets/credit").header("X-Internal-Api-Key", "test-key")
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(CREDIT))
                .andExpect(status().isCreated());
    }

    @Test
    void credit_withAdminJwt_is201() throws Exception {
        when(walletService.credit(anyString(), anyString(), any(), any())).thenReturn(result(OperationType.CREDIT, true));

        mockMvc.perform(post("/api/wallets/credit")
                        .with(jwt().jwt(t -> t.subject("boss").claim("role", "ADMIN"))
                                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                        .header("Idempotency-Key", "k").contentType(MediaType.APPLICATION_JSON).content(CREDIT))
                .andExpect(status().isCreated());
    }

    @Test
    void delete_isRefused() throws Exception {
        mockMvc.perform(delete("/api/wallets/me").with(jwt())).andExpect(status().isForbidden());
    }
}
