package org.example.ai.guest;

import org.example.ai.LocaleNormalizer;
import org.example.ai.business.tool.SearchBusinessesTool;
import org.example.ai.guardrail.ChatContextWindow;
import org.example.ai.guardrail.UsageLedgerService;
import org.example.ai.prompt.SystemPromptProvider;
import org.example.ai.provider.*;
import org.example.ai.tool.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Deliberately separate from account chat: one public search capability, no account tools/drafts. */
@Service
public class GuestAnswerService {
    private final ChatModelProvider provider;
    private final SearchBusinessesTool publicSearch;
    private final SystemPromptProvider prompts;
    private final ChatContextWindow contextWindow;
    private final UsageLedgerService usage;
    private final String model;
    private static final Map<String, Object> SCHEMA = Map.of("type", "OBJECT", "properties", Map.of(
            "query", Map.of("type", "STRING", "description", "Specific product or supplier to find, up to 300 characters."),
            "types", Map.of("type", "ARRAY", "items", Map.of("type", "STRING", "enum", List.of("PRODUCT", "COMPANY")))),
            "required", List.of("query"));

    public GuestAnswerService(ChatModelProvider provider, SearchBusinessesTool publicSearch,
            SystemPromptProvider prompts, ChatContextWindow contextWindow, UsageLedgerService usage,
            @Value("${ai.gemini.chat-model:gemini-2.5-flash}") String model) {
        this.provider = provider;
        this.publicSearch = publicSearch;
        this.prompts = prompts;
        this.contextWindow = contextWindow;
        this.usage = usage;
        this.model = model;
    }

    public Map<String, Object> answer(GuestTrialStore.Claim claim, List<ChatMessageInput> previous, String language,
            AtomicBoolean cancelled, AtomicReference<ChatStream> active) {
        String locale = LocaleNormalizer.normalize(language);
        String instruction = prompts.render(locale, Set.of()) + """

                GUEST TRIAL RULES: This is an anonymous visitor, not a signed-in buyer/seller/admin.
                Reply in the website language specified below. Be concise, at most three recommendations.
                You can explain the marketplace and search PUBLIC products/companies using search_businesses.
                You cannot see account history, favorites, carts, private contacts, orders, messages or admin data.
                Never create drafts, place orders or take account actions; ask the user to sign in for these.
                Use search before asserting catalog facts. Do not fetch the entire catalog for general questions.
                If search fails, say so honestly. Never claim a successful action or invent products/links/images.
                Cards are rendered separately; don't repeat a long product catalog in the text.
                Website language: """ + locale;
        List<ChatMessageInput> history = new ArrayList<>(previous);
        history.add(new ChatMessageInput("user", claim.prompt()));
        List<ToolExchangeEntry> exchange = new ArrayList<>();
        List<Map<String, Object>> resultSets = new ArrayList<>();
        ToolSpec tool = new ToolSpec(SearchBusinessesTool.NAME, publicSearch.description(), SCHEMA);
        for (int iteration = 0; iteration < 3; iteration++) {
            if (cancelled.get()) throw new GuestTrialException("guest_unavailable", 503);
            List<ToolCallRequest> calls = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            TokenUsage tokenUsage = null;
            ChatGenerationRequest request = contextWindow.fit(new ChatGenerationRequest(model, instruction, history,
                    iteration < 2 ? List.of(tool) : List.of(), exchange, 0.3f, 1024));
            try (ChatStream stream = provider.generateStream(request)) {
                active.set(stream);
                if (cancelled.get()) throw new GuestTrialException("guest_unavailable", 503);
                for (ChatStreamChunk chunk : stream) {
                    if (cancelled.get()) throw new GuestTrialException("guest_unavailable", 503);
                    if (chunk.textDelta() != null) text.append(chunk.textDelta());
                    if (chunk.usage() != null) tokenUsage = chunk.usage();
                    if (chunk.toolCalls() != null) calls.addAll(chunk.toolCalls());
                    if (text.length() > 12000 || calls.size() > 2) throw new GuestTrialException("guest_unavailable", 503);
                }
            } finally {
                active.set(null);
                if (tokenUsage != null) usage.recordUsage("ai-guest-trial", tokenUsage.promptTokens(), tokenUsage.candidatesTokens());
            }
            if (calls.isEmpty()) {
                if (text.toString().isBlank()) throw new GuestTrialException("guest_unavailable", 503);
                return Map.of("text", text.toString(), "resultSets", resultSets);
            }
            if (iteration == 2) throw new GuestTrialException("guest_unavailable", 503);
            exchange.add(new ModelToolCallEntry(text.toString(), calls));
            List<ToolCallOutcome> outcomes = new ArrayList<>();
            for (ToolCallRequest call : calls) {
                ToolResult result = executePublicSearch(call, locale, cancelled);
                if (result.success()) {
                    // One compact result panel, not repeated catalog pages across tool iterations.
                    resultSets.clear();
                    resultSets.add(result.data());
                }
                outcomes.add(new ToolCallOutcome(call.callId(), call.name(), UntrustedDataWrapper.wrap(result)));
            }
            exchange.add(new ToolResultEntry(outcomes));
        }
        throw new GuestTrialException("guest_unavailable", 503);
    }

    ToolResult executePublicSearch(ToolCallRequest call, String locale, AtomicBoolean cancelled) {
        if (cancelled.get() || !SearchBusinessesTool.NAME.equals(call.name())) return ToolResult.error("Sign in to use account features", 403);
        try {
            ToolArgumentValidator.validate(SCHEMA, call.args());
            String query = ToolArgs.asString(call.args().get("query"));
            if (query == null || query.isBlank() || query.length() > 300) return ToolResult.error("Please specify a product or company", 400);
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("query", query);
            args.put("limit", 3);
            if (call.args().get("types") != null) args.put("types", call.args().get("types"));
            // No JWT, identity, category lookup, personal recommendation or caller-controlled role.
            return publicSearch.execute(args, new ToolExecutionContext(null, null, null, Set.of(), locale));
        } catch (IllegalArgumentException | ToolArgumentException e) { return ToolResult.error("Invalid search arguments", 400); }
    }
}
