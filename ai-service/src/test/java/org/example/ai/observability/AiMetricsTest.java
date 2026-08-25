package org.example.ai.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiMetricsTest {

    @Test
    void recordsChatTurnTokensToolCallAndProviderMeters() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiMetrics metrics = new AiMetrics(registry);

        metrics.recordChatTurn("success", 120);
        metrics.recordChatTurn("rate_limited", 2);
        metrics.recordTokens(100, 40);
        metrics.recordToolCall("search_products", "ok", 55);
        metrics.recordToolCall("draft_lead", "error", 12);
        metrics.recordProviderStream("ok", 300);
        metrics.recordSemanticSearch("search", "ok", 25);

        assertThat(registry.get("ai.chat.turn.duration").tag("outcome", "success").timer().count()).isEqualTo(1);
        assertThat(registry.get("ai.chat.turn.duration").tag("outcome", "rate_limited").timer().count()).isEqualTo(1);
        assertThat(registry.get("ai.chat.tokens.in").summary().totalAmount()).isEqualTo(100.0);
        assertThat(registry.get("ai.chat.tokens.out").summary().totalAmount()).isEqualTo(40.0);
        assertThat(registry.get("ai.tool.call.duration").tag("tool", "search_products").tag("status", "ok").timer().count()).isEqualTo(1);
        assertThat(registry.get("ai.tool.call.duration").tag("tool", "draft_lead").tag("status", "error").timer().count()).isEqualTo(1);
        assertThat(registry.get("ai.provider.stream.duration").tag("status", "ok").timer().count()).isEqualTo(1);
        assertThat(registry.get("ai.semantic.search.duration").tag("operation", "search").tag("status", "ok").timer().count()).isEqualTo(1);
    }
}
