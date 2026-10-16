package org.example.ai.jobs;

import org.example.ai.tool.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class SearchCandidatesTool implements AgentTool {
    public static final String NAME = "search_job_candidates";
    private final CandidateMatchingService matching;
    public SearchCandidatesTool(CandidateMatchingService matching) { this.matching = matching; }
    public String name() { return NAME; }
    public String description() {
        return "Find up to five opted-in candidates for an explicitly selected caller-owned PUBLISHED vacancy. "
                + "Use get_my_job_vacancies to obtain real vacancy IDs and ask the user to select when ambiguous. "
                + "Returns professional resume links, never contacts. Cannot invite/apply/message anybody. "
                + "Candidate details are displayed by the application, not copied into prose.";
    }
    public Map<String, Object> parametersSchema() {
        return Map.of("type", "OBJECT", "properties", Map.of(
                "vacancyId", Map.of("type", "INTEGER", "description", "User-selected real owned vacancy ID."),
                "query", Map.of("type", "STRING", "description", "Optional short specialty/skill refinement, maximum 120 chars. Never drop explicit constraints.")),
                "required", List.of("vacancyId"));
    }
    public Set<String> allowedRoles() { return Set.of(); }
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context.userSub() == null || context.userSub().isBlank() || context.bearerToken() == null)
            return ToolResult.error("Sign in to find candidates for your vacancy", 403);
        try {
            if (!(args.get("vacancyId") instanceof Number id)) throw new IllegalArgumentException("Select your vacancy");
            return ToolResult.ok(matching.search(id.longValue(), ToolArgs.asString(args.get("query")), context.bearerToken(), context.acceptLanguage()));
        } catch (org.springframework.security.access.AccessDeniedException denied) { return ToolResult.error("Only the vacancy owner can search its candidates", 403); }
        catch (org.example.exception.AiNotFoundException missing) { return ToolResult.error("Selected vacancy is not available", 404); }
        catch (IllegalArgumentException invalid) { return ToolResult.error(invalid.getMessage(), 400); }
        catch (RuntimeException unavailable) { return ToolResult.error("Candidate search is temporarily unavailable; this is not a no-match result", 503); }
    }
}
