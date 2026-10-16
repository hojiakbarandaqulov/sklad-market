package org.example.ai.jobs;

import org.example.ai.tool.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RecommendJobsTool implements AgentTool {
    public static final String NAME = "recommend_jobs";
    private final JobMatchingService matching;
    private final ResumeJobMatchingService resumeMatching;
    public RecommendJobsTool(JobMatchingService matching, ResumeJobMatchingService resumeMatching) {
        this.matching = matching; this.resumeMatching = resumeMatching;
    }
    public String name() { return NAME; }
    public String description() {
        return "Recommend live jobs from the signed-in caller's opt-in saved job preferences. All account roles may seek work. "
                + "Returns a setup CTA when no profile exists; never guesses skills, edits resumes, or submits applications. "
                + "An explicitly selected own resumeId may be used; incomplete experience requires confirmation. "
                + "Explicit filters override saved defaults. Up to five relevant jobs; matching itself uses no LLM.";
    }
    public Map<String, Object> parametersSchema() { return SearchJobsTool.schema(false); }
    public Set<String> allowedRoles() { return Set.of(); }
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context.userSub() == null || context.userSub().isBlank()) return ToolResult.error("Sign in for saved job recommendations", 403);
        try {
            var resume = args.get("resumeId");
            var result = resume instanceof Number id
                    ? resumeMatching.recommend(id.longValue(), ToolArgs.asString(args.get("confirmedExperience")),
                            SearchJobsTool.criteria(args), context.bearerToken(), context.acceptLanguage())
                    : matching.recommend(context.userSub(), SearchJobsTool.criteria(args), context.acceptLanguage());
            return SearchJobsTool.withPresentation(result, args);
        }
        catch (org.springframework.security.access.AccessDeniedException denied) { return ToolResult.error("Resume access denied", 403); }
        catch (org.example.exception.AiNotFoundException missing) { return ToolResult.error("Selected own resume is not available", 404); }
        catch (IllegalArgumentException invalid) { return ToolResult.error(invalid.getMessage(), 400); }
        catch (RuntimeException unavailable) { return ToolResult.error("Job recommendations are temporarily unavailable; this is not a no-match result", 503); }
    }
}
