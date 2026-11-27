package org.example.ai.jobs;

import org.example.ai.tool.*;
import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class ListMyJobVacanciesTool implements AgentTool {
    private final AuthenticatedJobsClient source;
    public ListMyJobVacanciesTool(AuthenticatedJobsClient source) { this.source = source; }
    public String name() { return "get_my_job_vacancies"; }
    public String description() { return "List up to five of the caller's own published vacancies to choose for employee matching. Never choose a vacancy on the user's behalf when ambiguous."; }
    public Map<String, Object> parametersSchema() { return Map.of("type", "OBJECT", "properties", Map.of()); }
    public Set<String> allowedRoles() { return Set.of(); }
    public ToolResult execute(Map<String, Object> args, ToolExecutionContext context) {
        if (context.userSub() == null || context.userSub().isBlank()) return ToolResult.error("Sign in to select your vacancy", 403);
        try {
            var all = source.ownedVacancies(context.bearerToken(), context.acceptLanguage());
            return ToolResult.ok(Map.of("items", all.stream().limit(5).toList(), "hasMore", all.size() > 5,
                    "setupUrl", "/jobs?aiMode=CANDIDATES"));
        } catch (org.springframework.security.access.AccessDeniedException denied) { return ToolResult.error("Vacancy access denied", 403); }
        catch (RuntimeException unavailable) { return ToolResult.error("Owned vacancies are temporarily unavailable", 503); }
    }
}
