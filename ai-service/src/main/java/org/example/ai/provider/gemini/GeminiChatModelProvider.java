package org.example.ai.provider.gemini;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.ResponseStream;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.ClientException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.errors.ServerException;
import com.google.genai.types.AutomaticFunctionCallingConfig;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.FunctionResponse;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import com.google.genai.types.Part;
import com.google.genai.types.Schema;
import com.google.genai.types.Tool;
import lombok.extern.slf4j.Slf4j;
import org.example.ai.error.AiChatException;
import org.example.ai.error.AiErrorCode;
import org.example.ai.provider.ChatCompletionResult;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatMessageInput;
import org.example.ai.provider.ChatModelProvider;
import org.example.ai.provider.ChatStream;
import org.example.ai.provider.ChatStreamChunk;
import org.example.ai.provider.ImagePart;
import org.example.ai.provider.ModelToolCallEntry;
import org.example.ai.provider.StructuredCompletionResult;
import org.example.ai.provider.StructuredGenerationRequest;
import org.example.ai.provider.ToolCallOutcome;
import org.example.ai.provider.ToolCallRequest;
import org.example.ai.provider.ToolExchangeEntry;
import org.example.ai.provider.ToolResultEntry;
import org.example.ai.provider.ToolSpec;
import org.example.ai.provider.TokenUsage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Official {@code com.google.genai:google-genai} SDK (verified 1.60.0). Applies PLAN.md §7
 * items 1-3:
 * <ol>
 *   <li>the SDK's default retry (5 attempts, blocking backoff, {@link RuntimeException} that
 *       loses the HTTP status) is disabled via {@code HttpRetryOptions.attempts(1)}; this class
 *       implements its own single retry with jitter, preserving the real status code;</li>
 *   <li>batching/embeddings are out of scope for Phase 1;</li>
 *   <li>every method name below was read from the actual 1.60.0 sources, not guessed.</li>
 * </ol>
 * The {@link Client} is built lazily on first use so the service can still boot (and pass health
 * checks) when {@code GEMINI_API_KEY} is unset — the failure surfaces as a typed
 * {@link AiErrorCode#PROVIDER_ERROR} on the first chat turn instead of an application-context
 * startup failure.
 */
@Slf4j
@Component
public class GeminiChatModelProvider implements ChatModelProvider {

    private static final List<Integer> RETRYABLE_HTTP_STATUS = List.of(408, 429, 500, 502, 503, 504);
    private static final long RETRY_BASE_DELAY_MILLIS = 300L;
    private static final long RETRY_JITTER_MILLIS = 400L;

    private final String apiKey;
    private final int timeoutSeconds;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    private volatile Client client;
    private final Object clientLock = new Object();

    public GeminiChatModelProvider(
            @Value("${ai.gemini.api-key:}") String apiKey,
            @Value("${ai.limits.request-timeout-seconds:60}") int timeoutSeconds,
            @Value("${ai.gemini.base-url:}") String baseUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.timeoutSeconds = timeoutSeconds;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
    }

    @Override
    public ChatStream generateStream(ChatGenerationRequest request) {
        Client activeClient = client();
        List<Content> contents = toContents(request);
        GenerateContentConfig config = toConfig(request);

        ResponseStream<GenerateContentResponse> responseStream =
                withSingleRetry(() -> activeClient.models.generateContentStream(request.model(), contents, config));
        return new GeminiChatStream(responseStream);
    }

    @Override
    public ChatCompletionResult generate(ChatGenerationRequest request) {
        Client activeClient = client();
        List<Content> contents = toContents(request);
        GenerateContentConfig config = toConfig(request);

        GenerateContentResponse response =
                withSingleRetry(() -> activeClient.models.generateContent(request.model(), contents, config));
        return new ChatCompletionResult(
                Optional.ofNullable(response.text()).orElse(""), toTokenUsage(response.usageMetadata().orElse(null)));
    }

    @Override
    public StructuredCompletionResult generateStructured(StructuredGenerationRequest request) {
        Client activeClient = client();

        List<Part> parts = new ArrayList<>();
        if (request.userText() != null && !request.userText().isBlank()) {
            parts.add(Part.fromText(request.userText()));
        }
        if (request.images() != null) {
            for (ImagePart image : request.images()) {
                parts.add(Part.fromBytes(image.data(), image.mimeType()));
            }
        }
        List<Content> contents = List.of(Content.builder().role("user").parts(parts).build());

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .temperature(request.temperature())
                .maxOutputTokens(request.maxOutputTokens())
                .responseMimeType("application/json")
                .responseSchema(Schema.fromJson(writeJson(request.responseSchema())));
        if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
            configBuilder.systemInstruction(Content.builder().parts(List.of(Part.fromText(request.systemInstruction()))).build());
        }

        GenerateContentResponse response =
                withSingleRetry(() -> activeClient.models.generateContent(request.model(), contents, configBuilder.build()));
        String json = Optional.ofNullable(response.text()).orElse("{}");
        return new StructuredCompletionResult(json, toTokenUsage(response.usageMetadata().orElse(null)));
    }

    private Client client() {
        Client existing = client;
        if (existing != null) {
            return existing;
        }
        synchronized (clientLock) {
            if (client == null) {
                if (apiKey == null || apiKey.isBlank()) {
                    throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "AI provider is not configured");
                }
                HttpOptions.Builder httpOptionsBuilder =
                        HttpOptions.builder()
                                .retryOptions(HttpRetryOptions.builder().attempts(1).build())
                                .timeout(timeoutSeconds * 1000);
                if (baseUrl != null && !baseUrl.isBlank()) {
                    httpOptionsBuilder.baseUrl(baseUrl);
                }
                client = Client.builder().apiKey(apiKey).httpOptions(httpOptionsBuilder.build()).build();
            }
            return client;
        }
    }

    private <T> T withSingleRetry(Supplier<T> call) {
        try {
            return call.get();
        } catch (RuntimeException first) {
            if (!isRetryable(first)) {
                throw mapException(first);
            }
            log.warn("Gemini call failed, retrying once: {}", first.getMessage());
            sleepWithJitter();
            try {
                return call.get();
            } catch (RuntimeException second) {
                throw mapException(second);
            }
        }
    }

    private boolean isRetryable(RuntimeException e) {
        if (e instanceof ServerException) {
            return true;
        }
        if (e instanceof ClientException clientException) {
            return RETRYABLE_HTTP_STATUS.contains(clientException.code());
        }
        return e instanceof GenAiIOException;
    }

    private AiChatException mapException(RuntimeException e) {
        if (e instanceof ClientException clientException) {
            if (clientException.code() == 429) {
                return new AiChatException(AiErrorCode.RATE_LIMITED, "The AI provider is rate-limiting requests", e);
            }
            return new AiChatException(
                    AiErrorCode.PROVIDER_ERROR, "The AI provider rejected the request (" + clientException.code() + ")", e);
        }
        if (e instanceof ApiException apiException) {
            return new AiChatException(
                    AiErrorCode.PROVIDER_ERROR, "The AI provider returned an error (" + apiException.code() + ")", e);
        }
        if (e instanceof GenAiIOException && isTimeout(e.getCause())) {
            return new AiChatException(AiErrorCode.TIMEOUT, "The AI provider timed out", e);
        }
        return new AiChatException(AiErrorCode.PROVIDER_ERROR, "The AI provider is temporarily unavailable", e);
    }

    private boolean isTimeout(Throwable cause) {
        return cause instanceof InterruptedIOException || cause instanceof java.net.SocketTimeoutException;
    }

    private void sleepWithJitter() {
        try {
            long delay = RETRY_BASE_DELAY_MILLIS + (long) (Math.random() * RETRY_JITTER_MILLIS);
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiChatException(AiErrorCode.PROVIDER_ERROR, "Interrupted while retrying the AI provider", interrupted);
        }
    }

    private List<Content> toContents(ChatGenerationRequest request) {
        List<ChatMessageInput> history = request.history();
        List<Content> contents = new ArrayList<>(history.size() + request.pendingToolExchange().size());
        for (ChatMessageInput turn : history) {
            contents.add(Content.builder().role(turn.role()).parts(List.of(Part.fromText(turn.text()))).build());
        }
        for (ToolExchangeEntry entry : request.pendingToolExchange()) {
            contents.add(toContent(entry));
        }
        return contents;
    }

    /**
     * Mirrors the SDK's own automatic-function-calling loop (decompiled from {@code Models.class}):
     * the model's function-call turn keeps role {@code "model"}; the reply carrying
     * {@link com.google.genai.types.FunctionResponse} parts uses role {@code "user"} — there is no
     * separate "function" role in this API.
     */
    private Content toContent(ToolExchangeEntry entry) {
        if (entry instanceof ModelToolCallEntry modelCall) {
            List<Part> parts = new ArrayList<>();
            if (modelCall.text() != null && !modelCall.text().isBlank()) {
                parts.add(Part.fromText(modelCall.text()));
            }
            for (ToolCallRequest call : modelCall.calls()) {
                FunctionCall functionCall = FunctionCall.builder()
                        .id(call.callId())
                        .name(call.name())
                        .args(call.args())
                        .build();
                Part.Builder part = Part.builder().functionCall(functionCall);
                if (call.continuationSignature() != null) {
                    // Gemini 3 function calls carry an opaque thought signature. It must be
                    // replayed byte-for-byte on the same Part when sending the tool result.
                    part.thoughtSignature(call.continuationSignature());
                }
                parts.add(part.build());
            }
            return Content.builder().role("model").parts(parts).build();
        }
        ToolResultEntry toolResult = (ToolResultEntry) entry;
        List<Part> parts = toolResult.outcomes().stream()
                .map(outcome -> Part.builder()
                        .functionResponse(FunctionResponse.builder()
                                .id(outcome.callId())
                                .name(outcome.name())
                                .response(outcome.responsePayload())
                                .build())
                        .build())
                .toList();
        return Content.builder().role("user").parts(parts).build();
    }

    private GenerateContentConfig toConfig(ChatGenerationRequest request) {
        GenerateContentConfig.Builder builder =
                GenerateContentConfig.builder()
                        .temperature(request.temperature())
                        .maxOutputTokens(request.maxOutputTokens());
        if (request.systemInstruction() != null && !request.systemInstruction().isBlank()) {
            builder.systemInstruction(Content.builder().parts(List.of(Part.fromText(request.systemInstruction()))).build());
        }
        if (request.tools() != null && !request.tools().isEmpty()) {
            List<FunctionDeclaration> declarations = request.tools().stream().map(this::toFunctionDeclaration).toList();
            builder.tools(List.of(Tool.builder().functionDeclarations(declarations).build()));
            // Explicit per PLAN.md Phase 2: this is a *manual* function-calling loop, never the
            // SDK's own automatic one (which in this SDK version only engages for reflective
            // Tool.functions(Method...) declarations anyway — we never populate that field).
            builder.automaticFunctionCalling(AutomaticFunctionCallingConfig.builder().disable(true).build());
        }
        return builder.build();
    }

    private FunctionDeclaration toFunctionDeclaration(ToolSpec spec) {
        Schema schema = Schema.fromJson(writeJson(spec.parametersSchema()));
        return FunctionDeclaration.builder()
                .name(spec.name())
                .description(spec.description())
                .parameters(schema)
                .build();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ToolCallRequest toToolCallRequest(Part part) {
        FunctionCall functionCall = part.functionCall().orElseThrow();
        String callId = functionCall.id().orElseGet(() -> java.util.UUID.randomUUID().toString());
        String name = functionCall.name().orElse("");
        Map<String, Object> args = functionCall.args().orElse(Map.of());
        return new ToolCallRequest(callId, name, args, part.thoughtSignature().orElse(null));
    }

    private static TokenUsage toTokenUsage(GenerateContentResponseUsageMetadata usage) {
        if (usage == null) {
            return new TokenUsage(0, 0, 0);
        }
        return new TokenUsage(
                usage.promptTokenCount().orElse(0),
                usage.candidatesTokenCount().orElse(0),
                usage.totalTokenCount().orElse(0));
    }

    /** Adapts the SDK's blocking {@link ResponseStream} to our vendor-neutral {@link ChatStream}. */
    private final class GeminiChatStream implements ChatStream {

        private final ResponseStream<GenerateContentResponse> delegate;

        private GeminiChatStream(ResponseStream<GenerateContentResponse> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() {
            try {
                delegate.close();
            } catch (RuntimeException e) {
                log.debug("Error closing Gemini response stream (likely already closed): {}", e.getMessage());
            }
        }

        @Override
        public Iterator<ChatStreamChunk> iterator() {
            Iterator<GenerateContentResponse> source = delegate.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    try {
                        return source.hasNext();
                    } catch (RuntimeException e) {
                        throw mapException(e);
                    }
                }

                @Override
                public ChatStreamChunk next() {
                    GenerateContentResponse chunk;
                    try {
                        chunk = source.next();
                    } catch (NoSuchElementException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        throw mapException(e);
                    }
                    String text = chunk.text();
                    TokenUsage usage = chunk.usageMetadata().map(GeminiChatModelProvider::toTokenUsage).orElse(null);
                    // Read function calls from their Parts instead of response.functionCalls().
                    // The convenience method discards Gemini 3's required thoughtSignature.
                    List<Part> rawParts = chunk.parts();
                    List<ToolCallRequest> toolCalls = rawParts == null ? List.of()
                            : rawParts.stream()
                                    .filter(part -> part.functionCall().isPresent())
                                    .map(GeminiChatModelProvider::toToolCallRequest)
                                    .toList();
                    return new ChatStreamChunk(text, usage, toolCalls);
                }
            };
        }
    }
}
