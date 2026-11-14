
package com.yongdrel.winner.adapter.in.web;

import com.yongdrel.winner.application.service.WinnerApplicationService;
import com.yongdrel.winner.domain.model.Winner;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "JWT_PUBLIC_KEY_PATH=/tmp/winner-test-public-key.pem",
        "spring.datasource.url=jdbc:h2:mem:winner_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class WinnerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WinnerApplicationService winnerApplicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String ADMIN = "ROLE_ADMIN";
    private static final String USER = "ROLE_USER";

    @Test
    void shouldRejectUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/winners/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnAuthenticatedUsersWinners() throws Exception {
        UUID winnerId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID prizeId = UUID.randomUUID();

        Winner winner = new Winner(
                winnerId, "draw-001", eventId,
                "ticket-001", "101", prizeId, null
        );

        when(winnerApplicationService.getMyWinners("101"))
                .thenReturn(List.of(winner));

        mockMvc.perform(get("/api/winners/me")
                        .with(jwt().jwt(token -> token.subject("101"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(winnerId.toString()))
                .andExpect(jsonPath("$[0].userId").value("101"));

        verify(winnerApplicationService).getMyWinners("101");
    }

    @Test
    void shouldAllowUserToAccessOwnWinnerRecord() throws Exception {
        UUID winnerId = UUID.randomUUID();

        Winner winner = new Winner(
                winnerId, "draw-001", UUID.randomUUID(),
                "ticket-001", "101", UUID.randomUUID(), null
        );

        when(winnerApplicationService.getWinnerById(winnerId))
                .thenReturn(winner);

        mockMvc.perform(get("/api/winners/{id}", winnerId)
                        .with(jwt().jwt(token -> token.subject("101"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(winnerId.toString()))
                .andExpect(jsonPath("$.userId").value("101"));
    }

    @Test
    void shouldRejectAccessToAnotherUsersWinnerRecord() throws Exception {
        UUID winnerId = UUID.randomUUID();

        Winner winner = new Winner(
                winnerId, "draw-001", UUID.randomUUID(),
                "ticket-001", "101", UUID.randomUUID(), null
        );

        when(winnerApplicationService.getWinnerById(winnerId))
                .thenReturn(winner);

        mockMvc.perform(get("/api/winners/{id}", winnerId)
                        .with(jwt().jwt(token -> token.subject("202"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnauthenticatedEventWinnersRequest() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(get("/api/winners/events/{eventId}", eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectNormalUserFromViewingEventWinners() throws Exception {
        UUID eventId = UUID.randomUUID();

        mockMvc.perform(get("/api/winners/events/{eventId}", eventId)
                        .with(jwt()
                                .jwt(token -> token.subject("101"))
                                .authorities(new SimpleGrantedAuthority(USER))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(winnerApplicationService);
    }

    @Test
    void shouldAllowAdminToViewEventWinners() throws Exception {
        UUID eventId = UUID.randomUUID();

        when(winnerApplicationService.getWinnersByEvent(eventId))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/winners/events/{eventId}", eventId)
                        .with(jwt()
                                .jwt(token -> token.subject("999"))
                                .authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(winnerApplicationService).getWinnersByEvent(eventId);
    }

    @Test
    void shouldAllowAdminToCreateWinner() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID prizeId = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();

        Winner winner = new Winner(
                winnerId, "draw-001", eventId,
                "ticket-001", "101", prizeId, null
        );

        when(winnerApplicationService.createWinner(
                eq("draw-001"),
                eq(eventId),
                eq("ticket-001"),
                eq("101"),
                eq(prizeId),
                anyString()
        )).thenReturn(winner);


        String request = """
                {
                    "drawId": "draw-001",
                    "eventId": "%s",
                    "ticketId": "ticket-001",
                    "userId": "101",
                    "prizeId": "%s"
                }
                """.formatted(eventId, prizeId);

        mockMvc.perform(post("/api/winners")
                        .with(jwt()
                                .jwt(token -> token.subject("999"))
                                .authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(winnerId.toString()))
                .andExpect(jsonPath("$.drawId").value("draw-001"));

        verify(winnerApplicationService).createWinner(
                eq("draw-001"),
                eq(eventId),
                eq("ticket-001"),
                eq("101"),
                eq(prizeId),
                anyString()
        );
    }

    @Test
    void shouldRejectNormalUserFromCreatingWinner() throws Exception {
        mockMvc.perform(post("/api/winners")
                        .with(jwt()
                                .jwt(token -> token.subject("101"))
                                .authorities(new SimpleGrantedAuthority(USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(winnerApplicationService);
    }

    @Test
    void shouldRejectInvalidWinnerCreationRequest() throws Exception {
        mockMvc.perform(post("/api/winners")
                        .with(jwt()
                                .jwt(token -> token.subject("999"))
                                .authorities(new SimpleGrantedAuthority(ADMIN)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "drawId": "",
                                    "eventId": null,
                                    "ticketId": "",
                                    "userId": "",
                                    "prizeId": null
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(winnerApplicationService);
    }

    @Test
    void shouldAllowAdminToVerifyWinner() throws Exception {
        UUID winnerId = UUID.randomUUID();

        Winner winner = new Winner(
                winnerId, "draw-001", UUID.randomUUID(),
                "ticket-001", "101", UUID.randomUUID(),
                java.time.LocalDateTime.of(2026, 11, 6, 10, 0)
        );

        when(winnerApplicationService.verifyWinner(winnerId))
                .thenReturn(winner);

        mockMvc.perform(patch("/api/winners/{id}/verify", winnerId)
                        .with(jwt()
                                .jwt(token -> token.subject("999"))
                                .authorities(new SimpleGrantedAuthority(ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(winnerId.toString()))
                .andExpect(jsonPath("$.verifiedAt").isNotEmpty());

        verify(winnerApplicationService).verifyWinner(winnerId);
    }

    @Test
    void shouldRejectNormalUserFromVerifyingWinner() throws Exception {
        UUID winnerId = UUID.randomUUID();

        mockMvc.perform(patch("/api/winners/{id}/verify", winnerId)
                        .with(jwt()
                                .jwt(token -> token.subject("101"))
                                .authorities(new SimpleGrantedAuthority(USER))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(winnerApplicationService);
    }
}
