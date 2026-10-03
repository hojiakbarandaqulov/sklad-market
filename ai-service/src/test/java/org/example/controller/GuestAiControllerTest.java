package org.example.controller;

import org.example.ai.guest.*;
import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.config.SecurityConfig;
import org.example.service.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({GuestAiController.class, GuestAiAdminController.class, AiChatController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties="server.domain=http://localhost")
class GuestAiControllerTest {
    @Autowired MockMvc mvc;
    @MockBean JwtDecoder decoder;
    @MockBean GuestTrialStore store;
    @MockBean GuestAnswerService answers;
    @MockBean GuestClientAddress addresses;
    @MockBean AiChatService accountChat;
    @MockBean AiChatRateLimitService rateLimits;
    @MockBean(name="aiGuestExecutor") ThreadPoolTaskExecutor executor;

    @Test void anonymousStatusDoesNotCreateConversationOrInvokeModel() throws Exception {
        when(store.status(null)).thenReturn(Map.of("remaining", 5));
        mvc.perform(get("/api/v1/ai/guest/status")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.remaining").value(5));
        verifyNoInteractions(answers, accountChat);
        verify(executor, never()).execute(any(Runnable.class));
    }
    @Test void guestCannotUseAccountChatOrAdminControls() throws Exception {
        mvc.perform(post("/api/v1/ai/conversations/" + UUID.randomUUID() + "/messages")
                .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ai/admin/guest-trial")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/ai/admin/guest-trial").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER")))
                .contentType("application/json").content("{\"messageLimit\":20}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(answers, accountChat);
        verify(executor, never()).execute(any(Runnable.class));
    }
    @Test void exhaustedTrialNeverReachesAiExecutor() throws Exception {
        when(store.claim(any(), any(), any(), any())).thenThrow(new GuestTrialException("guest_limit_reached", 429));
        mvc.perform(post("/api/v1/ai/guest/messages").header("X-AI-Guest-Token", "test")
                .contentType("application/json").content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"content\":\"hello\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("X-AI-Error-Code", "guest_limit_reached"));
        verifyNoInteractions(answers);
        verify(executor, never()).execute(any(Runnable.class));
    }
    @Test void onlyAdminsCanSaveBoundedPolicy() throws Exception {
        for (String role : new String[]{"ADMIN", "SUPER_ADMIN"}) {
            when(store.update(eq(3), any())).thenReturn(new GuestTrialStore.Policy(3));
            mvc.perform(put("/api/v1/ai/admin/guest-trial").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role)))
                    .contentType("application/json").content("{\"messageLimit\":3}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.messageLimit").value(3));
        }
        mvc.perform(put("/api/v1/ai/admin/guest-trial").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content("{\"messageLimit\":21}"))
                .andExpect(status().isBadRequest());
    }

    @Test void anonymousMessageCompletesWithoutUsingAnAccountConversation() throws Exception {
        UUID id = UUID.randomUUID();
        var claim = new GuestTrialStore.Claim("hash", id, "hello", "PENDING", null);
        when(addresses.resolve(any())).thenReturn("192.0.2.1");
        when(store.claim("token", id, "hello", "192.0.2.1")).thenReturn(claim);
        when(store.context("hash")).thenReturn(List.of());
        when(store.status("token")).thenReturn(Map.of("remaining", 4, "messages", List.of()));
        when(answers.answer(eq(claim), anyList(), eq("EN"), any(), any()))
                .thenReturn(Map.of("text", "Hello!", "resultSets", List.of()));
        doAnswer(invocation -> { invocation.getArgument(0, Runnable.class).run(); return null; })
                .when(executor).execute(any(Runnable.class));
        var result = mvc.perform(post("/api/v1/ai/guest/messages")
                .header("X-AI-Guest-Token", "token").header("Accept-Language", "EN")
                .contentType("application/json").content("{\"requestId\":\"" + id + "\",\"content\":\"hello\"}"))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.answer.text").value("Hello!"))
                .andExpect(jsonPath("$.data.remaining").value(4));
        verify(store).complete(eq(claim), anyMap());
        verifyNoInteractions(accountChat);
    }
}
