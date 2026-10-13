package org.example.ai.jobs;

import org.example.ai.tool.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RecommendJobsTool implements AgentTool {
    public static final String NAME = "recommend_jobs";
    private final JobMatchingService matching;
    public RecommendJobsTool(JobMatchingService matching) { this.matching = matching; }
    public String name() { return NAME; }
    public String description() {
        return "Recommend live jobs from the signed-in caller's opt-in saved job preferences. All account roles may seek work. "
                + "Returns a setup CTA when no profile exists; never guesses skills, edits resumes, or submits applications. "
                + "Explicit filters override saved defaults. At most three initial jobs; no LLM used by matching itself.";
    }
    public Map<String, Object> parametersSchema() { return SearchJobsTool.schema(false); }
    public Set<String> allowedRoles() { return Set.of(); }
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context.userSub() == null || context.userSub().isBlank()) return ToolResult.error("Sign in for saved job recommendations", 403);
        try { return SearchJobsTool.withPresentation(matching.recommend(context.userSub(), SearchJobsTool.criteria(args), context.acceptLanguage()), args); }
        catch (IllegalArgumentException invalid) { return ToolResult.error(invalid.getMessage(), 400); }
        catch (RuntimeException unavailable) { return ToolResult.error("Job recommendations are temporarily unavailable; this is not a no-match result", 503); }
    }
}
