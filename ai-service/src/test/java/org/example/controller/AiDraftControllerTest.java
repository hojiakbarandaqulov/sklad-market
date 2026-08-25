package org.example.controller;

import org.example.config.SecurityConfig;
import org.example.dto.DraftActionResponse;
import org.example.dto.DraftDetailsResponse;
import org.example.entity.DraftStatus;
import org.example.exception.ActionDraftStateException;
import org.example.exception.AiNotFoundException;
import org.example.service.ActionDraftConfirmService;
import org.example.service.ActionDraftService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiDraftController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.domain=http://localhost")
class AiDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ActionDraftConfirmService confirmService;

    @MockBean
    private ActionDraftService draftService;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor buyerJwt(JwtAuthenticationConverter converter) {
        return jwt()
                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("BUYER"))))
                .authorities(jwt -> converter.convert(jwt).getAuthorities());
    }

    @Test
    void confirm_withoutJwt_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ai/drafts/{id}/confirm", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void get_withJwtReturnsCurrentOwnerScopedDraftState() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(draftService.getDetails(any(), eq(draftId))).thenReturn(new DraftDetailsResponse(
                draftId, "LEAD", DraftStatus.DRAFT, Map.of("contactName", "Ali"), null,
                Instant.parse("2026-08-11T11:00:00Z")));

        mockMvc.perform(get("/api/v1/ai/drafts/{id}", draftId)
                        .with(buyerJwt(jwtAuthenticationConverter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.draftId").value(draftId.toString()))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.payload.contactName").value("Ali"));
    }

    @Test
    void get_withoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/ai/drafts/{id}", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void confirm_withJwt_returnsConfirmedEnvelope() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(confirmService.confirm(any(), eq(draftId), any(), any(), any())).thenReturn(
                DraftActionResponse.builder().draftId(draftId).status(DraftStatus.CONFIRMED).leadId(101L).build());

        mockMvc.perform(post("/api/v1/ai/drafts/{id}/confirm", draftId)
                        .with(buyerJwt(jwtAuthenticationConverter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.leadId").value(101));
    }

    @Test
    void confirm_expiredDraft_returnsConflict() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(confirmService.confirm(any(), eq(draftId), any(), any(), any()))
                .thenThrow(new ActionDraftStateException(DraftStatus.EXPIRED, "This draft has expired."));

        mockMvc.perform(post("/api/v1/ai/drafts/{id}/confirm", draftId)
                        .with(buyerJwt(jwtAuthenticationConverter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirm_foreignDraft_returnsNotFound() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(confirmService.confirm(any(), eq(draftId), any(), any(), any()))
                .thenThrow(new AiNotFoundException("Draft not found"));

        mockMvc.perform(post("/api/v1/ai/drafts/{id}/confirm", draftId)
                        .with(buyerJwt(jwtAuthenticationConverter))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancel_withJwt_returnsCancelledEnvelope() throws Exception {
        UUID draftId = UUID.randomUUID();
        when(confirmService.cancel(any(), eq(draftId))).thenReturn(
                DraftActionResponse.builder().draftId(draftId).status(DraftStatus.CANCELLED).build());

        mockMvc.perform(post("/api/v1/ai/drafts/{id}/cancel", draftId).with(buyerJwt(jwtAuthenticationConverter)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
