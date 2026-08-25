package org.example.controller;

import org.example.ai.sse.SseEventPublisher;
import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.config.SecurityConfig;
import org.example.service.AiChatService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AiChatController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "server.domain=http://localhost")
class AiChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter jwtAuthenticationConverter;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private AiChatService aiChatService;

    @MockBean
    private AiChatRateLimitService rateLimitService;

    @Test
    void sendMessageWithoutJwtIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sendMessageWithJwt_returnsSseStreamWithNginxSafeHeaders() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(aiChatService.streamMessage(any(), eq(conversationId), any(), any(), any(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter();
            emitter.send(SseEmitter.event().name("done").data(Map.of("ok", true), MediaType.APPLICATION_JSON));
            emitter.complete();
            return emitter;
        });

        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", conversationId)
                        .with(jwt()
                                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("BUYER"))))
                                .authorities(jwt -> jwtAuthenticationConverter.convert(jwt).getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(header().string("Cache-Control", "no-cache"))
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("\"ok\":true")));
    }

    /** PLAN.md §6: {@code draft} event wire shape — {@code {"draftId","type","payload"}}. */
    @Test
    void sendMessageWithJwt_emitsDraftEventWithExactWireShape() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(aiChatService.streamMessage(any(), eq(conversationId), any(), any(), any(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter();
            SseEventPublisher publisher = new SseEventPublisher(emitter);
            publisher.sendDraft("draft-123", "LEAD", Map.of("contactName", "Ali"));
            emitter.complete();
            return emitter;
        });

        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", conversationId)
                        .with(jwt()
                                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("BUYER"))))
                                .authorities(jwt -> jwtAuthenticationConverter.convert(jwt).getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hi\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:draft")))
                .andExpect(content().string(containsString("\"draftId\":\"draft-123\"")))
                .andExpect(content().string(containsString("\"type\":\"LEAD\"")))
                .andExpect(content().string(containsString("\"contactName\":\"Ali\"")));
    }

    @Test
    void sendMessageWithJwt_emitsStructuredResultSetWireShape() throws Exception {
        UUID conversationId = UUID.randomUUID();
        when(aiChatService.streamMessage(any(), eq(conversationId), any(), any(), any(), any())).thenAnswer(invocation -> {
            SseEmitter emitter = new SseEmitter();
            SseEventPublisher publisher = new SseEventPublisher(emitter);
            publisher.sendResultSet(Map.of(
                    "kind", "business_search",
                    "items", List.of(Map.of("type", "COMPANY", "slug", "acme"))));
            emitter.complete();
            return emitter;
        });

        MvcResult result = mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", conversationId)
                        .with(jwt()
                                .jwt(builder -> builder.claim("realm_access", Map.of("roles", List.of("BUYER"))))
                                .authorities(jwt -> jwtAuthenticationConverter.convert(jwt).getAuthorities()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"find suppliers\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("event:result_set")))
                .andExpect(content().string(containsString("\"kind\":\"business_search\"")))
                .andExpect(content().string(containsString("\"slug\":\"acme\"")));
    }
}
