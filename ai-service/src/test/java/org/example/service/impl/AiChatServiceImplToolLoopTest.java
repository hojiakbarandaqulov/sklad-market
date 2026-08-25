package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.ai.audit.ToolAuditService;
import org.example.ai.observability.AiMetrics;
import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.ai.guardrail.TokenBudgetGuard;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.prompt.SystemPromptProvider;
import org.example.ai.provider.ChatCompletionResult;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.provider.ChatStream;
import org.example.ai.provider.ChatStreamChunk;
import org.example.ai.provider.TokenUsage;
import org.example.ai.provider.ToolCallRequest;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolRegistry;
import org.example.ai.tool.ToolResult;
import org.example.ai.tool.impl.DraftLeadTool;
import org.example.entity.Conversation;
import org.example.entity.Message;
import org.example.entity.MessageRole;
import org.example.repository.ConversationRepository;
import org.example.repository.MessageRepository;
import org.example.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit-level coverage of the manual function-calling loop added in Phase 2: iteration cap
 * enforcement, per-call tool auditing, and user-JWT pass-through into {@link ToolExecutionContext}
 * (PLAN.md §4.2 item 1). SSE wire content is covered separately ({@code AiChatControllerTest},
 * {@code GeminiChatModelProviderTest}) since {@link SseEmitter}'s internals aren't observable
 * without a real servlet async dispatch.
 */
class AiChatServiceImplToolLoopTest {

    private static final int MAX_TOOL_ITERATIONS = 2;
    private static final String USER_SUB = "user-sub-1";
    private static final String BEARER_TOKEN = "user-jwt-abc";

    @Test
    void runTurn_stopsAtIterationCap_auditsEachToolCall_andForwardsCallerJwtToTools() throws Exception {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_SUB);
        conversation.setUserRole("BUYER");
        conversation.setLocale("ru");

        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.requireOwnedForRoles(USER_SUB, conversationId, Set.of("BUYER"))).thenReturn(conversation);

        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findByConversationIdAndRoleInOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(UUID.randomUUID());
            }
            return message;
        });

        AiChatRateLimitService rateLimiter = mock(AiChatRateLimitService.class);
        when(rateLimiter.tryConsume(USER_SUB)).thenReturn(true);
        TokenBudgetGuard budgetGuard = mock(TokenBudgetGuard.class);
        when(budgetGuard.hasRemainingBudget(USER_SUB)).thenReturn(true);
        when(budgetGuard.remaining(USER_SUB)).thenReturn(1000L);
        UsageLedgerService usageLedgerService = mock(UsageLedgerService.class);

        SystemPromptProvider systemPromptProvider = mock(SystemPromptProvider.class);
        when(systemPromptProvider.render(any(), any())).thenReturn("system prompt");

        FakeAgentTool fakeTool = new FakeAgentTool();
        ToolRegistry toolRegistry = new ToolRegistry(List.of(fakeTool));
        ToolAuditService toolAuditService = mock(ToolAuditService.class);

        FakeChatModelProvider chatModelProvider = new FakeChatModelProvider();

        ThreadPoolTaskExecutor chatExecutor = new ThreadPoolTaskExecutor();
        chatExecutor.setThreadNamePrefix("test-ai-chat-");
        chatExecutor.initialize();
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setThreadNamePrefix("test-ai-heartbeat-");
        heartbeatScheduler.initialize();

        AiChatServiceImpl service = new AiChatServiceImpl(
                conversationService, conversationRepository, messageRepository, chatModelProvider,
                rateLimiter, budgetGuard, usageLedgerService, systemPromptProvider, toolRegistry, toolAuditService,
                new ObjectMapper(), chatExecutor, heartbeatScheduler,
                4000, 20, 2048, MAX_TOOL_ITERATIONS, "gemini-2.5-flash", new AiMetrics(new SimpleMeterRegistry()));

        service.streamMessage(USER_SUB, conversationId, "search for cement", "ru", BEARER_TOKEN, Set.of("BUYER"));

        // The turn runs on the async chatExecutor; SseEmitter's completion handler is internal
        // (package-private in spring-webmvc) so it isn't observable from here — poll instead.
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (System.currentTimeMillis() < deadline && chatModelProvider.callCount() < MAX_TOOL_ITERATIONS + 1) {
            Thread.sleep(20);
        }
        Thread.sleep(100); // let the final persistMessage/usageLedger calls after the last provider call settle

        // one extra forced-final call once the iteration cap is reached (§4.2 item 6)
        assertThat(chatModelProvider.callCount()).isEqualTo(MAX_TOOL_ITERATIONS + 1);
        assertThat(chatModelProvider.lastRequestHadNoTools()).isTrue();

        // exactly one tool execution per non-final round
        assertThat(fakeTool.invocationCount()).isEqualTo(MAX_TOOL_ITERATIONS);
        assertThat(fakeTool.lastContext().bearerToken()).isEqualTo(BEARER_TOKEN);
        assertThat(fakeTool.lastContext().userSub()).isEqualTo(USER_SUB);

        verify(toolAuditService, times(MAX_TOOL_ITERATIONS))
                .record(eq(conversationId), any(), eq(USER_SUB), eq("fake_tool"), any(), any(), anyLong(), eq(true));

        // (calls) * (10 promptTokens + 5 candidatesTokens) accumulated across every round, incl. the final one
        long expectedTokensIn = (long) (MAX_TOOL_ITERATIONS + 1) * 10;
        long expectedTokensOut = (long) (MAX_TOOL_ITERATIONS + 1) * 5;
        verify(usageLedgerService).recordUsage(USER_SUB, expectedTokensIn, expectedTokensOut);
        verify(messageRepository).findByConversationIdAndRoleInOrderByCreatedAtDesc(
                eq(conversationId), eq(List.of(MessageRole.USER, MessageRole.ASSISTANT)), any());

        ArgumentCaptor<Message> savedMessages = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(1 + MAX_TOOL_ITERATIONS + 1)).save(savedMessages.capture());
        List<MessageRole> roles = savedMessages.getAllValues().stream().map(Message::getRole).toList();
        assertThat(roles.get(0)).isEqualTo(MessageRole.USER);
        assertThat(roles.subList(1, 1 + MAX_TOOL_ITERATIONS)).containsOnly(MessageRole.TOOL);
        assertThat(roles.get(roles.size() - 1)).isEqualTo(MessageRole.ASSISTANT);
    }

    /**
     * Smoke coverage for the {@code draft} SSE hook added in Phase 4: when a successful tool result
     * carries {@code draftId}/{@code draftType}/{@code draftPayload}, the turn must not fail and
     * must still complete the loop and audit normally — the exact SSE wire shape of the resulting
     * {@code draft} event is covered separately in {@code AiChatControllerTest} (SseEmitter content
     * isn't observable outside a real servlet async dispatch, per the class javadoc above).
     */
    @Test
    void runTurn_draftProducingToolSucceeds_doesNotFailTurnAndStillAudits() throws Exception {
        UUID conversationId = UUID.randomUUID();
        Conversation conversation = new Conversation();
        conversation.setId(conversationId);
        conversation.setUserSub(USER_SUB);
        conversation.setUserRole("BUYER");
        conversation.setLocale("ru");

        ConversationService conversationService = mock(ConversationService.class);
        when(conversationService.requireOwnedForRoles(USER_SUB, conversationId, Set.of("BUYER"))).thenReturn(conversation);

        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        when(messageRepository.findByConversationIdAndRoleInOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            Message message = invocation.getArgument(0);
            if (message.getId() == null) {
                message.setId(UUID.randomUUID());
            }
            return message;
        });

        AiChatRateLimitService rateLimiter = mock(AiChatRateLimitService.class);
        when(rateLimiter.tryConsume(USER_SUB)).thenReturn(true);
        TokenBudgetGuard budgetGuard = mock(TokenBudgetGuard.class);
        when(budgetGuard.hasRemainingBudget(USER_SUB)).thenReturn(true);
        when(budgetGuard.remaining(USER_SUB)).thenReturn(1000L);
        UsageLedgerService usageLedgerService = mock(UsageLedgerService.class);

        SystemPromptProvider systemPromptProvider = mock(SystemPromptProvider.class);
        when(systemPromptProvider.render(any(), any())).thenReturn("system prompt");

        FakeDraftTool draftTool = new FakeDraftTool();
        ToolRegistry toolRegistry = new ToolRegistry(List.of(draftTool));
        ToolAuditService toolAuditService = mock(ToolAuditService.class);

        FakeDraftChatModelProvider chatModelProvider = new FakeDraftChatModelProvider();
        ThreadPoolTaskExecutor chatExecutor = new ThreadPoolTaskExecutor();
        chatExecutor.setThreadNamePrefix("test-ai-chat-draft-");
        chatExecutor.initialize();
        ThreadPoolTaskScheduler heartbeatScheduler = new ThreadPoolTaskScheduler();
        heartbeatScheduler.setThreadNamePrefix("test-ai-heartbeat-draft-");
        heartbeatScheduler.initialize();

        AiChatServiceImpl service = new AiChatServiceImpl(
                conversationService, conversationRepository, messageRepository, chatModelProvider,
                rateLimiter, budgetGuard, usageLedgerService, systemPromptProvider, toolRegistry, toolAuditService,
                new ObjectMapper(), chatExecutor, heartbeatScheduler,
                4000, 20, 2048, 1, "gemini-2.5-flash", new AiMetrics(new SimpleMeterRegistry()));

        service.streamMessage(USER_SUB, conversationId, "draft a lead", "ru", BEARER_TOKEN, Set.of("BUYER"));

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(5);
        while (System.currentTimeMillis() < deadline && chatModelProvider.callCount() < 2) {
            Thread.sleep(20);
        }
        Thread.sleep(100);

        assertThat(draftTool.invocationCount()).isEqualTo(1);
        verify(toolAuditService, times(1))
                .record(eq(conversationId), any(), eq(USER_SUB), eq(DraftLeadTool.NAME), any(), any(), anyLong(), eq(true));
        ArgumentCaptor<Message> persisted = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository, times(3)).save(persisted.capture());
        Message toolMessage = persisted.getAllValues().stream()
                .filter(message -> message.getRole() == MessageRole.TOOL)
                .findFirst()
                .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> storedPayload = new ObjectMapper().readValue(toolMessage.getToolPayload(), Map.class);
        Map<?, ?> draftRef = (Map<?, ?>) storedPayload.get("draftRef");
        assertThat(draftRef.get("draftId")).isEqualTo("draft-1");
        assertThat(draftRef.get("type")).isEqualTo("LEAD");
        assertThat(toolMessage.getRequiredRoles()).isEqualTo("BUYER");
        Message assistantMessage = persisted.getAllValues().stream()
                .filter(message -> message.getRole() == MessageRole.ASSISTANT)
                .findFirst()
                .orElseThrow();
        assertThat(assistantMessage.getRequiredRoles()).isEqualTo("BUYER");
    }

    private static final class FakeDraftTool implements AgentTool {
        private int invocations = 0;

        @Override
        public String name() {
            return DraftLeadTool.NAME;
        }

        @Override
        public String description() {
            return "Fake draft-producing tool for SSE-hook smoke coverage.";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "OBJECT", "properties", Map.of(), "required", List.of());
        }

        @Override
        public Set<String> allowedRoles() {
            return Set.of("BUYER");
        }

        @Override
        public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
            invocations++;
            return ToolResult.ok(Map.of(
                    "draftId", "draft-1",
                    "draftType", "LEAD",
                    "draftPayload", Map.of("contactName", "Ali")));
        }

        int invocationCount() {
            return invocations;
        }
    }

    /** Requests exactly one round of {@code draft_lead}, then answers final on the next call — mirrors {@link FakeChatModelProvider} but targets the draft tool's real name. */
    private static final class FakeDraftChatModelProvider implements ChatModelProvider {
        private final List<ChatGenerationRequest> requests = new ArrayList<>();

        @Override
        public ChatStream generateStream(ChatGenerationRequest request) {
            requests.add(request);
            boolean toolsOffered = !request.tools().isEmpty();
            ChatStreamChunk chunk = toolsOffered
                    ? new ChatStreamChunk("Preparing. ", new TokenUsage(10, 5, 15),
                            List.of(new ToolCallRequest("call-1", DraftLeadTool.NAME, Map.of())))
                    : new ChatStreamChunk("Drafted.", new TokenUsage(10, 5, 15), List.of());
            Iterator<ChatStreamChunk> iterator = List.of(chunk).iterator();
            return new ChatStream() {
                @Override
                public void close() {
                }

                @Override
                public Iterator<ChatStreamChunk> iterator() {
                    return iterator;
                }
            };
        }

        @Override
        public ChatCompletionResult generate(ChatGenerationRequest request) {
            throw new UnsupportedOperationException("not used by the chat turn loop");
        }

        @Override
        public org.example.ai.provider.StructuredCompletionResult generateStructured(org.example.ai.provider.StructuredGenerationRequest request) {
            throw new UnsupportedOperationException("not used by the chat turn loop");
        }

        int callCount() {
            return requests.size();
        }
    }

    private static final class FakeChatModelProvider implements ChatModelProvider {
        private final List<ChatGenerationRequest> requests = new ArrayList<>();
        private final AtomicInteger callIndex = new AtomicInteger();

        @Override
        public ChatStream generateStream(ChatGenerationRequest request) {
            requests.add(request);
            boolean toolsOffered = !request.tools().isEmpty();
            ChatStreamChunk contentChunk = toolsOffered
                    ? new ChatStreamChunk("Looking. ", new TokenUsage(10, 5, 15),
                            List.of(new ToolCallRequest("call-" + callIndex.incrementAndGet(), "fake_tool", Map.of("q", "x"))))
                    : new ChatStreamChunk("Final answer.", new TokenUsage(10, 5, 15), List.of());
            // Gemini can repeat the same cumulative usage snapshot on more than one stream
            // chunk. The chat service must charge the model call once, not once per chunk.
            ChatStreamChunk repeatedUsageChunk =
                    new ChatStreamChunk("", new TokenUsage(10, 5, 15), List.of());
            Iterator<ChatStreamChunk> iterator = List.of(contentChunk, repeatedUsageChunk).iterator();
            return new ChatStream() {
                @Override
                public void close() {
                }

                @Override
                public Iterator<ChatStreamChunk> iterator() {
                    return iterator;
                }
            };
        }

        @Override
        public ChatCompletionResult generate(ChatGenerationRequest request) {
            throw new UnsupportedOperationException("not used by the chat turn loop");
        }

        @Override
        public org.example.ai.provider.StructuredCompletionResult generateStructured(org.example.ai.provider.StructuredGenerationRequest request) {
            throw new UnsupportedOperationException("not used by the chat turn loop");
        }

        int callCount() {
            return requests.size();
        }

        boolean lastRequestHadNoTools() {
            return requests.get(requests.size() - 1).tools().isEmpty();
        }
    }

    private static final class FakeAgentTool implements AgentTool {
        private final List<ToolExecutionContext> contexts = new ArrayList<>();

        @Override
        public String name() {
            return "fake_tool";
        }

        @Override
        public String description() {
            return "A fake tool for loop testing.";
        }

        @Override
        public Map<String, Object> parametersSchema() {
            return Map.of("type", "OBJECT", "properties", Map.of("q", Map.of("type", "STRING")), "required", List.of());
        }

        @Override
        public Set<String> allowedRoles() {
            return Set.of();
        }

        @Override
        public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
            contexts.add(context);
            return ToolResult.ok(Map.of("echo", args));
        }

        int invocationCount() {
            return contexts.size();
        }

        ToolExecutionContext lastContext() {
            return contexts.get(contexts.size() - 1);
        }
    }
}
