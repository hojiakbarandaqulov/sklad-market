package org.example.evals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.ai.provider.ChatGenerationRequest;
import org.example.ai.provider.ChatMessageInput;
import org.example.ai.provider.ChatStream;
import org.example.ai.provider.ChatStreamChunk;
import org.example.ai.provider.ToolCallRequest;
import org.example.ai.provider.ToolSpec;
import org.example.ai.provider.gemini.GeminiChatModelProvider;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPTIONAL live model-judgment eval (PLAN.md Phase 7). Self-skips unless {@code AI_EVAL_LIVE=true}
 * (and a real {@code GEMINI_API_KEY} is present) so CI stays deterministic and offline. It sends the
 * golden set's {@code tool_selection} and {@code refusal} prompts to the real Gemini model with the
 * persona's real tool declarations and measures whether the model actually picks the expected tool
 * (or correctly declines with no tool call). Role-gating and injection-resistance are NOT re-checked
 * here — they are server-side guarantees fully covered by {@link GoldenSetEvalTest}.
 *
 * <p>Documented threshold: accuracy over the live-scored subset must be &ge; {@code liveThreshold}
 * (0.85 in {@code golden-set.json}). Model output is inherently non-deterministic, so this is a
 * quality signal run on demand, never a blocking CI gate.
 */
@Tag("eval")
@EnabledIfEnvironmentVariable(named = "AI_EVAL_LIVE", matches = "true")
class LiveToolSelectionEvalIT {

    @Test
    void liveModel_toolSelectionAndRefusal_meetsThreshold() throws Exception {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY must be set to run the live eval").isNotBlank();
        String model = System.getenv().getOrDefault("AI_CHAT_MODEL", "gemini-2.5-flash");

        GeminiChatModelProvider provider =
                new GeminiChatModelProvider(apiKey, 60, "", new ObjectMapper());
        ToolRegistry registry = EvalSupport.buildFullRegistry();
        String systemPrompt = EvalSupport.loadSystemPrompt();

        JsonNode root = EvalSupport.loadGoldenSet();
        double threshold = root.path("liveThreshold").asDouble(0.85);

        int scored = 0;
        int correct = 0;
        StringBuilder log = new StringBuilder("\n=== SKLADx AI Live Eval (Gemini " + model + ") ===\n");

        for (JsonNode caseNode : root.path("cases")) {
            String category = caseNode.path("category").asText();
            if (!"tool_selection".equals(category) && !"refusal".equals(category)) {
                continue; // role_gating + injection are covered deterministically
            }
            scored++;
            Set<String> roles = EvalSupport.asSet(caseNode.path("roles"));
            String userMessage = caseNode.path("userMessage").asText();
            List<ToolSpec> tools = registry.availableFor(roles).stream()
                    .map(t -> new ToolSpec(t.name(), t.description(), t.parametersSchema()))
                    .toList();

            String firstTool = firstToolCall(provider, model, systemPrompt, userMessage, tools);

            boolean pass;
            if ("refusal".equals(category)) {
                pass = firstTool == null; // a correct refusal calls no tool
            } else {
                Set<String> acceptable = EvalSupport.asSet(caseNode.path("availableTools"));
                pass = firstTool != null && acceptable.contains(firstTool);
            }
            if (pass) {
                correct++;
            }
            log.append(String.format("  %-28s %-16s -> %-24s %s%n",
                    caseNode.path("id").asText(), category, firstTool == null ? "(no tool / text)" : firstTool,
                    pass ? "PASS" : "FAIL"));
        }

        double accuracy = scored == 0 ? 0.0 : (double) correct / scored;
        log.append(String.format("  TOTAL %d/%d (%.0f%%), threshold %.0f%%%n",
                correct, scored, accuracy * 100, threshold * 100));
        System.out.println(log);

        assertThat(accuracy)
                .as("Live model tool-selection/refusal accuracy must meet the documented threshold")
                .isGreaterThanOrEqualTo(threshold);
    }

    /** Runs one streamed generation and returns the first tool the model requests, or null if it only produced text. */
    private String firstToolCall(GeminiChatModelProvider provider, String model, String systemPrompt,
                                 String userMessage, List<ToolSpec> tools) throws Exception {
        ChatGenerationRequest request = new ChatGenerationRequest(
                model, systemPrompt, List.of(new ChatMessageInput("user", userMessage)),
                tools, List.of(), 0.6f, 2048);
        try (ChatStream stream = provider.generateStream(request)) {
            for (ChatStreamChunk chunk : stream) {
                if (chunk.toolCalls() != null && !chunk.toolCalls().isEmpty()) {
                    ToolCallRequest call = chunk.toolCalls().get(0);
                    return call.name();
                }
            }
        }
        return null;
    }
}
