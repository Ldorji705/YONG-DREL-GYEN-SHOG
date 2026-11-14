package com.yongdrel.refund_service.adapter.in.web;

import com.yongdrel.refund_service.application.exception.OverRefundException;
import com.yongdrel.refund_service.application.exception.RefundNotFoundException;
import com.yongdrel.refund_service.application.service.RefundService;
import com.yongdrel.refund_service.config.SecurityConfig;
import com.yongdrel.refund_service.domain.model.Refund;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web layer + real SecurityConfig; RefundService and JwtDecoder are mocks. */
@WebMvcTest(
        controllers = RefundController.class,
        properties = "JWT_PUBLIC_KEY_PATH=unused-in-tests.pem"
)
@Import(SecurityConfig.class)
class RefundControllerSecurityTest {

    private static final String URL = "/api/refunds";
    private final UUID paymentId = UUID.randomUUID();
    private final String body = "{\"paymentId\":\"" + paymentId + "\",\"amount\":40.00}";

    @Autowired private MockMvc mockMvc;
    @MockitoBean private RefundService refundService;
    @MockitoBean private JwtDecoder jwtDecoder;

    private Refund refund(String owner) {
        return new Refund(paymentId, UUID.randomUUID(), owner, new BigDecimal("40"), "BTN", null, "k");
    }

    @Test
    void post_withoutToken_is401() throws Exception {
        mockMvc.perform(post(URL).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        verify(refundService, never()).create(any(), anyString(), anyString(), anyString());
    }

    @Test
    void post_withoutIdempotencyKey_is400() throws Exception {
        mockMvc.perform(post(URL).with(jwt().jwt(t -> t.subject("u1")))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    void post_invalidAmount_is400_andServiceNotCalled() throws Exception {
        mockMvc.perform(post(URL).with(jwt().jwt(t -> t.subject("u1"))).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentId\":\"" + paymentId + "\",\"amount\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.amount").exists());
        verify(refundService, never()).create(any(), anyString(), anyString(), anyString());
    }

    @Test
    void post_newRefund_is201_andUsesJwtSubject() throws Exception {
        when(refundService.create(any(), eq("u1"), anyString(), eq("k")))
                .thenReturn(new RefundService.CreateResult(refund("u1"), true));

        mockMvc.perform(post(URL).with(jwt().jwt(t -> t.subject("u1"))).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("u1"));
    }

    @Test
    void post_replayedKey_is200() throws Exception {
        when(refundService.create(any(), anyString(), anyString(), anyString()))
                .thenReturn(new RefundService.CreateResult(refund("u1"), false));

        mockMvc.perform(post(URL).with(jwt().jwt(t -> t.subject("u1"))).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void post_overRefund_is422() throws Exception {
        when(refundService.create(any(), anyString(), anyString(), anyString()))
                .thenThrow(new OverRefundException("too much"));

        mockMvc.perform(post(URL).with(jwt().jwt(t -> t.subject("u1"))).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error").value("OVER_REFUND"));
    }

    @Test
    void get_withoutToken_is401() throws Exception {
        mockMvc.perform(get(URL + "/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void get_someoneElsesRefund_is404() throws Exception {
        UUID id = UUID.randomUUID();
        when(refundService.get(id, "u2", false)).thenThrow(new RefundNotFoundException("nope"));

        mockMvc.perform(get(URL + "/" + id).with(jwt().jwt(t -> t.subject("u2"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_asAdmin_passesAdminFlag() throws Exception {
        UUID id = UUID.randomUUID();
        when(refundService.get(id, "boss", true)).thenReturn(refund("u1"));

        mockMvc.perform(get(URL + "/" + id).with(jwt().jwt(t -> t.subject("boss").claim("role", "ADMIN"))))
                .andExpect(status().isOk());
        verify(refundService).get(id, "boss", true);
    }

    @Test
    void retry_withToken_is200() throws Exception {
        UUID id = UUID.randomUUID();
        when(refundService.retry(id, "u1", false)).thenReturn(refund("u1"));

        mockMvc.perform(post(URL + "/" + id + "/retry").with(jwt().jwt(t -> t.subject("u1"))))
                .andExpect(status().isOk());
    }

    @Test
    void put_and_delete_areRefused() throws Exception {
        mockMvc.perform(put(URL + "/" + UUID.randomUUID()).with(jwt())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(URL + "/" + UUID.randomUUID()).with(jwt()))
                .andExpect(status().isForbidden());
    }
}
