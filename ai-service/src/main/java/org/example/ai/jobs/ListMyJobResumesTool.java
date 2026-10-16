package org.example.ai.jobs;

import org.example.ai.tool.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ListMyJobResumesTool implements AgentTool {
    private final AuthenticatedJobsClient source;
    public ListMyJobResumesTool(AuthenticatedJobsClient source) { this.source = source; }
    public String name() { return "get_my_job_resumes"; }
    public String description() { return "List up to five caller-owned resume IDs and desired occupations for explicitly choosing a resume for job matching. No contacts. Ask the user to choose if ambiguous; never publish or edit the resume."; }
    public Map<String, Object> parametersSchema() { return Map.of("type", "OBJECT", "properties", Map.of()); }
    public Set<String> allowedRoles() { return Set.of(); }
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context.userSub() == null || context.userSub().isBlank()) return ToolResult.error("Sign in to select your own resume", 403);
        try { return ToolResult.ok(Map.of("items", source.ownResumes(context.bearerToken(), context.acceptLanguage()), "setupUrl", "/jobs?aiSetup=1")); }
        catch (org.springframework.security.access.AccessDeniedException denied) { return ToolResult.error("Resume access denied", 403); }
        catch (RuntimeException unavailable) { return ToolResult.error("Own resumes are temporarily unavailable", 503); }
    }
}
