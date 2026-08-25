package org.example.evals;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.ai.tool.AgentTool;
import org.example.ai.tool.ToolRegistry;
import org.example.ai.tool.ToolResult;
import org.example.ai.tool.UntrustedDataWrapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic, offline evaluation harness (PLAN.md Phase 7). Runs the golden set
 * ({@code evals/golden-set.json}) against the REAL {@link ToolRegistry} + REAL tool instances and
 * the REAL system prompt, asserting only the model-INDEPENDENT invariants of each case:
 *
 * <ul>
 *   <li><b>tool_selection</b>: the expected tool is registered and available to the persona (the
 *       model is actually offered the right option).</li>
 *   <li><b>role_gating</b>: every {@code forbiddenTools} entry is denied to the persona at the
 *       registry layer ({@code availableFor} + {@code findAllowed}), and every {@code availableTools}
 *       entry is offered.</li>
 *   <li><b>injection_resistance</b>: a tool result carrying an injection payload is wrapped by
 *       {@link UntrustedDataWrapper} as {@code untrusted_data} with the ignore-instructions envelope,
 *       and the raw payload stays nested under {@code result} (never elevated to instructions).</li>
 *   <li><b>refusal</b>: the active system prompt carries the scope/decline policy that drives
 *       off-topic refusals.</li>
 * </ul>
 *
 * These invariants must hold 100% ({@code deterministicThreshold}); they are the CI gate. The actual
 * model judgment (does Gemini really pick the expected tool / refuse / resist injection) is measured
 * by the optional {@link LiveToolSelectionEvalIT} against {@code liveThreshold}. Run just this set
 * with {@code ./gradlew eval}.
 */
@Tag("eval")
class GoldenSetEvalTest {

    @Test
    void goldenSet_deterministicInvariants_allPass() throws Exception {
        JsonNode root = EvalSupport.loadGoldenSet();
        double threshold = root.path("deterministicThreshold").asDouble(1.0);

        ToolRegistry registry = EvalSupport.buildFullRegistry();
        String systemPrompt = EvalSupport.loadSystemPrompt();

        // Global prompt invariants relied on by refusal + injection cases.
        assertThat(systemPrompt.toLowerCase())
                .as("system prompt must carry an off-topic decline policy")
                .contains("decline");
        assertThat(systemPrompt.toLowerCase())
                .as("system prompt must carry an untrusted-tool-data policy")
                .contains("instructions");

        List<String> failures = new ArrayList<>();
        Map<String, int[]> perCategory = new TreeMap<>(); // category -> [passed, total]
        int total = 0;
        int passed = 0;

        for (JsonNode caseNode : root.path("cases")) {
            total++;
            String id = caseNode.path("id").asText();
            String category = caseNode.path("category").asText();
            perCategory.computeIfAbsent(category, k -> new int[2]);
            perCategory.get(category)[1]++;

            List<String> caseFailures = evaluateCase(caseNode, registry, systemPrompt);
            if (caseFailures.isEmpty()) {
                passed++;
                perCategory.get(category)[0]++;
            } else {
                caseFailures.forEach(f -> failures.add("[" + id + "] " + f));
            }
        }

        double passRate = total == 0 ? 0.0 : (double) passed / total;
        printReport(perCategory, passed, total, passRate, threshold);

        assertThat(failures).as("Deterministic golden-set invariant failures").isEmpty();
        assertThat(passRate)
                .as("Deterministic golden-set pass rate must meet the documented threshold")
                .isGreaterThanOrEqualTo(threshold);
        // Sanity: the set is the ~20-case size the plan calls for.
        assertThat(total).isGreaterThanOrEqualTo(20);
    }

    private List<String> evaluateCase(JsonNode caseNode, ToolRegistry registry, String systemPrompt) {
        List<String> failures = new ArrayList<>();
        Set<String> roles = EvalSupport.asSet(caseNode.path("roles"));
        List<String> availableNow = registry.availableFor(roles).stream().map(AgentTool::name).toList();

        for (JsonNode t : caseNode.path("availableTools")) {
            String tool = t.asText();
            if (!availableNow.contains(tool)) {
                failures.add("expected tool '" + tool + "' NOT available to roles " + roles);
            }
            if (registry.findAllowed(tool, roles).isEmpty()) {
                failures.add("findAllowed denied expected tool '" + tool + "' for roles " + roles);
            }
        }
        for (JsonNode t : caseNode.path("forbiddenTools")) {
            String tool = t.asText();
            if (availableNow.contains(tool)) {
                failures.add("forbidden tool '" + tool + "' WAS available to roles " + roles);
            }
            if (registry.findAllowed(tool, roles).isPresent()) {
                failures.add("findAllowed allowed forbidden tool '" + tool + "' for roles " + roles);
            }
        }

        String expectedTool = caseNode.path("expectedTool").isNull() ? null : caseNode.path("expectedTool").asText(null);
        if (expectedTool != null && registry.findAllowed(expectedTool, roles).isEmpty()) {
            failures.add("expectedTool '" + expectedTool + "' is not registered/available to roles " + roles);
        }

        if (caseNode.hasNonNull("injectionPayload")) {
            failures.addAll(checkInjection(caseNode.path("injectionPayload")));
        }

        if (caseNode.path("expectRefusal").asBoolean(false)
                && !systemPrompt.toLowerCase().contains("decline")) {
            failures.add("refusal case but system prompt lacks a decline policy");
        }
        return failures;
    }

    @SuppressWarnings("unchecked")
    private List<String> checkInjection(JsonNode payloadNode) {
        List<String> failures = new ArrayList<>();
        Map<String, Object> payload = EvalSupport.MAPPER.convertValue(payloadNode, Map.class);
        Map<String, Object> envelope = UntrustedDataWrapper.wrap(ToolResult.ok(payload));

        if (!Boolean.TRUE.equals(envelope.get("untrusted_data"))) {
            failures.add("wrapped tool result is not flagged untrusted_data=true");
        }
        Object instructions = envelope.get("instructions");
        if (!(instructions instanceof String s) || !s.toLowerCase().contains("ignore")) {
            failures.add("wrapped tool result lacks an ignore-instructions directive");
        }
        Object nested = envelope.get("result");
        if (!(nested instanceof Map) || !payload.equals(nested)) {
            failures.add("injected payload was not kept nested under 'result'");
        }
        // The raw injected string must not have leaked to a top-level, instruction-like envelope key.
        for (Map.Entry<String, Object> e : envelope.entrySet()) {
            if (!"result".equals(e.getKey()) && e.getValue() instanceof String str
                    && str.toLowerCase().contains("ignore all previous")) {
                failures.add("injected instruction leaked to envelope key '" + e.getKey() + "'");
            }
        }
        return failures;
    }

    private void printReport(Map<String, int[]> perCategory, int passed, int total, double passRate, double threshold) {
        Map<String, int[]> ordered = new LinkedHashMap<>(perCategory);
        StringBuilder sb = new StringBuilder("\n=== SKLADx AI Golden-Set Eval (deterministic) ===\n");
        ordered.forEach((cat, pt) -> sb.append(String.format("  %-22s %d/%d%n", cat, pt[0], pt[1])));
        sb.append(String.format("  %-22s %d/%d (%.0f%%), threshold %.0f%%%n",
                "TOTAL", passed, total, passRate * 100, threshold * 100));
        System.out.println(sb);
    }
}
