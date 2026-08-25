package org.example.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.audit.ToolAuditService;
import org.example.ai.audit.ToolAuditSanitizer;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.guardrail.AiChatRateLimitService;
import org.example.ai.guardrail.TokenBudgetGuard;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.observability.AiMetrics;
import org.example.ai.prompt.SystemPromptProvider;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatMessageInput;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.provider.ChatStream;
import org.example.ai.provider.ChatStreamChunk;
import org.example.ai.provider.ModelToolCallEntry;
import org.example.ai.provider.ToolCallOutcome;
import org.example.ai.provider.ToolCallRequest;
import org.example.ai.provider.ToolExchangeEntry;
import org.example.ai.provider.ToolResultEntry;
import org.example.ai.provider.ToolSpec;
import org.example.ai.sse.SseEventPublisher;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolArgumentException;
import org.example.ai.tool.ToolArgumentValidator;
import org.example.ai.tool.ToolExecutionContext;
import org.example.ai.tool.ToolRegistry;
import org.example.ai.tool.ToolResult;
import org.example.ai.tool.UntrustedDataWrapper;
import org.example.ai.tool.impl.DraftLeadTool;
import org.example.entity.Conversation;
import org.example.entity.Message;
import org.example.entity.MessageRole;
import org.example.repository.ConversationRepository;
import org.example.repository.MessageRepository;
import org.example.service.AiChatService;
import org.example.service.ConversationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class AiChatServiceImpl implements AiChatService {

    private static final Set<String> AI_DATA_ROLES = Set.of("ADMIN", "SUPER_ADMIN", "BUYER", "SELLER");

    private static final long EMITTER_TIMEOUT_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;
    private static final float TEMPERATURE = 0.6f;
    private static final int ARG_SUMMARY_MAX_CHARS = 160;

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ChatModelProvider chatModelProvider;
    private final AiChatRateLimitService rateLimiter;
    private final TokenBudgetGuard budgetGuard;
    private final UsageLedgerService usageLedgerService;
    private final SystemPromptProvider systemPromptProvider;
    private final ToolRegistry toolRegistry;
    private final ToolAuditService toolAuditService;
    private final ObjectMapper objectMapper;
    private final ThreadPoolTaskExecutor chatExecutor;
    private final ThreadPoolTaskScheduler heartbeatScheduler;
    private final AiMetrics aiMetrics;

    private final int maxInputChars;
    private final int historyWindowMessages;
    private final int maxOutputTokens;
    private final int maxToolIterations;
    private final String chatModel;

    public AiChatServiceImpl(
            ConversationService conversationService,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            ChatModelProvider chatModelProvider,
            AiChatRateLimitService rateLimiter,
            TokenBudgetGuard budgetGuard,
            UsageLedgerService usageLedgerService,
            SystemPromptProvider systemPromptProvider,
            ToolRegistry toolRegistry,
            ToolAuditService toolAuditService,
            ObjectMapper objectMapper,
            @Qualifier("aiChatExecutor") ThreadPoolTaskExecutor chatExecutor,
            @Qualifier("aiHeartbeatScheduler") ThreadPoolTaskScheduler heartbeatScheduler,
            @Value("${ai.limits.max-input-chars:4000}") int maxInputChars,
            @Value("${ai.limits.history-window-messages:6}") int historyWindowMessages,
            @Value("${ai.limits.max-output-tokens:2048}") int maxOutputTokens,
            @Value("${ai.limits.max-tool-iterations:6}") int maxToolIterations,
            @Value("${ai.gemini.chat-model:gemini-2.5-flash}") String chatModel,
            AiMetrics aiMetrics) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.chatModelProvider = chatModelProvider;
        this.rateLimiter = rateLimiter;
        this.budgetGuard = budgetGuard;
        this.usageLedgerService = usageLedgerService;
        this.systemPromptProvider = systemPromptProvider;
        this.toolRegistry = toolRegistry;
        this.toolAuditService = toolAuditService;
        this.objectMapper = objectMapper;
        this.chatExecutor = chatExecutor;
        this.heartbeatScheduler = heartbeatScheduler;
        this.maxInputChars = maxInputChars;
        this.historyWindowMessages = Math.max(2, Math.min(historyWindowMessages, 20));
        this.maxOutputTokens = maxOutputTokens;
        this.maxToolIterations = Math.max(maxToolIterations, 0);
        this.chatModel = chatModel;
        this.aiMetrics = aiMetrics;
    }

    @Override
    public SseEmitter streamMessage(
            String userSub, UUID conversationId, String content, String acceptLanguage, String bearerToken, Set<String> callerRoles) {
        Conversation conversation = conversationService.requireOwnedForRoles(userSub, conversationId, callerRoles);

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MILLIS);
        SseEventPublisher publisher = new SseEventPublisher(emitter);
        AtomicReference<ChatStream> activeStream = new AtomicReference<>();
        AtomicBoolean disconnected = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> heartbeatHandle = new AtomicReference<>();

        Runnable cleanup = () -> {
            disconnected.set(true);
            ScheduledFuture<?> heartbeat = heartbeatHandle.get();
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            ChatStream stream = activeStream.get();
            if (stream != null) {
                stream.close();
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            cleanup.run();
            emitter.complete();
        });
        emitter.onError(throwable -> cleanup.run());

        heartbeatHandle.set(heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (!publisher.sendHeartbeat()) {
                cleanup.run();
            }
        }, Duration.ofSeconds(HEARTBEAT_INTERVAL_SECONDS)));

        Set<String> effectiveRoles = callerRoles == null ? Set.of() : callerRoles;
        chatExecutor.execute(() ->
                runTurn(userSub, conversation, content, acceptLanguage, bearerToken, effectiveRoles, emitter, publisher, activeStream, disconnected));

        return emitter;
    }

    private void runTurn(
            String userSub,
            Conversation conversation,
            String content,
            String acceptLanguage,
            String bearerToken,
            Set<String> callerRoles,
            SseEmitter emitter,
            SseEventPublisher publisher,
            AtomicReference<ChatStream> activeStream,
            AtomicBoolean disconnected) {
        long turnStart = System.currentTimeMillis();
        try {
            if (!rateLimiter.tryConsume(userSub)) {
                publisher.sendError(AiErrorCode.RATE_LIMITED, "Too many requests, please slow down.");
                aiMetrics.recordChatTurn("rate_limited", System.currentTimeMillis() - turnStart);
                emitter.complete();
                return;
            }
            String trimmed = content == null ? "" : content.trim();
            if (trimmed.isEmpty()) {
                publisher.sendError(AiErrorCode.INVALID_INPUT, "Message must not be empty.");
                aiMetrics.recordChatTurn("invalid_input", System.currentTimeMillis() - turnStart);
                emitter.complete();
                return;
            }
            if (trimmed.length() > maxInputChars) {
                publisher.sendError(AiErrorCode.INVALID_INPUT, "Message exceeds " + maxInputChars + " characters.");
                aiMetrics.recordChatTurn("invalid_input", System.currentTimeMillis() - turnStart);
                emitter.complete();
                return;
            }
            if (!budgetGuard.hasRemainingBudget(userSub)) {
                publisher.sendError(AiErrorCode.BUDGET_EXCEEDED, "Daily usage limit reached.");
                aiMetrics.recordChatTurn("budget_exceeded", System.currentTimeMillis() - turnStart);
                emitter.complete();
                return;
            }
            if (disconnected.get()) {
                return;
            }

            persistMessage(conversation.getId(), MessageRole.USER, trimmed, null, null, null, null);
            if (conversation.getTitle() == null) {
                conversation.setTitle(deriveTitle(trimmed));
            }
            conversation.touch();
            conversationRepository.save(conversation);

            String locale = resolveLocale(conversation, acceptLanguage);
            List<ChatMessageInput> history = buildHistory(conversation.getId());
            String systemInstruction = systemPromptProvider.render(locale, callerRoles);

            List<AgentTool> availableTools = toolRegistry.availableFor(callerRoles);
            List<ToolSpec> toolSpecs = availableTools.stream()
                    .map(tool -> new ToolSpec(tool.name(), tool.description(), tool.parametersSchema()))
                    .toList();
            ToolExecutionContext toolContext = new ToolExecutionContext(conversation.getId(), userSub, bearerToken, callerRoles, locale);

            StringBuilder assistantText = new StringBuilder();
            List<ToolExchangeEntry> pendingExchange = new ArrayList<>();
            long totalTokensIn = 0;
            long totalTokensOut = 0;
            int iteration = 0;
            Set<String> assistantRequiredRoles = new LinkedHashSet<>();

            while (true) {
                if (disconnected.get()) {
                    return;
                }
                boolean toolsAllowedThisCall = iteration < maxToolIterations;
                List<ToolSpec> toolsForCall = toolsAllowedThisCall ? toolSpecs : List.of();

                ChatGenerationRequest request = new ChatGenerationRequest(
                        chatModel, systemInstruction, history, toolsForCall, pendingExchange, TEMPERATURE, maxOutputTokens);

                StringBuilder roundText = new StringBuilder();
                List<ToolCallRequest> roundToolCalls = new ArrayList<>();
                long roundTokensIn = 0;
                long roundTokensOut = 0;

                long roundStart = System.currentTimeMillis();
                try (ChatStream stream = chatModelProvider.generateStream(request)) {
                    activeStream.set(stream);
                    if (disconnected.get()) {
                        return;
                    }
                    for (ChatStreamChunk chunk : stream) {
                        if (disconnected.get()) {
                            break;
                        }
                        if (chunk.usage() != null) {
                            // Gemini's streaming usage metadata is a cumulative snapshot for the
                            // current model call. It can be repeated on several chunks, so adding
                            // every chunk inflates a short reply into tens of thousands of tokens.
                            // Keep the latest/highest snapshot and charge it once per model call.
                            roundTokensIn = Math.max(roundTokensIn, chunk.usage().promptTokens());
                            roundTokensOut = Math.max(roundTokensOut, chunk.usage().candidatesTokens());
                        }
                        if (chunk.textDelta() != null && !chunk.textDelta().isEmpty()) {
                            roundText.append(chunk.textDelta());
                            assistantText.append(chunk.textDelta());
                            try {
                                publisher.sendToken(chunk.textDelta());
                            } catch (IOException clientGone) {
                                disconnected.set(true);
                                break;
                            }
                        }
                        if (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty()) {
                            roundToolCalls.addAll(chunk.toolCalls());
                        }
                    }
                    aiMetrics.recordProviderStream("ok", System.currentTimeMillis() - roundStart);
                } catch (AiChatException e) {
                    log.warn("AI chat turn failed for user (code={}): {}", e.code(), e.getMessage());
                    aiMetrics.recordProviderStream("error", System.currentTimeMillis() - roundStart);
                    publisher.sendError(e.code(), userFacingMessage(e.code()));
                    aiMetrics.recordChatTurn(e.code().name().toLowerCase(), System.currentTimeMillis() - turnStart);
                    emitter.complete();
                    return;
                }

                totalTokensIn += roundTokensIn;
                totalTokensOut += roundTokensOut;

                if (disconnected.get()) {
                    return;
                }

                if (roundToolCalls.isEmpty()) {
                    break;
                }

                pendingExchange.add(new ModelToolCallEntry(roundText.toString(), roundToolCalls));
                List<ToolCallOutcome> outcomes = new ArrayList<>(roundToolCalls.size());
                for (ToolCallRequest call : roundToolCalls) {
                    Set<String> callRequiredRoles = requiredRolesForCall(availableTools, callerRoles, call.name());
                    assistantRequiredRoles.addAll(callRequiredRoles);
                    outcomes.add(executeToolCall(conversation.getId(), userSub, availableTools, callerRoles,
                            call, toolContext, publisher, disconnected, callRequiredRoles));
                }
                pendingExchange.add(new ToolResultEntry(outcomes));
                iteration++;
            }

            int tokensIn = (int) totalTokensIn;
            int tokensOut = (int) totalTokensOut;
            Message assistantMessage = persistMessage(
                    conversation.getId(), MessageRole.ASSISTANT, assistantText.toString(), tokensIn, tokensOut,
                    null, null, rolesCsv(assistantRequiredRoles));
            usageLedgerService.recordUsage(userSub, tokensIn, tokensOut);

            publisher.sendUsage(tokensIn, tokensOut, budgetGuard.remaining(userSub));
            publisher.sendDone(assistantMessage.getId(), conversation.getId());
            aiMetrics.recordTokens(totalTokensIn, totalTokensOut);
            aiMetrics.recordChatTurn("success", System.currentTimeMillis() - turnStart);
            emitter.complete();
        } catch (Exception unexpected) {
            log.error("Unexpected failure in AI chat turn", unexpected);
            aiMetrics.recordChatTurn("provider_error", System.currentTimeMillis() - turnStart);
            try {
                publisher.sendError(AiErrorCode.PROVIDER_ERROR, "The AI service is temporarily unavailable.");
            } catch (Exception ignored) {
                // best-effort; the emitter may already be closed
            }
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already completed
            }
        }
    }

    /**
     * Validates, executes, audits, and wraps one model-requested tool call. Never throws — a
     * failed or invalid call becomes an inert {@link ToolResult#error} that the model sees as
     * data, per the system prompt's "if a tool fails, say so" policy.
     */
    private ToolCallOutcome executeToolCall(
            UUID conversationId,
            String userSub,
            List<AgentTool> availableTools,
            Set<String> callerRoles,
            ToolCallRequest call,
            ToolExecutionContext toolContext,
            SseEventPublisher publisher,
            AtomicBoolean disconnected,
            Set<String> requiredRoles) {
        String summary = summarizeArgs(call.args());
        String durableToolName = toolRegistry.findRegistered(call.name())
                .map(AgentTool::name)
                .orElse("unknown");
        trySend(() -> publisher.sendToolStart(durableToolName, summary), disconnected);

        long startedAt = System.currentTimeMillis();
        ValidatedToolResult execution = toolRegistry.findAllowed(call.name(), callerRoles)
                .map(tool -> safeExecute(tool, call.args(), toolContext))
                .orElseGet(() -> new ValidatedToolResult(
                        ToolResult.error("Unknown or unavailable tool: " + call.name(), null), false));
        ToolResult result = execution.result();
        long latencyMs = System.currentTimeMillis() - startedAt;
        aiMetrics.recordToolCall(durableToolName, result.success() ? "ok" : "error", latencyMs);

        trySend(() -> publisher.sendToolEnd(durableToolName, result.success() ? "ok" : "error"), disconnected);
        if (result.success() && DraftLeadTool.NAME.equals(call.name())) {
            trySend(() -> publisher.sendDraft(
                    String.valueOf(result.data().get("draftId")),
                    String.valueOf(result.data().get("draftType")),
                    result.data().get("draftPayload")), disconnected);
        }
        if (result.success() && result.data() != null
                && result.data().containsKey("kind") && result.data().containsKey("items")) {
            trySend(() -> publisher.sendResultSet(result.data()), disconnected);
        }

        Message toolMessage = persistMessage(
                conversationId, MessageRole.TOOL, toolMessageContent(durableToolName, result), null, null,
                durableToolName, toolPayload(durableToolName, call, result, execution.argumentsValidated()),
                rolesCsv(requiredRoles));
        toolAuditService.record(conversationId, toolMessage.getId(), userSub, durableToolName, call.args(), result,
                latencyMs, execution.argumentsValidated());

        return new ToolCallOutcome(call.callId(), call.name(), UntrustedDataWrapper.wrap(result));
    }

    private ValidatedToolResult safeExecute(
            AgentTool tool, Map<String, Object> args, ToolExecutionContext context) {
        boolean argumentsValidated = false;
        try {
            ToolArgumentValidator.validate(tool.parametersSchema(), args == null ? Map.of() : args);
            argumentsValidated = true;
            return new ValidatedToolResult(
                    tool.execute(args == null ? Map.of() : args, context), true);
        } catch (ToolArgumentException e) {
            return new ValidatedToolResult(
                    ToolResult.error("Invalid arguments: " + e.getMessage(), 400), false);
        } catch (Exception unexpected) {
            log.warn("Tool '{}' execution threw unexpectedly", tool.name(), unexpected);
            return new ValidatedToolResult(
                    ToolResult.error("Tool execution failed", null), argumentsValidated);
        }
    }

    private record ValidatedToolResult(ToolResult result, boolean argumentsValidated) {}

    private interface SseAction {
        void run() throws IOException;
    }

    private void trySend(SseAction action, AtomicBoolean disconnected) {
        try {
            action.run();
        } catch (IOException clientGone) {
            disconnected.set(true);
        }
    }

    private String toolMessageContent(String toolName, ToolResult result) {
        return result.success()
                ? toolName + " completed"
                : toolName + " failed";
    }

    private String toolPayload(
            String durableToolName, ToolCallRequest call, ToolResult result, boolean argumentsValidated) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("arguments", argumentsValidated && result.success()
                ? ToolAuditSanitizer.sanitize(durableToolName, call.args())
                : ToolAuditSanitizer.sanitizeRejected(durableToolName, call.args()));
        payload.put("status", result.success() ? "ok" : "error");
        if (result.success() && result.data() != null
                && result.data().containsKey("kind") && result.data().containsKey("items")) {
            // Conversation reads are owner-scoped. Persist the structured result for reload-safe
            // cards, but never persist live-hydrated public phone/site/address snapshots.
            payload.put("resultSet",
                    ToolAuditSanitizer.sanitizeResultSetForPersistence(objectMapper, result.data()));
        }
        if (result.success() && DraftLeadTool.NAME.equals(call.name()) && result.data() != null
                && result.data().get("draftId") != null && result.data().get("draftType") != null) {
            // Persist only an opaque owner-scoped reference. History reload obtains current status
            // and private payload through GET /ai/drafts/{id}; it never restores a stale pending copy.
            payload.put("draftRef", Map.of(
                    "draftId", String.valueOf(result.data().get("draftId")),
                    "type", String.valueOf(result.data().get("draftType"))));
        }
        return writeJson(payload);
    }

    private String summarizeArgs(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return "(no arguments)";
        }
        String joined = args.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        return joined.length() <= ARG_SUMMARY_MAX_CHARS ? joined : joined.substring(0, ARG_SUMMARY_MAX_CHARS) + "…";
    }

    private Set<String> requiredRolesForCall(
            List<AgentTool> availableTools, Set<String> callerRoles, String toolName) {
        AgentTool authorized = availableTools.stream()
                .filter(tool -> tool.name().equals(toolName))
                .findFirst()
                .orElse(null);
        if (authorized == null || authorized.allowedRoles() == null || authorized.allowedRoles().isEmpty()) {
            return Set.of();
        }
        Set<String> required = new LinkedHashSet<>();
        if (callerRoles != null) {
            callerRoles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(role -> role.toUpperCase(Locale.ROOT))
                    .filter(AI_DATA_ROLES::contains)
                    .sorted()
                    .forEach(required::add);
        }
        return Set.copyOf(required);
    }

    private String rolesCsv(Set<String> roles) {
        if (roles == null || roles.isEmpty()) return null;
        return roles.stream().sorted().reduce((left, right) -> left + "," + right).orElse(null);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Message persistMessage(
            UUID conversationId, MessageRole role, String content, Integer tokensIn, Integer tokensOut, String toolName, String toolPayload) {
        return persistMessage(conversationId, role, content, tokensIn, tokensOut, toolName, toolPayload, null);
    }

    private Message persistMessage(
            UUID conversationId, MessageRole role, String content, Integer tokensIn, Integer tokensOut,
            String toolName, String toolPayload, String requiredRoles) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setTokensIn(tokensIn);
        message.setTokensOut(tokensOut);
        message.setToolName(toolName);
        message.setToolPayload(toolPayload);
        message.setRequiredRoles(requiredRoles);
        return messageRepository.save(message);
    }

    private List<ChatMessageInput> buildHistory(UUID conversationId) {
        List<Message> recent = messageRepository.findByConversationIdAndRoleInOrderByCreatedAtDesc(
                conversationId,
                List.of(MessageRole.USER, MessageRole.ASSISTANT),
                PageRequest.of(0, historyWindowMessages));
        List<Message> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);

        List<ChatMessageInput> history = new ArrayList<>(chronological.size());
        for (Message message : chronological) {
            if (message.getRole() == MessageRole.USER) {
                history.add(new ChatMessageInput("user", message.getContent()));
            } else if (message.getRole() == MessageRole.ASSISTANT) {
                history.add(new ChatMessageInput("model", message.getContent()));
            }
        }
        return history;
    }

    private String resolveLocale(Conversation conversation, String acceptLanguage) {
        if (acceptLanguage != null && !acceptLanguage.isBlank()) {
            return acceptLanguage;
        }
        return conversation.getLocale();
    }

    private String deriveTitle(String firstMessage) {
        String singleLine = firstMessage.replaceAll("\\s+", " ").trim();
        return singleLine.length() > 60 ? singleLine.substring(0, 60) + "…" : singleLine;
    }

    private String userFacingMessage(AiErrorCode code) {
        return switch (code) {
            case RATE_LIMITED -> "The AI provider is rate-limiting requests, please try again shortly.";
            case TIMEOUT -> "The request took too long. Please try again.";
            case BUDGET_EXCEEDED -> "Daily usage limit reached.";
            case INVALID_INPUT -> "Your message is invalid.";
            case PROVIDER_ERROR -> "The AI service is temporarily unavailable.";
        };
    }
}
