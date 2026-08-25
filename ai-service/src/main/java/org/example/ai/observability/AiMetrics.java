package org.example.ai.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Thin Micrometer facade for the AI-specific signals the auto {@code http.server.requests} metrics
 * don't capture (PLAN.md Phase 7 observability): chat-turn latency/outcome, per-turn token usage,
 * per-tool call latency + error rate, and provider streaming latency. All dynamic tags
 * ({@code tool}, {@code outcome}, {@code status}) are bounded low-cardinality sets — the tool
 * registry is a fixed allowlist and outcomes/statuses are enum-like — so this can't explode the
 * registry. Everything is exposed via the auth-gated {@code /actuator/metrics} + {@code /prometheus}.
 */
@Component
public class AiMetrics {

    private final MeterRegistry registry;
    private final DistributionSummary tokensIn;
    private final DistributionSummary tokensOut;

    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.tokensIn = DistributionSummary.builder("ai.chat.tokens.in")
                .baseUnit("tokens").description("Prompt (input) tokens per completed chat turn").register(registry);
        this.tokensOut = DistributionSummary.builder("ai.chat.tokens.out")
                .baseUnit("tokens").description("Candidate (output) tokens per completed chat turn").register(registry);
    }

    /** One completed (or failed) chat turn. {@code outcome} is one of success / rate_limited / budget_exceeded / invalid_input / provider_error / timeout / error. */
    public void recordChatTurn(String outcome, long durationMillis) {
        Timer.builder("ai.chat.turn.duration")
                .description("Wall-clock duration of a chat turn, tagged by final outcome")
                .tag("outcome", outcome)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** Token usage for a successfully completed turn. */
    public void recordTokens(long promptTokens, long candidateTokens) {
        tokensIn.record(promptTokens);
        tokensOut.record(candidateTokens);
    }

    /** One tool invocation. {@code status} is {@code ok} or {@code error}; the tool-error rate is the ratio over this timer's counts. */
    public void recordToolCall(String tool, String status, long latencyMillis) {
        Timer.builder("ai.tool.call.duration")
                .description("Per-tool execution latency, tagged by tool name and ok/error status")
                .tag("tool", tool)
                .tag("status", status)
                .register(registry)
                .record(latencyMillis, TimeUnit.MILLISECONDS);
    }

    /** One provider streaming round (a single generateStream call within a turn's tool loop). */
    public void recordProviderStream(String status, long durationMillis) {
        Timer.builder("ai.provider.stream.duration")
                .description("Gemini streaming-generation latency per round, tagged by ok/error status")
                .tag("status", status)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** One direct semantic REST request; operation/status are fixed, low-cardinality values. */
    public void recordSemanticSearch(String operation, String status, long durationMillis) {
        Timer.builder("ai.semantic.search.duration")
                .description("Direct semantic search endpoint latency, tagged by operation and status")
                .tag("operation", operation)
                .tag("status", status)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** Business discovery and recommendation endpoints; operation/status are bounded values. */
    public void recordBusinessDiscovery(String operation, String status, long durationMillis) {
        Timer.builder("ai.business.discovery.duration")
                .description("Business search and recommendation latency")
                .tag("operation", operation)
                .tag("status", status)
                .register(registry)
                .record(durationMillis, TimeUnit.MILLISECONDS);
    }
}
